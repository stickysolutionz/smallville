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
     * Describes the world from one agent's point of view: where everybody is,
     * and nothing about what they are doing.
     * <p>
     * Activity text used to be included here. It had to go, twice over. First
     * it let one agent's invention become everyone's fact - somebody wrote
     * "laughing at a joke with Lindsey" and the others were told a conversation
     * was underway and wrote themselves into it. Labelling it as hearsay rather
     * than fact fixed that, but not the second problem: with nine people in one
     * room, handing each of them the others' activity text verbatim made them
     * copy it. An overnight run had three agents lying on the same hallway
     * floor "breathing slowly", word for word, then all making pancakes.
     * <p>
     * Location alone is what this actually needs to carry. It is what lets an
     * agent decide to go and find someone, and co-location is what the engine
     * uses to start conversations - none of which needs a script of what the
     * other person is up to.
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
	}

	result.setDescription(description.toString());

	return result;
    }
}
