package io.github.nickm980.smallville;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.exceptions.SmallvilleException;
import io.github.nickm980.smallville.repository.Repository;

/**
 * Storage for agents locations objects and conversations
 */
public class World {
    private Repository<Location> locations;
    private Repository<Conversation> conversations;
    private Repository<Agent> agents;
    private final Logger LOG = LoggerFactory.getLogger(World.class);

    public World() {
	this.locations = new Repository<>();
	this.agents = new Repository<>();
	this.conversations = new Repository<>();
    }

    public void create(Conversation conversation) {
	if (conversation.size() == 0) {
	    throw new SmallvilleException("Cannot have an empty conversation");
	}

	List<String> participants = conversation.getParticipants();

	if (participants.size() < 2) {
	    throw new SmallvilleException("A conversation must have at least two participants");
	}

	if (new java.util.HashSet<>(participants).size() != participants.size()) {
	    throw new SmallvilleException("A conversation cannot have duplicate participants");
	}

	conversations.save(UUID.randomUUID().toString(), conversation);
    }

    public boolean create(Agent agent) {
	return agents.save(agent.getFullName(), agent);
    }

    public void create(Location location) {
	locations.save(location.getFullPath(), location);
    }

    public List<Agent> getAgents() {
	return agents.all();
    }

    public List<Location> getLocations() {
	return locations.all();
    }

    public Optional<Location> getLocation(String locationName) {
	return Optional.ofNullable(locations.getById(locationName));
    }

    public Optional<Agent> getAgent(String name) {
	return Optional.ofNullable(agents.getById(name));
    }

    public boolean deleteAgent(String name) {
	return agents.delete(name);
    }

    public boolean deleteLocation(String name) {
	return locations.delete(name);
    }

    /**
     * Wipes all conversations and every agent's diary history, leaving
     * agents (and their characteristics) and locations untouched.
     */
    public void resetSimulationData() {
	conversations.clear();
	for (Agent agent : agents.all()) {
	    agent.getMemoryStream().clearDiary();
	    agent.setCurrentActivity("idle");
	}
    }

    public List<Conversation> getConversationsAfter(LocalDateTime time) {
	return conversations.all();
    }

    public List<Conversation> getAllConversations() {
	return conversations.all();
    }

    public void setState(String object, String state) {
	Location obj = getLocation(object).orElseThrow();

	LOG.info("Changing state. " + object + ": " + state);
	obj.setState(state);
    }
}
