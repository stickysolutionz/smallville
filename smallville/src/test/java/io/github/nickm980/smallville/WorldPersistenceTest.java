package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Dialog;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.persistence.WorldMapper;
import io.github.nickm980.smallville.persistence.WorldSnapshot;

public class WorldPersistenceTest {

    private static ObjectMapper mapper() {
	ObjectMapper mapper = new ObjectMapper();
	mapper.registerModule(new JavaTimeModule());
	mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	return mapper;
    }

    private static World populatedWorld() {
	World world = new World();

	Location cafe = new Location("Hobbs Cafe");
	cafe.setState("open");
	world.create(cafe);
	world.create(new Location("Red House: Bedroom"));

	Agent maria = new Agent("Maria Lopez", List.of(new Characteristic("Maria runs the cafe")), "idle", cafe);
	maria.setTraits("Warm, tired, stubborn");
	maria.setCurrentActivity("wiping down the counter");
	maria.setCurrentEmoji("☕");

	Observation observation = new Observation("Klaus said: the coffee is good");
	observation.setDialog(true);
	maria.getMemoryStream().add(observation);
	maria.getMemoryStream().add(new Plan("9:00 am at Hobbs Cafe, open up", LocalDateTime.now(), PlanType.SHORT_TERM));
	maria.getMemoryStream().add(new Reflection("Maria takes pride in the cafe"));

	world.create(maria);

	Agent klaus = new Agent("Klaus Mueller", List.of(new Characteristic("Klaus is a student")), "reading", cafe);
	world.create(klaus);

	world
	    .create(new Conversation(List.of("Maria Lopez", "Klaus Mueller"),
		    List.of(new Dialog("Maria", "Morning."), new Dialog("Klaus", "Morning, is the wifi back?")),
		    SimulationTime.now()));

	world.getRelationships().recordConversation(List.of("Maria Lopez", "Klaus Mueller"), 0.15, SimulationTime.now());

	return world;
    }

    private static World roundTrip(World original) throws Exception {
	ObjectMapper mapper = mapper();
	String json = mapper.writeValueAsString(WorldMapper.toSnapshot(original));

	World restored = new World();
	WorldMapper.restore(restored, mapper.readValue(json, WorldSnapshot.class));

	return restored;
    }

    @Test
    public void agents_locations_and_conversations_survive_a_restart() throws Exception {
	World restored = roundTrip(populatedWorld());

	assertEquals(2, restored.getAgents().size());
	assertEquals(2, restored.getLocations().size());
	assertEquals(1, restored.getAllConversations().size());
	assertEquals("open", restored.getLocation("Hobbs Cafe").get().getState());
    }

    @Test
    public void an_agents_identity_and_position_survive() throws Exception {
	Agent maria = roundTrip(populatedWorld()).getAgent("Maria Lopez").orElseThrow();

	assertEquals("Warm, tired, stubborn", maria.getTraits());
	assertEquals("wiping down the counter", maria.getCurrentActivity());
	assertEquals("idle", maria.getLastActivity(), "the previous activity is part of the agent's state");
	assertEquals("☕", maria.getEmoji());
	assertEquals("Hobbs Cafe", maria.getLocation().getFullPath());
    }

    @Test
    public void every_kind_of_memory_survives_with_its_type_intact() throws Exception {
	List<Memory> memories = roundTrip(populatedWorld()).getAgent("Maria Lopez").orElseThrow().getMemoryStream()
	    .getMemories();

	assertEquals(1, memories.stream().filter(m -> m instanceof Characteristic).count());
	assertEquals(1, memories.stream().filter(m -> m instanceof Reflection).count());
	assertEquals(1, memories.stream().filter(m -> m instanceof Plan).count());
	assertEquals(1, memories.stream().filter(m -> m instanceof Observation).count());
    }

    @Test
    public void a_dialog_observation_is_still_marked_as_dialog() throws Exception {
	// If this is lost, conversation lines start showing up in diaries and
	// in the story feed, which both filter on it.
	Observation restored = (Observation) roundTrip(populatedWorld())
	    .getAgent("Maria Lopez")
	    .orElseThrow()
	    .getMemoryStream()
	    .getMemories()
	    .stream()
	    .filter(m -> m instanceof Observation)
	    .findFirst()
	    .orElseThrow();

	assertTrue(restored.isDialog());
	assertEquals("Klaus said: the coffee is good", restored.getDescription());
    }

    @Test
    public void a_plan_keeps_whether_it_is_short_or_long_term() throws Exception {
	Plan restored = (Plan) roundTrip(populatedWorld()).getAgent("Maria Lopez").orElseThrow().getMemoryStream()
	    .getMemories().stream().filter(m -> m instanceof Plan).findFirst().orElseThrow();

	assertEquals(PlanType.SHORT_TERM, restored.getType());
	assertNotNull(restored.getTime());
    }

    @Test
    public void relationships_survive() throws Exception {
	World restored = roundTrip(populatedWorld());

	assertEquals(1, restored.getRelationships().get("Maria Lopez", "Klaus Mueller").familiarity());
	assertTrue(restored.getRelationships().get("Maria Lopez", "Klaus Mueller").affinity() > 0);
    }

    @Test
    public void an_agent_whose_location_vanished_is_kept_rather_than_dropped() throws Exception {
	// Losing the location should not cost the agent their entire history.
	ObjectMapper mapper = mapper();
	WorldSnapshot snapshot = WorldMapper.toSnapshot(populatedWorld());
	snapshot.getLocations().removeIf(location -> "Hobbs Cafe".equals(location.getName()));

	World restored = new World();
	WorldMapper.restore(restored, mapper.readValue(mapper.writeValueAsString(snapshot), WorldSnapshot.class));

	assertEquals(2, restored.getAgents().size());
	assertNotNull(restored.getAgent("Maria Lopez").orElseThrow().getLocation());
    }

    @Test
    public void a_memory_of_an_unrecognised_type_is_skipped_not_fatal() throws Exception {
	WorldSnapshot snapshot = WorldMapper.toSnapshot(populatedWorld());
	snapshot.getAgents().get(0).getMemories().get(0).setType("SomethingFromTheFuture");

	World restored = new World();
	WorldMapper.restore(restored, snapshot);

	assertFalse(restored.getAgents().isEmpty(), "one unknown memory must not lose the whole town");
    }

    @Test
    public void the_simulated_clock_is_restored() throws Exception {
	LocalDateTime restore = SimulationTime.now();

	try {
	    SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 17, 30));
	    WorldSnapshot snapshot = WorldMapper.toSnapshot(populatedWorld());

	    SimulationTime.setSimulationTime(LocalDateTime.of(2020, 1, 1, 0, 0));
	    WorldMapper.restore(new World(), snapshot);

	    assertEquals(LocalDateTime.of(2026, 8, 1, 17, 30), SimulationTime.now());
	} finally {
	    SimulationTime.setSimulationTime(restore);
	}
    }
}
