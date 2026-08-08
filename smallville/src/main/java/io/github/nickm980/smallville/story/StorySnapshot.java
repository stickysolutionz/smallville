package io.github.nickm980.smallville.story;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Persisted on disk as story-data/story.json.
 * <p>
 * The story is held as a list of passages rather than one accumulated string.
 * Every passage is kept forever, because the dashboard renders the whole
 * narrative and compacting what the reader sees would lose the prose. What is
 * bounded is the prompt: only the passages after {@link #summarisedThrough} are
 * sent verbatim, preceded by {@link #summary}, a compressed account of
 * everything before them. Otherwise the entire story is re-sent on every
 * regeneration and eventually stops fitting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorySnapshot {

    private String summary = "";
    private List<String> passages = new ArrayList<>();
    /** How many leading passages {@link #summary} already accounts for. */
    private int summarisedThrough;
    private LocalDateTime asOf;

    public StorySnapshot() {
    }

    public StorySnapshot(List<String> passages, String summary, int summarisedThrough, LocalDateTime asOf) {
	this.passages = new ArrayList<>(passages);
	this.summary = summary == null ? "" : summary;
	this.summarisedThrough = summarisedThrough;
	this.asOf = asOf;
    }

    public String getSummary() {
	return summary;
    }

    public void setSummary(String summary) {
	this.summary = summary == null ? "" : summary;
    }

    public List<String> getPassages() {
	return passages;
    }

    public void setPassages(List<String> passages) {
	this.passages = passages == null ? new ArrayList<>() : new ArrayList<>(passages);
    }

    public int getSummarisedThrough() {
	return summarisedThrough;
    }

    public void setSummarisedThrough(int summarisedThrough) {
	this.summarisedThrough = summarisedThrough;
    }

    public LocalDateTime getAsOf() {
	return asOf;
    }

    public void setAsOf(LocalDateTime asOf) {
	this.asOf = asOf;
    }

    /**
     * Reads a story.json written before the story was split into passages, so
     * an existing run keeps its narrative across the upgrade.
     */
    @JsonProperty("story")
    public void setLegacyStory(String story) {
	if (story != null && !story.isBlank() && passages.isEmpty()) {
	    passages = new ArrayList<>(List.of(story.trim()));
	}
    }

    /** The whole narrative in order, as the dashboard displays it. */
    @JsonIgnore
    public String getStory() {
	return String.join("\n\n", passages);
    }

    /**
     * The part of the story the model is shown for continuity: the summary of
     * older passages, then the most recent ones verbatim.
     */
    @JsonIgnore
    public String getPromptContext() {
	List<String> parts = new ArrayList<>();

	if (!summary.isBlank()) {
	    parts.add("Summary of earlier events: " + summary);
	}

	parts.addAll(passagesAfterSummary());

	return String.join("\n\n", parts);
    }

    @JsonIgnore
    public List<String> passagesAfterSummary() {
	int from = Math.max(0, Math.min(summarisedThrough, passages.size()));

	return passages.subList(from, passages.size());
    }

    @JsonIgnore
    public boolean isEmpty() {
	return passages.isEmpty() && summary.isBlank();
    }
}
