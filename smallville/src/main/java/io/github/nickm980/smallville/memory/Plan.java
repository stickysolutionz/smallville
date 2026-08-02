package io.github.nickm980.smallville.memory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.math.SmallvilleMath;

/**
 * Plans are basically just memories in the future tense. There are more events
 * closer to the current time and later plans are more spread out. Should always
 * have enough plans for the rest of the day but need to recalculate
 * periodically
 */
public class Plan extends Memory implements TemporalMemory {

    private final LocalDateTime time;
    public PlanType type;
    /**
     * The location this plan is about, kept separately from the description so
     * the simulation can tell whether the agent has actually been there.
     */
    private String location;
    /**
     * Whether the agent has since spent time where this plan meant to take
     * them. Without it a daily intention like "pick up cat food" is restated
     * every hour forever, because nothing records having gone.
     */
    private boolean addressed;

    public Plan(String description, LocalDateTime time) {
	this(description, time, PlanType.LONG_TERM);
    }

    public Plan(String description, LocalDateTime time, PlanType type) {
	super(description);
	this.time = time;
	this.type = type;
    }

    public String getLocation() {
	return location;
    }

    public void setLocation(String location) {
	this.location = location;
    }

    public boolean isAddressed() {
	return addressed;
    }

    public void setAddressed(boolean addressed) {
	this.addressed = addressed;
    }

    public PlanType getType() {
	return type;
    }

    public LocalDateTime getTime() {
	return time;
    }

    @Override
    double getRecency() {
	return recencyOf(time);
    }

    public void convert(PlanType type) {
	this.type = type;
    }
}