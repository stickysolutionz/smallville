package io.github.nickm980.smallville.update;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.nickm980.smallville.Util;
import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.memory.Observation;
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

	return next(service, world, agent, info);
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
