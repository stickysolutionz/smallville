package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.prompts.PromptRequest;
import io.github.nickm980.smallville.prompts.Prompts;
import io.github.nickm980.smallville.update.UpdateCurrentActivity;
import io.github.nickm980.smallville.update.UpdateInfo;
import io.github.nickm980.smallville.prompts.ChatService;

/**
 * What an agent remembers must be something the simulation can stand behind.
 * <p>
 * The activity text used to be filed straight into memory as an observation, so
 * a plan saying "join in the conversation and share a joke" became a first-hand
 * memory of a conversation that never took place. Four agents in a Walmart each
 * remembered chatting with each other while the conversation system had
 * actually failed and recorded nothing.
 */
public class ActivityMemoryTest {

    private static World walmartWith(String... names) {
	World world = new World();
	Location walmart = new Location("Walmart");
	world.create(walmart);

	for (String name : names) {
	    world.create(new Agent(name, List.of(new Characteristic(name + " shops here")), "idle", walmart));
	}

	return world;
    }

    /** Runs the activity step with a canned model answer. */
    private static void runActivityStep(World world, Agent agent, String activityResponse) {
	Prompts prompts = new ChatService(world, new LLM() {
	    @Override
	    public String sendChat(PromptRequest prompt, double temperature) {
		return activityResponse;
	    }
	});

	new UpdateCurrentActivity().start(prompts, world, agent, new UpdateInfo());
    }

    private static String lastMemoryOf(Agent agent) {
	List<Memory> memories = agent.getMemoryStream().getMemories();

	return memories.get(memories.size() - 1).getDescription();
    }

    @Test
    public void who_else_was_present_comes_from_the_world() {
	World world = walmartWith("Joan", "Paul", "Lindsey");
	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertTrue(memory.contains("Walmart"), memory);
	assertTrue(memory.contains("Lindsey") && memory.contains("Paul"),
		"co-presence should be recorded from the world, got: " + memory);
    }

    @Test
    public void what_others_were_doing_is_witnessed_not_just_who_was_there() {
	// People are learned by watching them. Recording only names meant an
	// agent could stand beside somebody for hours and come away knowing
	// nothing about them - while the conversation prompt quietly handed
	// over that person's entire inner life instead.
	World world = walmartWith("Joan", "Paul");
	world.getAgent("Paul").orElseThrow().setCurrentActivity("arguing with the self-checkout machine");

	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertTrue(memory.contains("Paul was arguing with the self-checkout machine"),
		"Joan should remember what she saw Paul doing, got: " + memory);
    }

    @Test
    public void a_crowded_room_does_not_bury_the_agents_own_memory() {
	World world = walmartWith("Joan", "A", "B", "C", "D", "E");

	for (String name : new String[] { "A", "B", "C", "D", "E" }) {
	    world.getAgent(name).orElseThrow().setCurrentActivity("milling about");
	}

	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertEquals(3, memory.split("milling about", -1).length - 1,
		"only a few of the room should be recorded, got: " + memory);
	// The agent's own activity leads, before anyone else's. A memory
	// describes the PREVIOUS activity, which here is the starting "idle".
	assertTrue(memory.startsWith("idle at Walmart"), "the agent's own doing should lead, got: " + memory);
    }

    @Test
    public void someone_who_is_elsewhere_is_not_recorded_as_present() {
	World world = walmartWith("Joan", "Paul");
	world.create(new Location("Cottage"));

	Agent paul = world.getAgent("Paul").orElseThrow();
	paul.setLocation(world.getLocation("Cottage").orElseThrow());

	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	assertFalse(lastMemoryOf(joan).contains("Paul"),
		"Paul is in the Cottage and must not appear in Joan's memory of Walmart");
    }

    @Test
    public void being_alone_produces_no_claim_about_company() {
	World world = walmartWith("Joan");
	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertTrue(memory.contains("Walmart"), memory);
	assertFalse(memory.contains("also"), "nobody else was there, got: " + memory);
    }

    @Test
    public void a_missing_activity_still_records_where_they_were() {
	World world = walmartWith("Joan", "Paul");
	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertTrue(memory.contains("Walmart"), memory);
	assertTrue(memory.contains("Paul"), memory);
    }

    @Test
    public void an_unchanged_activity_is_not_recorded_twice() {
	// A plan now spans several ticks on purpose, so an agent genuinely
	// spends a few of them on one thing. Writing a near-identical memory
	// each time fills the stream with duplicates that get retrieved
	// together and crowd everything else out.
	World world = walmartWith("Joan");
	Agent joan = world.getAgent("Joan").orElseThrow();

	String sameAnswer = """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""";

	// A memory describes the PREVIOUS activity, so the first two differ -
	// "idle", then "browsing". Only from the third does the same activity
	// come round again.
	runActivityStep(world, joan, sameAnswer);
	runActivityStep(world, joan, sameAnswer);
	int settled = joan.getMemoryStream().getMemories().size();

	runActivityStep(world, joan, sameAnswer);
	runActivityStep(world, joan, sameAnswer);

	assertEquals(settled, joan.getMemoryStream().getMemories().size(),
		"an unchanged activity should not be remembered again");
    }

    @Test
    public void a_memory_belongs_to_where_the_activity_happened() {
	// The memory describes what the agent was doing BEFORE this update, so
	// it belongs to where they were doing it. Reading the location
	// afterwards attributed the old activity to the new place - an agent
	// walking from the Cottage to Walmart remembered making coffee at
	// Walmart.
	World world = walmartWith("Joan");
	world.create(new Location("Cottage"));

	Agent joan = world.getAgent("Joan").orElseThrow();
	joan.setLocation(world.getLocation("Cottage").orElseThrow());
	joan.setCurrentActivity("making a pot of coffee");

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	String memory = lastMemoryOf(joan);

	assertTrue(memory.contains("Cottage"), "the coffee was made at the Cottage, got: " + memory);
	assertFalse(memory.contains("Walmart"), "the coffee was not made at Walmart, got: " + memory);
	assertEquals("Walmart", joan.getLocation().getFullPath(), "the agent should still have moved");
    }

    @Test
    public void a_daily_goal_is_marked_addressed_once_its_place_is_visited() {
	// Without this, "pick up cat food" is restated every hour for the rest
	// of the day because nothing records having gone to the shop.
	World world = walmartWith("Joan");
	Agent joan = world.getAgent("Joan").orElseThrow();

	Plan errand = new Plan("afternoon at Walmart, pick up cat food", SimulationTime.now(), PlanType.LONG_TERM);
	errand.setLocation("Walmart");

	Plan elsewhere = new Plan("evening at The Tavern, unwind", SimulationTime.now(), PlanType.LONG_TERM);
	elsewhere.setLocation("The Tavern");

	joan.getMemoryStream().addAll(List.of(errand, elsewhere));

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies
		Location: Walmart
		Emoji: 🛒
		""");

	assertTrue(errand.isAddressed(), "the Walmart errand should be marked addressed");
	assertFalse(elsewhere.isAddressed(), "a goal somewhere else must stay outstanding");
    }

    @Test
    public void the_location_is_not_repeated_when_the_activity_already_names_it() {
	World world = walmartWith("Joan");
	Agent joan = world.getAgent("Joan").orElseThrow();

	runActivityStep(world, joan, """
		Activity: browsing the pet supplies at Walmart
		Location: Walmart
		Emoji: 🛒
		""");

	assertEquals(1, lastMemoryOf(joan).split("Walmart", -1).length - 1,
		"Walmart should appear once, got: " + lastMemoryOf(joan));
    }
}
