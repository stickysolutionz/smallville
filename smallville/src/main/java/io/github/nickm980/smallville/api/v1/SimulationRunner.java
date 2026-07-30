package io.github.nickm980.smallville.api.v1;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the simulation forward on a timer by repeatedly calling
 * {@link SimulationService#updateState()}. The server has no built-in clock
 * otherwise - without this, the world only advances when something calls
 * POST /state.
 */
public class SimulationRunner {

    private final static Logger LOG = LoggerFactory.getLogger(SimulationRunner.class);
    private final static double MIN_INTERVAL_SECONDS = 2;

    private final SimulationService service;
    private ScheduledExecutorService executor;
    private volatile double intervalSeconds = 15;
    private volatile boolean running = false;
    private volatile String lastError = null;
    private volatile long tickCount = 0;

    public SimulationRunner(SimulationService service) {
	this.service = service;
    }

    public synchronized void start(double intervalSeconds) {
	if (intervalSeconds < MIN_INTERVAL_SECONDS) {
	    throw new IllegalArgumentException("intervalSeconds must be at least " + MIN_INTERVAL_SECONDS);
	}

	stop();

	this.intervalSeconds = intervalSeconds;
	this.running = true;
	this.lastError = null;
	this.executor = Executors.newSingleThreadScheduledExecutor();

	long delayMs = Math.round(intervalSeconds * 1000);

	executor.scheduleWithFixedDelay(() -> {
	    try {
		service.updateState();
		tickCount++;
		lastError = null;
	    } catch (Exception e) {
		lastError = e.getMessage();
		LOG.error("Simulation tick failed", e);
	    }
	}, 0, delayMs, TimeUnit.MILLISECONDS);

	LOG.info("Simulation started, ticking every " + intervalSeconds + "s");
    }

    public synchronized void stop() {
	if (executor != null) {
	    executor.shutdownNow();
	    executor = null;
	}
	running = false;
    }

    public boolean isRunning() {
	return running;
    }

    public double getIntervalSeconds() {
	return intervalSeconds;
    }

    public String getLastError() {
	return lastError;
    }

    public long getTickCount() {
	return tickCount;
    }
}
