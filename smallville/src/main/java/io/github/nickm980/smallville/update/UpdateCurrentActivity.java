package io.github.nickm980.smallville.update;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.nickm980.smallville.Util;
import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.prompts.Prompts;
import io.github.nickm980.smallville.prompts.dto.CurrentActivity;

public class UpdateCurrentActivity extends AgentUpdate {

    @Override
    public boolean update(Prompts service, World world, Agent agent, UpdateInfo info) {
	LOG.info("[Activity] Updating current activity and emoji");

	CurrentActivity activity = service.getCurrentActivity(agent);
	LOG.debug(activity.getLocation());

	if (activity.getActivity() != null && !activity.getActivity().isBlank()) {
	    agent.setCurrentActivity(activity.getActivity());
	} else {
	    LOG.warn("[Activity] Model response didn't include an activity. Keeping previous activity for "
		    + agent.getFullName());
	}

	agent.setCurrentEmoji(activity.getEmoji());

	String locationName = activity.getLocation();
	Optional<Location> location = world.getLocation(locationName);

	if (location.isEmpty() && locationName != null && locationName.contains(":")) {
	    location = world.getLocation(locationName.split(":")[0].trim());
	}

	if (location.isPresent()) {
	    agent.setLocation(location.get());
	} else {
	    LOG.warn("[Activity] Model returned unknown location '" + locationName + "'. Keeping agent at "
		    + agent.getLocation().getFullPath());
	}

	agent.getMemoryStream().add(new Observation(recordOf(activity.getLastActivity(), agent, world)));
	markGoalsReached(agent);

	return next(service, world, agent, info);
    }

    /**
     * Marks any daily goal whose location the agent is now standing in as
     * addressed.
     * <p>
     * Deliberately crude - being somewhere is not the same as having done the
     * thing. But without any notion of a goal being met, "pick up cat food"
     * is restated every hour for the rest of the day, because nothing records
     * having gone to the shop.
     */
    private static void markGoalsReached(Agent agent) {
	if (agent.getLocation() == null) {
	    return;
	}

	String here = agent.getLocation().getFullPath();

	for (Plan plan : agent.getMemoryStream().getPlans(PlanType.LONG_TERM)) {
	    if (plan.isAddressed() || plan.getLocation() == null) {
		continue;
	    }

	    // Goals name a location which may be more or less specific than
	    // where the agent ended up, so either containing the other counts.
	    String there = plan.getLocation();

	    if (here.equalsIgnoreCase(there) || here.toLowerCase().startsWith(there.toLowerCase())
		    || there.toLowerCase().startsWith(here.toLowerCase())) {
		plan.setAddressed(true);
	    }
	}
    }

    /**
     * Builds what the agent will remember of this moment.
     * <p>
     * This used to be the model's own activity text, past-tensed and filed
     * straight into the memory stream. Nothing checked it against the
     * simulation, so a plan saying "join in the conversation and share a joke"
     * became a first-hand memory of a conversation that never happened - and
     * from there fed reflection, retrieval and the story. Two agents in the
     * same aisle ended up with contradictory recollections of the same minute.
     * <p>
     * The activity half is still the model's, because that is where the texture
     * lives, but the prompt now forbids it referring to anyone else. Who was
     * actually there comes from the world, so the social half of the memory is
     * something the simulation can stand behind.
     */
    private static String recordOf(String activity, Agent agent, World world) {
	String where = agent.getLocation() == null ? null : agent.getLocation().getFullPath();
	String what = activity == null ? "" : activity.trim();

	StringBuilder record = new StringBuilder(what.isEmpty() ? "Spent time" : what);

	if (where != null) {
	    record.append(what.toLowerCase().contains(where.toLowerCase()) ? "" : " at " + where);
	}

	List<String> alsoHere = othersAt(agent, world);

	if (!alsoHere.isEmpty()) {
	    record.append(", where ").append(join(alsoHere)).append(alsoHere.size() == 1 ? " also was" : " also were");
	}

	return record.toString();
    }

    private static List<String> othersAt(Agent agent, World world) {
	if (agent.getLocation() == null) {
	    return List.of();
	}

	String here = agent.getLocation().getFullPath();

	return world
	    .getAgents()
	    .stream()
	    .filter(other -> !other.getFullName().equals(agent.getFullName()))
	    .filter(other -> other.getLocation() != null && here.equals(other.getLocation().getFullPath()))
	    .map(Agent::getFullName)
	    .sorted()
	    .collect(Collectors.toList());
    }

    private static String join(List<String> names) {
	if (names.size() == 1) {
	    return names.get(0);
	}

	return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
