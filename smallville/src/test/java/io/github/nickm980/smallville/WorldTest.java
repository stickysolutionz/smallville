package io.github.nickm980.smallville;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Dialog;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.exceptions.SmallvilleException;

public class WorldTest {

    private World world;

    @BeforeEach
    public void setUp() {
	world = new World();
    }

    @Test
    public void test_world_locations() {
	assertTrue(world.getLocation("missing location").isEmpty());

	world.create(new Location("location name"));

	assertTrue(world.getLocation("location name").isPresent());

	world.setState("location name", "empty");

	assertTrue(world.getLocation("location name").get().getState().equals("empty"));
    }

    @Test
    public void test_saving_null_location_does_not_throw_error() {
	assertThrows(Exception.class, () -> {
	    world.setState(null, null);
	});
    }

    @Test
    public void test_world_conversation_creation() {
	// Deliberately simulation time, not LocalDateTime.now(). Conversations
	// are stamped with SimulationTime.now(), which is a separate clock that
	// only advances when the simulation ticks - comparing them against the
	// wall clock is meaningless. This test previously passed a wall-clock
	// time and still expected a match, which only worked because
	// getConversationsAfter ignored its argument entirely.
	LocalDateTime beforeConversation = SimulationTime.now().minusMinutes(1);

	assertEquals(0, world.getConversationsAfter(beforeConversation).size());

	Conversation conversation = new Conversation(List.of("none", "other"), List.of(new Dialog("john", "hi")));
	world.create(conversation);

	assertEquals(1, world.getConversationsAfter(beforeConversation).size());

	// ...and is genuinely excluded by a cutoff after it happened.
	assertEquals(0, world.getConversationsAfter(SimulationTime.now().plusMinutes(1)).size());

	assertThrows(SmallvilleException.class, () -> {
	    world.create(new Conversation(List.of("name", "name"), List.of(new Dialog("name", "message"))));
	});

	assertThrows(SmallvilleException.class, () -> {
	    world.create(new Conversation(List.of("name", "name"), List.of()));
	});
    }
}
