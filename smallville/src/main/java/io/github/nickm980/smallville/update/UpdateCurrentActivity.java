package io.github.nickm980.smallville.update;

import java.util.Optional;

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

	agent.getMemoryStream().add(new Observation(activity.getLastActivity()));

	return next(service, world, agent, info);
    }
}
