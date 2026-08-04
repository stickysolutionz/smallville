package io.github.nickm980.smallville.update;

import java.time.Duration;
import java.time.LocalDateTime;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.prompts.Prompts;

public class UpdateReflection extends AgentUpdate {

    @Override
    public boolean update(Prompts service, World world, Agent agent, UpdateInfo info) {
	/*
	 * The score to cutoff memories by. Fine tune this value to get the desired
	 * result. Relfections should be triggered 2-3 times per day. Fine tune this
	 * value to vary results
	 */
	int cutoff = SmallvilleConfig.getConfig().getReflectionCutoff();
	double since = agent.getMemoryStream().importanceSinceLastReflection();

	LOG.info("[Reflections] Weight since last reflection: " + since + " / " + cutoff);

	if (since > cutoff && longEnoughSinceLast(agent)) {
	    LOG.info("[Reflections] Reflecting on recent memories");
	    Reflection reflection = service.createReflectionFor(agent);
	    agent.getMemoryStream().add(reflection);
	    agent.getMemoryStream().markReflected();
	    LOG.info("[Reflections] Reflection: " + reflection.getDescription());
	}

	return next(service, world, agent, info);
    }

    /**
     * A hard floor in simulated time, independent of any weighting.
     * <p>
     * Reflection is the most expensive thing an agent does - two model calls,
     * one of them carrying every recent memory. The weighting above should keep
     * it rare on its own, but it depends on importance scores that come back
     * from another model call and are sometimes not parsed at all. This makes
     * the rate impossible to run away with regardless.
     */
    private static boolean longEnoughSinceLast(Agent agent) {
	LocalDateTime last = agent.getMemoryStream().getLastReflectedAt();

	return last == null || !SimulationTime.now().isBefore(last.plus(MINIMUM_GAP));
    }

    private static final Duration MINIMUM_GAP = Duration.ofHours(3);
}
