package io.github.nickm980.smallville.prompts.dto;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;

public class WorldModel {

    private String description;

    public String getDescription() {
	return description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    /**
     * Describes the world from one agent's point of view.
     * <p>
     * Where somebody is comes from the world and is stated as fact. What they
     * appear to be doing is their own generated account of themselves, and is
     * phrased so it reads that way. Previously both halves were one flat
     * sentence that the activity prompt called ground truth, so when one agent
     * wrote "laughing at a joke with Lindsey" every other agent in the room was
     * told, as fact, that a conversation was underway - and wrote themselves
     * into it. One fabrication became everybody's memory.
     */
    public static WorldModel fromWorld(String name, World world) {
	WorldModel result = new WorldModel();
	StringBuilder description = new StringBuilder("Available Locations: ");

	for (Location location : world.getLocations()) {
	    description.append(location.getFullPath()).append("; ");
	}

	for (Agent agent : world.getAgents()) {
	    if (agent.getFullName().equals(name) || agent.getLocation() == null) {
		continue;
	    }

	    description
		.append("\n")
		.append(agent.getFullName())
		.append(" is at ")
		.append(agent.getLocation().getFullPath());

	    String activity = agent.getCurrentActivity();

	    if (activity != null && !activity.isBlank()) {
		description.append(" and appears to be ").append(activity);
	    }
	}

	result.setDescription(description.toString());

	return result;
    }
}
