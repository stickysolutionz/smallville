package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.ChatGPT;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.MemoryStream;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.prompts.ChatService;

public class MemoryStreamTest {

    @Test
    public void test_observation_is_added_to_stream() {
	MemoryStream stream = new MemoryStream();
	stream.add(new Observation("memory"));

	assertEquals(1, stream.getObservations().size());
    }

    @Test
    public void test_correct_relevant_memories_and_correct_amount_are_fetched() {
	MemoryStream stream = new MemoryStream();
	stream.add(new Observation("memory"));
	stream.add(new Observation(""));
	stream.add(new Observation("2"));
	stream.add(new Observation("3"));
	stream.add(new Observation("4"));
	stream.add(new Observation("5"));
	stream.add(new Observation("6"));
	stream.add(new Observation("7"));
	stream.add(new Observation("8"));
	stream.add(new Observation("memory two"));
	stream.add(new Observation("9"));

	List<Memory> memories = stream.getRelevantMemories("memory", -1);

	assertEquals(3, memories.size());
	assertEquals("memory", memories.get(0).getDescription());
	assertEquals("memory two", memories.get(1).getDescription());
    }

    @Test
    public void test_correct_relevant_memories_and_correct_amount_are_fetched2() {
	MemoryStream stream = new MemoryStream();
	stream.add(new Observation("i love playing basketball"));
	stream.add(new Observation("on saturday i slept in"));
	stream.add(new Observation("i completed my homework"));
	stream.add(new Observation("finished my homework"));
	stream.add(new Observation("woke up and made breakfast"));
	stream.add(new Observation("played video games for an hour"));
	stream.add(new Observation("played soccer for an hour"));
	stream.add(new Observation("played Battlefield 1 for an hour"));
	stream.add(new Observation("likes to play video games"));
	stream.add(new Observation("saw a bird fly by"));
	stream.add(new Observation("memory"));
	stream.add(new Observation("memory two"));

	List<Memory> memories = stream.getRelevantMemories("memory", 0);

	assertEquals(3, memories.size());
	assertEquals("memory", memories.get(0).getDescription());
	assertEquals("memory two", memories.get(1).getDescription());
    }

    @Test
    public void test_adding_and_getting_plans_from_memory_stream() {
	ChatService service = new ChatService(new World(), new ChatGPT());
	List<Plan> plans = service.parsePlans("""
		\nI will then go to the Green House and sleep from 12:05 AM.
		\nI will wake up and make breakfast from 11:30 PM - 9:30 AM.
		\nI will then go to the Forest and spend some time gathering branches from 10:00 AM - 11:00 AM.
				""");
	MemoryStream stream = new MemoryStream();

	stream.addAll(plans);

	assertEquals(3, stream.getPlans().size());
    }

    @Test
    public void hyphens_inside_a_memory_are_preserved() {
	// The constructor used to delete every hyphen anywhere in the text, so
	// every memory an agent formed was silently rewritten.
	assertEquals("Maria is well-known for her half-finished projects",
		new Observation("Maria is well-known for her half-finished projects").getDescription());
	assertEquals("worked the 3:00-4:00 shift", new Observation("worked the 3:00-4:00 shift").getDescription());
    }

    @Test
    public void a_leading_list_marker_is_still_stripped() {
	assertEquals("wake up and get dressed", new Observation("- wake up and get dressed").getDescription());
	assertEquals("wake up and get dressed", new Observation("* wake up and get dressed").getDescription());
	assertEquals("wake up and get dressed", new Observation("2. wake up and get dressed").getDescription());
    }

    @Test
    public void identically_scoring_memories_do_not_destroy_each_other() {
	// The old implementation collected candidates into a
	// Map<Double, Integer> keyed by score. Unweighted memories with no
	// relevance to the query all score exactly the same, so all but one of
	// these used to vanish before ranking began.
	MemoryStream stream = new MemoryStream();

	for (int i = 0; i < 8; i++) {
	    stream.add(new Observation("completely unrelated filler text"));
	}

	assertEquals(5, stream.getRelevantMemories("something else entirely", 0, 5).size());
    }

    @Test
    public void retrieval_count_is_honoured_rather_than_hardcoded() {
	MemoryStream stream = new MemoryStream();

	for (int i = 0; i < 10; i++) {
	    stream.add(new Observation("memory number " + i));
	}

	assertEquals(1, stream.getRelevantMemories("memory", 0, 1).size());
	assertEquals(7, stream.getRelevantMemories("memory", 0, 7).size());
	// Asking for more than exist yields everything, not an error.
	assertEquals(10, stream.getRelevantMemories("memory", 0, 50).size());
    }

    @Test
    public void a_small_stream_is_still_ranked_not_returned_wholesale() {
	// Previously any stream of three or fewer candidates was returned in
	// insertion order without ranking at all.
	MemoryStream stream = new MemoryStream();
	stream.add(new Observation("the tractor needs a new fuel filter"));
	stream.add(new Observation("memory"));

	List<Memory> memories = stream.getRelevantMemories("memory", 0, 1);

	assertEquals(1, memories.size());
	assertEquals("memory", memories.get(0).getDescription());
    }

    @Test
    public void scores_stay_within_the_configured_weight_range() {
	// Every component is bounded 0-1 and the three weights default to 1,
	// so no memory may score outside 0-3. Recency alone used to return
	// values large enough to make the whole sum infinite or NaN.
	MemoryStream stream = new MemoryStream();
	Observation recent = new Observation("just made a pot of coffee");
	stream.add(recent);
	recent.setImportance(7);

	double score = recent.getScore("coffee");

	assertTrue(score >= 0 && score <= 3, "score out of range: " + score);
	assertFalse(Double.isNaN(score), "score was NaN");
    }

    @Test
    public void an_older_memory_is_less_recent_than_a_newer_one() {
	Observation now = new Observation("happening right now");
	Observation earlier = new Observation("happened a while ago", SimulationTime.now().minusHours(24), 0);

	assertTrue(now.getScore("x") > earlier.getScore("x"),
		"a memory from 24 simulated hours ago should score below one from now");
    }
}
