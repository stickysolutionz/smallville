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
import io.github.nickm980.smallville.memory.Memory;
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
