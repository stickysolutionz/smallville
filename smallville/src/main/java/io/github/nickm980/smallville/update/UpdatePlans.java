package io.github.nickm980.smallville.update;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.nlp.LocalNLP;
import io.github.nickm980.smallville.nlp.NLPCoreUtils;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.Prompts;
import io.github.nickm980.smallville.prompts.dto.Reaction;

/**
 * The UpdateFuturePlans class is responsible for creating and updating the
 * short and long term plans of an agent based on reactable observations.
 */
public class UpdatePlans extends AgentUpdate {

    @Override
    public boolean update(Prompts converter, World world, Agent agent, UpdateInfo info) {
	LOG.info("[Plans] Updating plans...");
	boolean hasPlans = !agent.getMemoryStream().getPlans().isEmpty();
	boolean shouldUpdatePlans = !hasPlans;
	String observation = info.getObservation();
	
	if (observation != null && !observation.isEmpty()) {
	    LOG.info("starting reaction to an observation");
	    Reaction reaction = converter.shouldUpdatePlans(agent, observation);
	    shouldUpdatePlans = reaction.getAnswer().toLowerCase().contains("yes");
	    info.setShouldUpdateConversation(reaction.getConversation().toLowerCase().contains("yes"));
	}

	if (shouldUpdatePlans) {
	    LOG.info("[Plans] Reacting to observation [" + info.getObservation() + "]");
	    agent.getMemoryStream().prunePlans(PlanType.LONG_TERM);
	    agent.getMemoryStream().prunePlans(PlanType.SHORT_TERM);
	    updatePlans(converter, agent, PlanType.LONG_TERM);
	    updatePlans(converter, agent, PlanType.SHORT_TERM);
	}

	// Nothing else expires a plan. Without this an agent generates plans on
	// their very first tick and works from them forever - a schedule written
	// at 9pm was still steering them at 3am, and a day's goals never rolled
	// over into the next day, so an unattended overnight run had nobody
	// waking up. Dropping the stale ones lets the checks below regenerate
	// them.
	if (isStale(agent, PlanType.LONG_TERM)) {
	    LOG.info("[Plans] Daily goals are from a previous day, replanning");
	    agent.getMemoryStream().prunePlans(PlanType.LONG_TERM);
	}

	if (isStale(agent, PlanType.SHORT_TERM)) {
	    LOG.info("[Plans] The hour these plans covered has passed, replanning");
	    agent.getMemoryStream().prunePlans(PlanType.SHORT_TERM);
	}

	if (agent.getMemoryStream().getPlans(PlanType.LONG_TERM).isEmpty()) {
	    updatePlans(converter, agent, PlanType.LONG_TERM);
	}

	if (agent.getMemoryStream().getPlans(PlanType.SHORT_TERM).isEmpty()) {
	    updatePlans(converter, agent, PlanType.SHORT_TERM);
	}

	LOG.info("[Plans] Plans updated");

	info.setPlansUpdated(shouldUpdatePlans);
	return next(converter, world, agent, info);
    }

    /**
     * How long a set of short-term plans stays current. They cover "the next
     * hour", so once a simulated hour has passed they describe a moment that
     * has been and gone.
     */
    private static final Duration SHORT_TERM_LIFETIME = Duration.ofHours(1);

    /**
     * Whether plans of this type were made too long ago to still be steering
     * the agent.
     * <p>
     * Judged from when the plan was written, not the time it names. A plan made
     * late at night for an early hour names a time that reads as almost a day
     * in the past, which would mark it stale immediately and regenerate it on
     * every tick.
     */
    private static boolean isStale(Agent agent, PlanType type) {
	List<Plan> plans = agent.getMemoryStream().getPlans(type);

	if (plans.isEmpty()) {
	    return false;
	}

	LocalDateTime madeAt = plans
	    .stream()
	    .map(Plan::getCreatedAt)
	    .filter(java.util.Objects::nonNull)
	    .max(LocalDateTime::compareTo)
	    .orElse(null);

	if (madeAt == null) {
	    return false;
	}

	LocalDateTime now = SimulationTime.now();

	if (type == PlanType.LONG_TERM) {
	    // A day's goals belong to that day.
	    return !madeAt.toLocalDate().equals(now.toLocalDate());
	}

	return !now.isBefore(madeAt.plus(SHORT_TERM_LIFETIME));
    }

    private void updatePlans(Prompts converter, Agent agent, PlanType type) {
	List<Plan> plans = type == PlanType.LONG_TERM ? converter.getPlans(agent) : converter.getShortTermPlans(agent);

	for (Plan plan : plans) {
	    plan.convert(type);
	    LOG.debug("[Plans] " + plan.getType() + " " + plan.getDescription());
	}

	agent.getMemoryStream().setPlans(plans, type);

	LOG.info("[Plans] Updated " + type.toString() + " plans");
    }
}
