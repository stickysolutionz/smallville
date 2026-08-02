package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.PromptRequest;
import io.github.nickm980.smallville.update.UpdateInfo;
import io.github.nickm980.smallville.update.UpdatePlans;

/**
 * Plans have to expire on their own.
 * <p>
 * Nothing used to empty a plan list, and plans were only generated when one was
 * empty, so an agent planned once on their first tick and worked from it
 * forever - a schedule written at 9pm still steering them at 3am, and a day's
 * goals never rolling into the next day. Over an unattended overnight run that
 * meant nobody ever woke up.
 */
public class ReplanningTest {

    private final LocalDateTime realNow = SimulationTime.now();

    @AfterEach
    public void restoreClock() {
	SimulationTime.setSimulationTime(realNow);
    }

    /** Counts how many times the model is asked for anything. */
    private static class CountingLLM implements LLM {
	final AtomicInteger calls = new AtomicInteger();

	@Override
	public String sendChat(PromptRequest prompt, double temperature) {
	    calls.incrementAndGet();

	    return """
		    {"plans": [{"time": "9:00 am", "location": "Cottage", "activity": "pottering about"}]}
		    """;
	}
    }

    private static World cottageWith(Agent... agents) {
	World world = new World();
	world.create(new Location("Cottage"));

	for (Agent agent : agents) {
	    world.create(agent);
	}

	return world;
    }

    private static Agent resident(World world) {
	return new Agent("Maria Lopez", List.of(new Characteristic("Maria lives here")), "idle",
		world.getLocation("Cottage").orElseThrow());
    }

    private static void runPlanStep(World world, Agent agent, LLM llm) {
	new UpdatePlans().start(new ChatService(world, llm), world, agent, new UpdateInfo());
    }

    @Test
    public void plans_are_not_regenerated_while_they_are_still_current() {
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 9, 0));

	World world = new World();
	world.create(new Location("Cottage"));
	Agent maria = resident(world);
	world.create(maria);

	CountingLLM llm = new CountingLLM();
	runPlanStep(world, maria, llm);
	int afterFirst = llm.calls.get();

	// Ten simulated minutes later nothing should need rewriting.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 9, 10));
	runPlanStep(world, maria, llm);

	assertEquals(afterFirst, llm.calls.get(), "plans were rewritten while still current");
    }

    @Test
    public void the_hour_is_replanned_once_it_has_passed() {
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 9, 0));

	World world = new World();
	world.create(new Location("Cottage"));
	Agent maria = resident(world);
	world.create(maria);

	CountingLLM llm = new CountingLLM();
	runPlanStep(world, maria, llm);
	int afterFirst = llm.calls.get();

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 10, 30));
	runPlanStep(world, maria, llm);

	assertTrue(llm.calls.get() > afterFirst, "the hour had passed and should have been replanned");
	assertTrue(!maria.getMemoryStream().getPlans(PlanType.SHORT_TERM).isEmpty());
    }

    @Test
    public void the_day_is_replanned_once_it_rolls_over() {
	// The overnight case: an agent whose daily goals ended in "night, sleep"
	// has to get a new day, or they sleep through it.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 21, 0));

	World world = new World();
	world.create(new Location("Cottage"));
	Agent maria = resident(world);
	world.create(maria);

	CountingLLM llm = new CountingLLM();
	runPlanStep(world, maria, llm);

	LocalDateTime madeAt = maria.getMemoryStream().getPlans(PlanType.LONG_TERM).get(0).getCreatedAt();
	assertEquals(LocalDateTime.of(2026, 8, 1, 21, 0), madeAt);

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 2, 7, 0));
	runPlanStep(world, maria, llm);

	LocalDateTime replannedAt = maria.getMemoryStream().getPlans(PlanType.LONG_TERM).get(0).getCreatedAt();

	assertEquals(LocalDateTime.of(2026, 8, 2, 7, 0), replannedAt,
		"the new day should have produced new goals");
    }

    @Test
    public void a_plan_made_late_at_night_is_not_instantly_stale() {
	// Staleness is judged from when a plan was written, not the time it
	// names. A plan made at 11:45pm for 12:15am names a time that reads as
	// nearly a day in the past - judging by that would rewrite it every
	// single tick.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 23, 45));

	World world = new World();
	world.create(new Location("Cottage"));
	Agent maria = resident(world);
	world.create(maria);

	CountingLLM llm = new CountingLLM();
	runPlanStep(world, maria, llm);
	int afterFirst = llm.calls.get();

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 1, 23, 55));
	runPlanStep(world, maria, llm);

	assertEquals(afterFirst, llm.calls.get(), "a freshly made plan must not be treated as stale");
    }
}
