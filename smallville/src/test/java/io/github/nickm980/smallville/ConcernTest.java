package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Concern;
import io.github.nickm980.smallville.persistence.WorldMapper;
import io.github.nickm980.smallville.persistence.WorldSnapshot;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.PromptRequest;

/**
 * Things that land on an agent from outside the town.
 * <p>
 * Nothing inside the simulation is ever at stake on its own - agents move,
 * talk and form opinions, but nobody wants anything they might not get. This is
 * the cheap source of wanting: a fact arrives and they have to live with it.
 */
public class ConcernTest {

    private final LocalDateTime realNow = SimulationTime.now();

    @AfterEach
    public void restoreClock() {
	SimulationTime.setSimulationTime(realNow);
    }

    private static World townWithJoan() {
	World world = new World();
	world.create(new Location("Cottage"));
	world
	    .create(new Agent("Joan", List.of(new Characteristic("Joan keeps to herself")), "idle",
		    world.getLocation("Cottage").orElseThrow()));

	return world;
    }

    private static ChatService replying(World world, String response) {
	return new ChatService(world, new LLM() {
	    @Override
	    public String sendChat(PromptRequest prompt, double temperature) {
		return response;
	    }
	});
    }

    @Test
    public void an_event_is_read_from_the_json_shape() {
	World world = townWithJoan();
	Agent joan = world.getAgent("Joan").orElseThrow();

	Concern concern = replying(world, """
		{"event": "Mom texted. Her car failed inspection, the repair is $1,200.",
		 "source": "family", "valence": "bad", "demand": "costly",
		 "privacy": "private", "hours": 30}
		""").generateEvent(joan, Concern.Valence.BAD);

	assertNotNull(concern);
	assertEquals(Concern.Source.FAMILY, concern.getSource());
	assertEquals(Concern.Valence.BAD, concern.getValence());
	assertEquals(Concern.Demand.COSTLY, concern.getDemand());
	assertEquals(Concern.Privacy.PRIVATE, concern.getPrivacy());
	assertTrue(concern.getDescription().contains("$1,200"));
    }

    @Test
    public void an_unusable_answer_yields_nothing_rather_than_throwing() {
	// Nothing is broken when an event fails to arrive - the town just has a
	// quieter day.
	World world = townWithJoan();

	assertNull(replying(world, "I'm sorry, I can't help with that.")
	    .generateEvent(world.getAgent("Joan").orElseThrow(), Concern.Valence.BAD));
    }

    private static void assertNull(Object value) {
	assertTrue(value == null, "expected nothing, got " + value);
    }

    @Test
    public void unrecognised_fields_fall_back_rather_than_failing() {
	World world = townWithJoan();

	Concern concern = replying(world, """
		{"event": "A package says delivered. It isn't here.", "source": "postman",
		 "valence": "confusing", "demand": "maybe", "privacy": "whatever", "hours": 9}
		""").generateEvent(world.getAgent("Joan").orElseThrow(), Concern.Valence.AMBIGUOUS);

	assertNotNull(concern);
	assertEquals(Concern.Source.CHANCE, concern.getSource());
	assertEquals(Concern.Valence.AMBIGUOUS, concern.getValence());
    }

    @Test
    public void the_caller_decides_the_valence_not_the_model() {
        // Left to the model the mix is whatever it happens to lean toward,
        // which is neither knowable nor tunable - and the balance between good
        // and bad news is most of what decides what kind of town this is.
        World world = townWithJoan();

        Concern concern = replying(world, """
                {"event": "A neighbor two states away left a voicemail.",
                 "source": "family", "valence": "good", "demand": "small",
                 "privacy": "open", "hours": 8}
                """).generateEvent(world.getAgent("Joan").orElseThrow(), Concern.Valence.BAD);

        assertEquals(Concern.Valence.BAD, concern.getValence(),
                "the model said good; the simulation asked for bad and should win");
    }

    @Test
    public void a_concern_expires_rather_than_resolving() {
	// There is no money in this world, so nobody can actually pay a repair
	// bill. "Something was weighing on her and then it passed" is most of
	// life, and asking for more would mean simulating an economy.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 9, 0));

	Concern concern = new Concern("Mom texted about the car", SimulationTime.now(), Duration.ofHours(6),
		Concern.Source.FAMILY, Concern.Valence.BAD, Concern.Demand.COSTLY, Concern.Privacy.PRIVATE);

	assertTrue(concern.isActive());

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 14, 0));
	assertTrue(concern.isActive(), "still within its window");

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 16, 0));
	assertFalse(concern.isActive(), "should have passed");
    }

    @Test
    public void only_live_concerns_reach_the_prompts() {
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 9, 0));

	World world = townWithJoan();
	Agent joan = world.getAgent("Joan").orElseThrow();

	joan
	    .getMemoryStream()
	    .add(new Concern("An old worry", SimulationTime.now(), Duration.ofHours(2), Concern.Source.CHANCE,
		    Concern.Valence.BAD, Concern.Demand.NOTHING, Concern.Privacy.OPEN));

	assertEquals(1, joan.getMemoryStream().getActiveConcerns().size());

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 12, 0));

	assertTrue(joan.getMemoryStream().getActiveConcerns().isEmpty(), "an expired concern should stop weighing");
    }

    @Test
    public void how_private_it_is_reaches_the_prompt() {
	// The whole point of the privacy axis: an agent who would rather nobody
	// knew is in conflict with the system that spreads information.
	Concern shameful = new Concern("Card declined at the register", SimulationTime.now(), Duration.ofHours(6),
		Concern.Source.CHANCE, Concern.Valence.BAD, Concern.Demand.NOTHING, Concern.Privacy.SHAMEFUL);

	assertTrue(shameful.describe().contains("rather nobody knew"), shameful.describe());
    }

    @Test
    public void a_concern_survives_a_restart() throws Exception {
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 4, 9, 0));

	World world = townWithJoan();
	world
	    .getAgent("Joan")
	    .orElseThrow()
	    .getMemoryStream()
	    .add(new Concern("Mom texted about the car", SimulationTime.now(), Duration.ofHours(30),
		    Concern.Source.FAMILY, Concern.Valence.BAD, Concern.Demand.COSTLY, Concern.Privacy.PRIVATE));

	ObjectMapper mapper = new ObjectMapper();
	mapper.registerModule(new JavaTimeModule());
	mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	World restored = new World();
	WorldMapper
	    .restore(restored,
		    mapper.readValue(mapper.writeValueAsString(WorldMapper.toSnapshot(world)), WorldSnapshot.class));

	List<Concern> concerns = restored.getAgent("Joan").orElseThrow().getMemoryStream().getActiveConcerns();

	assertEquals(1, concerns.size());
	assertEquals(Concern.Source.FAMILY, concerns.get(0).getSource());
	assertEquals(Concern.Privacy.PRIVATE, concerns.get(0).getPrivacy());
	assertTrue(concerns.get(0).isActive());
    }
}
