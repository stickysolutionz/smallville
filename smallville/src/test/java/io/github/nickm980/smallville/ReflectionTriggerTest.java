package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.memory.MemoryStream;
import io.github.nickm980.smallville.memory.Observation;

/**
 * Reflection has to stay rare on its own.
 * <p>
 * The trigger used to sum importance across a sliding window of recent
 * memories, which grows without bound as an agent accumulates them. A threshold
 * tuned to fire on 9% of agent updates fired on 57% a day later, and reflection
 * became 68% of the entire bill. No fixed number survives a measure that keeps
 * climbing - it has to reset.
 */
public class ReflectionTriggerTest {

    private final LocalDateTime realNow = SimulationTime.now();

    @AfterEach
    public void restoreClock() {
	SimulationTime.setSimulationTime(realNow);
    }

    private static MemoryStream streamWith(int count, int importanceEach) {
	MemoryStream stream = new MemoryStream();

	for (int i = 0; i < count; i++) {
	    Observation observation = new Observation("something happened " + i);
	    observation.setImportance(importanceEach);
	    stream.add(observation);
	}

	return stream;
    }

    @Test
    public void weight_accumulates_until_the_agent_reflects() {
	MemoryStream stream = streamWith(10, 5);

	assertEquals(50, stream.importanceSinceLastReflection());
    }

    @Test
    public void reflecting_resets_the_measure() {
	// The whole point. Without this the sum only ever climbs, and any
	// threshold set against it is correct for exactly one run.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 0));
	MemoryStream stream = streamWith(10, 5);

	assertEquals(50, stream.importanceSinceLastReflection());

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 30));
	stream.markReflected();

	assertEquals(0, stream.importanceSinceLastReflection(), "reflecting should clear what came before");
    }

    @Test
    public void only_what_arrived_afterwards_counts() {
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 0));
	MemoryStream stream = streamWith(10, 5);

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 30));
	stream.markReflected();

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 10, 0));
	Observation later = new Observation("something new");
	later.setImportance(7);
	stream.add(later);

	assertEquals(7, stream.importanceSinceLastReflection());
    }

    @Test
    public void a_long_running_stream_does_not_inflate_the_measure() {
	// A thousand old memories that have already been reflected on must not
	// push a fresh agent over the threshold. This is exactly what went
	// wrong: the median climbed from 325 to 706 over one longer run.
	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 0));
	MemoryStream stream = streamWith(1000, 8);

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 9, 30));
	stream.markReflected();

	SimulationTime.setSimulationTime(LocalDateTime.of(2026, 8, 3, 10, 0));
	Observation later = new Observation("one new thing");
	later.setImportance(3);
	stream.add(later);

	assertTrue(stream.importanceSinceLastReflection() < 10,
		"eight thousand points of already-reflected history should not count again, got "
			+ stream.importanceSinceLastReflection());
    }

    @Test
    public void plans_do_not_count_toward_reflecting() {
	// Plans are intentions, not things that happened. Counting them would
	// make an agent reflect for having made a schedule.
	MemoryStream stream = new MemoryStream();
	io.github.nickm980.smallville.memory.Plan plan = new io.github.nickm980.smallville.memory.Plan(
		"9:00 am at Cottage, make coffee", SimulationTime.now());
	plan.setImportance(9);
	stream.add(plan);

	assertEquals(0, stream.importanceSinceLastReflection());
    }
}
