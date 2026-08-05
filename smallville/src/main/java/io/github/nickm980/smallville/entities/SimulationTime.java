package io.github.nickm980.smallville.entities;

import java.time.Duration;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAmount;

import io.github.nickm980.smallville.exceptions.SmallvilleException;


public final class SimulationTime {
    private static final LocalDateTime START = LocalDateTime.now();

    private static volatile LocalDateTime time = LocalDateTime.now();
    /**
     * How much simulated time one tick advances.
     * <p>
     * Fixed rather than adjustable. Too much depends on it - the gap between
     * conversations in one place, how many ticks a plan spans, how often a day
     * rolls over - and every one of those was a number that quietly assumed a
     * particular value here. A twenty minute step gives an hourly plan three
     * ticks to play out in, which is what stops the plan being the behaviour.
     */
    public static final Duration STEP = Duration.ofMinutes(20);

    private static volatile Duration step = STEP;

    public static synchronized LocalDateTime now() { return time; }

    public static synchronized void setSimulationTime(LocalDateTime simTime) {
	time = simTime;
    }

    public static synchronized void setStep(Duration duration) {
	step = duration;
    }

    public static synchronized void update() {
	if (step == null || time == null) {
	    throw new SmallvilleException("Missing timestep or time");
	}

	time = time.plus(step);
    }

    public static synchronized LocalDateTime startedAt() {
	return START;
    }

    public static synchronized Duration getStepDuration() {
	return step;
    }

    public static int getStepDurationInMinutes() {
	return (int) getStepDuration().getSeconds()/60;
    }
}