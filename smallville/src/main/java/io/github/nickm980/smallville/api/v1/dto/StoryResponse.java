package io.github.nickm980.smallville.api.v1.dto;

public class StoryResponse {

    private String story;
    private boolean exists;
    private boolean updated;
    private String asOfDate;
    private String asOfTime;
    private long minutesSinceUpdate;
    private String message;

    public String getStory() {
	return story;
    }

    public void setStory(String story) {
	this.story = story;
    }

    public boolean isExists() {
	return exists;
    }

    public void setExists(boolean exists) {
	this.exists = exists;
    }

    public boolean isUpdated() {
	return updated;
    }

    public void setUpdated(boolean updated) {
	this.updated = updated;
    }

    public String getAsOfDate() {
	return asOfDate;
    }

    public void setAsOfDate(String asOfDate) {
	this.asOfDate = asOfDate;
    }

    public String getAsOfTime() {
	return asOfTime;
    }

    public void setAsOfTime(String asOfTime) {
	this.asOfTime = asOfTime;
    }

    public long getMinutesSinceUpdate() {
	return minutesSinceUpdate;
    }

    public void setMinutesSinceUpdate(long minutesSinceUpdate) {
	this.minutesSinceUpdate = minutesSinceUpdate;
    }

    public String getMessage() {
	return message;
    }

    public void setMessage(String message) {
	this.message = message;
    }
}
