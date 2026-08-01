package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.nickm980.smallville.story.StorySnapshot;

public class StorySnapshotTest {

    private static ObjectMapper mapper() {
	ObjectMapper mapper = new ObjectMapper();
	mapper.registerModule(new JavaTimeModule());
	mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	return mapper;
    }

    @Test
    public void the_whole_story_is_shown_even_once_older_passages_are_summarised() {
	// The reader must never lose prose to compaction - only the prompt is
	// shortened, not the narrative on disk.
	StorySnapshot snapshot = new StorySnapshot(List.of("first", "second", "third"), "a summary of the first two", 2,
		LocalDateTime.now());

	assertEquals("first\n\nsecond\n\nthird", snapshot.getStory());
    }

    @Test
    public void the_prompt_sees_the_summary_plus_only_unsummarised_passages() {
	StorySnapshot snapshot = new StorySnapshot(
		List.of("Maria opened the cafe.", "Klaus lost his notebook.", "Bill mended the fence."),
		"An account of what came before.", 2, LocalDateTime.now());

	String context = snapshot.getPromptContext();

	assertTrue(context.contains("An account of what came before."));
	assertTrue(context.contains("Bill mended the fence."));
	assertFalse(context.contains("Maria opened the cafe."), "summarised passages must not also be sent verbatim");
	assertFalse(context.contains("Klaus lost his notebook."), "summarised passages must not also be sent verbatim");
    }

    @Test
    public void prompt_context_stays_bounded_as_the_story_grows() {
	// The whole point of the change: an unbounded story must not produce an
	// unbounded prompt. Passages are realistically sized, since a summary
	// only saves room once it is shorter than what it replaces.
	String passage = "A reasonably long passage of narrative prose describing an afternoon in the town. ".repeat(4);

	List<String> twelve = new java.util.ArrayList<>();
	for (int i = 0; i < 12; i++) {
	    twelve.add(passage + i);
	}

	StorySnapshot small = new StorySnapshot(twelve.subList(0, 3), "", 0, LocalDateTime.now());
	StorySnapshot large = new StorySnapshot(twelve, "A short standing summary of the first six.", 6,
		LocalDateTime.now());

	assertEquals(3, small.passagesAfterSummary().size());
	assertEquals(6, large.passagesAfterSummary().size());
	assertTrue(large.getStory().length() > large.getPromptContext().length(),
		"the stored story should outgrow what is sent to the model");

	// And the bound holds regardless of how much further the story runs.
	List<String> hundred = new java.util.ArrayList<>();
	for (int i = 0; i < 100; i++) {
	    hundred.add(passage + i);
	}

	StorySnapshot huge = new StorySnapshot(hundred, "A short standing summary.", 94, LocalDateTime.now());

	assertEquals(6, huge.passagesAfterSummary().size());
	assertTrue(huge.getPromptContext().length() < large.getPromptContext().length() * 2,
		"prompt context must not grow with the length of the story");
    }

    @Test
    public void a_story_file_written_before_passages_existed_still_loads() throws Exception {
	String legacy = "{\"story\":\"Once upon a time in the town.\",\"asOf\":\"2026-08-01T13:00:00\"}";

	StorySnapshot snapshot = mapper().readValue(legacy, StorySnapshot.class);

	assertEquals(List.of("Once upon a time in the town."), snapshot.getPassages());
	assertEquals("Once upon a time in the town.", snapshot.getStory());
	assertFalse(snapshot.isEmpty());
    }

    @Test
    public void a_snapshot_survives_a_round_trip_through_json() throws Exception {
	StorySnapshot original = new StorySnapshot(List.of("one", "two"), "the summary", 1,
		LocalDateTime.of(2026, 8, 1, 13, 0));

	StorySnapshot restored = mapper().readValue(mapper().writeValueAsString(original), StorySnapshot.class);

	assertEquals(List.of("one", "two"), restored.getPassages());
	assertEquals("the summary", restored.getSummary());
	assertEquals(1, restored.getSummarisedThrough());
	assertEquals(original.getAsOf(), restored.getAsOf());
    }

    @Test
    public void a_fresh_snapshot_reads_as_empty() {
	assertTrue(new StorySnapshot().isEmpty());
    }
}
