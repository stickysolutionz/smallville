package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.ChatGPT;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.PromptRequest;

public class PlansParsingTest {

    @Test
    public void test_plans_parse_with_times_at_beginning_and_end() {
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		2:01 am at Red House: Bedroom, sleeping
		\n  2:30 PM Meet with the farmer to discuss crops at
		\nHelp with feeding the animals from 3:00 PM - 4:00 PM
		\nRead a book under the shade of a tree from 4:00 PM - 5:00 PM
		\n2:20 am at Red House: Bedroom, still sleeping
		""");

	assertTrue(plans.size() == 5);
    }

    @Test
    public void test_plans_parse_with_time_at_end() {
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		. Walk to the farmhouse at 2:00 PM
		\n- Meet with the farmer to discuss crops at 2:30 PM
		\n- Help with feeding the animals from 3:00 PM - 4:00 PM
		\n- Read a book under the shade of a tree from 4:00 PM - 5:00 PM
		\n- Have dinner at home at 6:00 PM.
		""");

	assertTrue(plans.size() == 5);
    }

    @Test
    public void conversational_preamble_before_the_plan_is_discarded() {
	// Observed in real output. The preamble mentions a time mid-sentence,
	// and was previously stored verbatim as a diary entry because the
	// parser matched that time.
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		Alright, let me figure out what's going on here. It's 10:30 PM and Maria is at the cafe, so
		she's probably wrapping up her shift soon. Here's the plan:

		10:30 pm at the Hobbs Cafe, finish the closing shift;
		11:15 pm at Red House: Bedroom, go to sleep
		""");

	assertEquals(2, plans.size());
	assertTrue(plans.get(0).getDescription().startsWith("10:30 pm at the Hobbs Cafe"),
		"expected the first plan to be the real one, got: " + plans.get(0).getDescription());
    }

    @Test
    public void numbered_list_markers_do_not_break_anchoring() {
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		Here is the plan:

		1. 8:00 am at Red House: Kitchen, make breakfast
		2. 9:00 am at the Market: Stalls, buy vegetables
		""");

	assertEquals(2, plans.size());
    }

    @Test
    public void times_are_read_as_real_clock_values() {
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		12:30 am at Red House: Bedroom, sleeping
		12:30 pm at the Hobbs Cafe, have lunch
		9:05 pm at Red House: Kitchen, wash up
		""");

	assertEquals(3, plans.size());
	// Midnight and noon are the two the 12-hour clock gets wrong when hour
	// is shifted without the modulo.
	assertEquals(0, plans.get(0).getTime().getHour());
	assertEquals(12, plans.get(1).getTime().getHour());
	assertEquals(21, plans.get(2).getTime().getHour());
	assertEquals(5, plans.get(2).getTime().getMinute());
    }

    @Test
    public void plans_are_dated_on_the_simulated_day_not_the_wall_clock() {
	// The simulated clock advances a timestep every tick and crosses
	// midnight within minutes of real time. Dating plans with LocalDate.now()
	// stamped them onto a day the simulation had already left.
	LocalDateTime simulatedDay = LocalDateTime.now().plusDays(3).withHour(6).withMinute(0);
	LocalDateTime restore = SimulationTime.now();

	try {
	    SimulationTime.setSimulationTime(simulatedDay);

	    ChatService service = new ChatService(new World(), new ChatGPT());
	    List<Plan> plans = service.parsePlans("9:00 am at Red House: Kitchen, make breakfast");

	    assertEquals(1, plans.size());
	    assertEquals(simulatedDay.toLocalDate(), plans.get(0).getTime().toLocalDate());
	} finally {
	    SimulationTime.setSimulationTime(restore);
	}
    }

    @Test
    public void a_daily_goal_uses_a_time_of_day_rather_than_a_clock_time() {
	// Daily plans are intentions now - "morning", not "9:00 am" - but Plan
	// is a TemporalMemory and everything that orders plans needs an instant,
	// so a time of day maps onto a representative hour. The location is kept
	// separately so the simulation can tell whether the agent has been.
	World world = new World();
	Location market = new Location("Market: Stalls");
	world.create(market);
	world.create(new Location("The Tavern"));

	Agent shopkeeper = new Agent("Maria Lopez", List.of(new Characteristic("Maria runs the market stall")), "idle",
		market);
	world.create(shopkeeper);

	ChatService service = new ChatService(world, new LLM() {
	    @Override
	    public String sendChat(PromptRequest prompt, double temperature) {
		return """
			{"plans": [
			  {"when": "morning", "location": "Market: Stalls", "intent": "open up and get through the deliveries"},
			  {"when": "evening", "location": "The Tavern", "intent": "unwind, and see who is about"}
			]}
			""";
	    }
	});

	List<Plan> plans = service.getPlans(shopkeeper);

	assertEquals(2, plans.size());
	assertEquals(9, plans.get(0).getTime().getHour(), "morning should map to a morning hour");
	assertEquals(19, plans.get(1).getTime().getHour(), "evening should map to an evening hour");
	assertEquals("Market: Stalls", plans.get(0).getLocation());
	assertTrue(plans.get(0).getDescription().contains("deliveries"), plans.get(0).getDescription());
	assertFalse(plans.get(0).isAddressed(), "a fresh goal starts outstanding");
    }

    @Test
    public void lines_without_a_usable_time_are_skipped() {
	ChatService service = new ChatService(new World(), new ChatGPT());

	List<Plan> plans = service.parsePlans("""
		I am not sure what to do today.
		Maybe something will come up.
		""");

	assertTrue(plans.isEmpty());
    }
}
