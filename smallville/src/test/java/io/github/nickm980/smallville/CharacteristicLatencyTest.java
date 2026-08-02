package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.api.v1.SimulationService;
import io.github.nickm980.smallville.api.v1.dto.CreateAgentRequest;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.prompts.PromptRequest;

/**
 * Editing an agent's personality must not queue behind a running tick.
 * <p>
 * The simulation lock is held for a whole agent update - several sequential
 * model calls, routinely 30-45 seconds against a reasoning model. Taking it for
 * a single list insert meant the dashboard appeared to hang on an operation
 * that does no real work.
 */
public class CharacteristicLatencyTest {

    /** Stands in for the model; never called by anything under test here. */
    private static final LLM UNUSED_LLM = new LLM() {
	@Override
	public String sendChat(PromptRequest prompt, double temperature) {
	    throw new AssertionError("no model call expected");
	}
    };

    private static World worldWithAgent() {
	World world = new World();
	Location cottage = new Location("Cottage");
	world.create(cottage);
	world.create(new Agent("Maria Lopez", List.of(new Characteristic("Maria runs the cafe")), "idle", cottage));

	return world;
    }

    @Test
    public void a_characteristic_can_be_added_while_the_world_is_locked() throws Exception {
	World world = worldWithAgent();
	SimulationService service = new SimulationService(UNUSED_LLM, world);

	CountDownLatch holding = new CountDownLatch(1);
	CountDownLatch release = new CountDownLatch(1);

	// Stands in for a tick: holds the simulation lock the way updateState
	// does while an agent is being updated.
	Thread tick = new Thread(() -> service.createLocation(request("Somewhere", holding, release)));
	tick.setDaemon(true);
	tick.start();

	assertTrue(holding.await(5, TimeUnit.SECONDS), "the stand-in tick never took the lock");

	try {
	    // Timed, not just completed: if this took the lock it would still
	    // finish once the stand-in tick released, so only the elapsed time
	    // distinguishes blocking from non-blocking.
	    long start = System.nanoTime();
	    service.addCharacteristic("Maria Lopez", "Maria is afraid of thunderstorms");
	    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

	    assertTrue(elapsedMs < 1000, "the edit waited " + elapsedMs + "ms, so it is blocking on the lock");
	    assertEquals(2, service.getCharacteristics("Maria Lopez").size(),
		    "the edit should have applied without waiting for the lock");
	} finally {
	    release.countDown();
	    tick.join(5000);
	}
    }

    @Test
    public void a_characteristic_can_be_removed_while_the_world_is_locked() throws Exception {
	World world = worldWithAgent();
	SimulationService service = new SimulationService(UNUSED_LLM, world);
	service.addCharacteristic("Maria Lopez", "Maria is afraid of thunderstorms");

	CountDownLatch holding = new CountDownLatch(1);
	CountDownLatch release = new CountDownLatch(1);

	Thread tick = new Thread(() -> service.createLocation(request("Elsewhere", holding, release)));
	tick.setDaemon(true);
	tick.start();

	assertTrue(holding.await(5, TimeUnit.SECONDS), "the stand-in tick never took the lock");

	try {
	    long start = System.nanoTime();
	    service.removeCharacteristic("Maria Lopez", 0);
	    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

	    assertTrue(elapsedMs < 1000, "the removal waited " + elapsedMs + "ms, so it is blocking on the lock");
	    assertEquals(1, service.getCharacteristics("Maria Lopez").size());
	} finally {
	    release.countDown();
	    tick.join(5000);
	}
    }

    @Test
    public void creating_an_agent_makes_no_model_call() {
	// Adding someone to the town is a change to world state and should cost
	// what that costs. The trait summary it used to block on is derived
	// from the characteristics just supplied, decorates one line of one
	// prompt, and is filled in by the next tick instead. UNUSED_LLM throws
	// if anything reaches the model, so this fails if that regresses.
	World world = worldWithAgent();
	SimulationService service = new SimulationService(UNUSED_LLM, world);

	CreateAgentRequest request = new CreateAgentRequest();
	request.setName("Klaus Mueller");
	request.setLocation("Cottage");
	request.setActivity("reading");
	request.setMemories(List.of("Klaus is a student"));

	long start = System.nanoTime();
	service.createAgent(request);
	long elapsedMs = (System.nanoTime() - start) / 1_000_000;

	assertTrue(elapsedMs < 1000, "creating an agent took " + elapsedMs + "ms");
	assertTrue(world.getAgent("Klaus Mueller").isPresent(), "the agent should exist immediately");
    }

    /**
     * A location request whose getName() blocks, so the calling thread parks
     * inside the locked section and holds the simulation lock until released.
     */
    private static io.github.nickm980.smallville.api.v1.dto.CreateLocationRequest request(String name,
	    CountDownLatch holding, CountDownLatch release) {
	return new io.github.nickm980.smallville.api.v1.dto.CreateLocationRequest() {
	    private boolean parked;

	    @Override
	    public String getName() {
		if (!parked) {
		    parked = true;
		    holding.countDown();

		    try {
			release.await(10, TimeUnit.SECONDS);
		    } catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		    }
		}

		return name;
	    }
	};
    }
}
