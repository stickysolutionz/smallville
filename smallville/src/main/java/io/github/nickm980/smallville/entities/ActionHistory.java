package io.github.nickm980.smallville.entities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ActionHistory {

    /**
     * How many recent activities to keep.
     * <p>
     * One step back is not enough to notice a loop. Told only "you were
     * dancing", the model reasonably answers "getting a drink"; told only "you
     * were getting a drink", it answers "dancing" - and an agent spent ninety
     * minutes going back and forth between the two. Seeing several in a row is
     * what makes the pattern visible.
     */
    private static final int REMEMBERED = 5;

    private String activity;
    private String lastActivity;
    private String emoji;
    private final Deque<String> recent = new ArrayDeque<>();

    public ActionHistory(String action) {
	this.activity = action;
 	this.lastActivity = action;
    }

    public String getActivity() {
	return activity;
    }

    public String getLastActivity() {
	return lastActivity;
    }

    /**
     * The last few things this agent was doing, oldest first.
     */
    public List<String> getRecentActivities() {
	return new ArrayList<>(recent);
    }

    public void setActivity(String activity) {
	this.lastActivity = this.activity;
	this.activity = activity;

	if (this.lastActivity != null && !this.lastActivity.isBlank()) {
	    recent.addLast(this.lastActivity);

	    while (recent.size() > REMEMBERED) {
		recent.removeFirst();
	    }
	}
    }

    public void setEmoji(String emoji) {
	this.emoji = emoji;
    }

    public String getEmoji() {
	return emoji;
    }
}
