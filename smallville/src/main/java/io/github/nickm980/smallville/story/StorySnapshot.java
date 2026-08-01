package io.github.nickm980.smallville.story;

import java.time.LocalDateTime;

// Persisted on disk as story-data/story.json. Deliberately just these two
// fields - the accumulated prose and the in-world timestamp it's caught up
// through - not a history of past snapshots.
public class StorySnapshot {

    private String story;
    private LocalDateTime asOf;

    public StorySnapshot() {
    }

    public StorySnapshot(String story, LocalDateTime asOf) {
	this.story = story;
	this.asOf = asOf;
    }

    public String getStory() {
	return story;
    }

    public void setStory(String story) {
	this.story = story;
    }

    public LocalDateTime getAsOf() {
	return asOf;
    }

    public void setAsOf(LocalDateTime asOf) {
	this.asOf = asOf;
    }
}
