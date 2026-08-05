package io.github.nickm980.smallville.persistence;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Dialog;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Concern;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.persistence.WorldSnapshot.AgentSnapshot;
import io.github.nickm980.smallville.persistence.WorldSnapshot.ConversationSnapshot;
import io.github.nickm980.smallville.persistence.WorldSnapshot.DialogSnapshot;
import io.github.nickm980.smallville.persistence.WorldSnapshot.LocationSnapshot;
import io.github.nickm980.smallville.persistence.WorldSnapshot.MemorySnapshot;
import io.github.nickm980.smallville.persistence.WorldSnapshot.RelationshipSnapshot;
import io.github.nickm980.smallville.relationships.Relationship;
import io.github.nickm980.smallville.relationships.RelationshipGraph;

/**
 * Converts between the live world and its stored form.
 */
public final class WorldMapper {

    private static final Logger LOG = LoggerFactory.getLogger(WorldMapper.class);

    private WorldMapper() {
    }

    public static WorldSnapshot toSnapshot(World world) {
	WorldSnapshot snapshot = new WorldSnapshot();

	snapshot.setSimulationTime(SimulationTime.now());
	snapshot.setStepMinutes(SimulationTime.getStepDuration().toMinutes());

	for (Location location : world.getLocations()) {
	    LocationSnapshot stored = new LocationSnapshot();
	    stored.setName(location.getFullPath());
	    stored.setState(location.getState());
	    snapshot.getLocations().add(stored);
	}

	for (Agent agent : world.getAgents()) {
	    snapshot.getAgents().add(toSnapshot(agent));
	}

	for (Conversation conversation : world.getAllConversations()) {
	    ConversationSnapshot stored = new ConversationSnapshot();
	    stored.setParticipants(new ArrayList<>(conversation.getParticipants()));
	    stored.setTime(conversation.getTime());

	    for (Dialog line : conversation.getDialog()) {
		DialogSnapshot dialog = new DialogSnapshot();
		dialog.setName(line.getName());
		dialog.setMessage(line.getMessage());
		stored.getDialog().add(dialog);
	    }

	    snapshot.getConversations().add(stored);
	}

	world.getRelationships().asMap().forEach((pair, relationship) -> {
	    RelationshipSnapshot stored = new RelationshipSnapshot();
	    stored.setFirst(pair.first());
	    stored.setSecond(pair.second());
	    stored.setFamiliarity(relationship.familiarity());
	    stored.setAffinity(relationship.affinity());
	    stored.setLastSpokeAt(relationship.lastSpokeAt());
	    snapshot.getRelationships().add(stored);
	});

	return snapshot;
    }

    private static AgentSnapshot toSnapshot(Agent agent) {
	AgentSnapshot stored = new AgentSnapshot();

	stored.setName(agent.getFullName());
	stored.setTraits(agent.getTraits());
	stored.setLocation(agent.getLocation() == null ? null : agent.getLocation().getFullPath());
	stored.setActivity(agent.getCurrentActivity());
	stored.setLastActivity(agent.getLastActivity());
	stored.setEmoji(agent.getEmoji());
	stored.setLastReflectedAt(agent.getMemoryStream().getLastReflectedAt());

	for (Memory memory : agent.getMemoryStream().getMemories()) {
	    stored.getMemories().add(toSnapshot(memory));
	}

	return stored;
    }

    private static MemorySnapshot toSnapshot(Memory memory) {
	MemorySnapshot stored = new MemorySnapshot();

	stored.setDescription(memory.getDescription());
	stored.setImportance((int) memory.getImportance());

	if (memory instanceof Plan plan) {
	    stored.setType("Plan");
	    stored.setTime(plan.getTime());
	    stored.setPlanType(plan.getType() == null ? null : plan.getType().name());
	    stored.setPlanLocation(plan.getLocation());
	    stored.setPlanAddressed(plan.isAddressed());
	    stored.setPlanCreatedAt(plan.getCreatedAt());
	} else if (memory instanceof Observation observation) {
	    stored.setType("Observation");
	    stored.setTime(observation.getTime());
	    stored.setDialog(observation.isDialog());
	    stored.setReactable(observation.isReactable());
	} else if (memory instanceof Concern concern) {
	    stored.setType("Concern");
	    stored.setTime(concern.getTime());
	    stored.setExpiresAt(concern.getExpiresAt());
	    stored.setSource(concern.getSource().name());
	    stored.setValence(concern.getValence().name());
	    stored.setDemand(concern.getDemand().name());
	    stored.setPrivacy(concern.getPrivacy().name());
	} else if (memory instanceof Reflection) {
	    stored.setType("Reflection");
	} else {
	    stored.setType("Characteristic");
	}

	return stored;
    }

    /**
     * Rebuilds {@code world} from a snapshot. The world is expected to be
     * empty; locations are restored first so agents can be placed in them.
     */
    public static void restore(World world, WorldSnapshot snapshot) {
	if (snapshot.getSimulationTime() != null) {
	    SimulationTime.setSimulationTime(snapshot.getSimulationTime());
	}

	SimulationTime.setStep(Duration.ofMinutes(Math.max(1, snapshot.getStepMinutes())));

	for (LocationSnapshot stored : snapshot.getLocations()) {
	    if (stored.getName() == null) {
		continue;
	    }

	    Location location = new Location(stored.getName());
	    location.setState(stored.getState());
	    world.create(location);
	}

	for (AgentSnapshot stored : snapshot.getAgents()) {
	    restoreAgent(world, stored);
	}

	for (ConversationSnapshot stored : snapshot.getConversations()) {
	    List<Dialog> dialog = new ArrayList<>();

	    for (DialogSnapshot line : stored.getDialog()) {
		dialog.add(new Dialog(line.getName(), line.getMessage()));
	    }

	    if (dialog.isEmpty() || stored.getParticipants().size() < 2) {
		continue;
	    }

	    world.create(new Conversation(stored.getParticipants(), dialog, stored.getTime()));
	}

	RelationshipGraph graph = world.getRelationships();

	for (RelationshipSnapshot stored : snapshot.getRelationships()) {
	    if (stored.getFirst() == null || stored.getSecond() == null) {
		continue;
	    }

	    graph
		.put(stored.getFirst(), stored.getSecond(),
			new Relationship(stored.getFamiliarity(), stored.getAffinity(), stored.getLastSpokeAt()));
	}
    }

    private static void restoreAgent(World world, AgentSnapshot stored) {
	Location location = world.getLocation(stored.getLocation()).orElse(null);

	if (location == null) {
	    // A location the agent was standing in no longer exists. Dropping
	    // the agent would lose their whole history, so place them in any
	    // location that does exist rather than failing the load.
	    location = world.getLocations().stream().findFirst().orElse(null);

	    if (location == null) {
		LOG.warn("Cannot restore " + stored.getName() + ": the saved world has no locations");
		return;
	    }

	    LOG.warn("Location '" + stored.getLocation() + "' is gone, placing " + stored.getName() + " at "
		    + location.getFullPath());
	}

	// Seeded with lastActivity so that setting the current activity below
	// shifts it into place - ActionHistory only tracks the previous value
	// through setActivity.
	Agent agent = new Agent(stored.getName(), List.of(), stored.getLastActivity(), location);

	if (stored.getActivity() != null) {
	    agent.setCurrentActivity(stored.getActivity());
	}

	agent.setTraits(stored.getTraits());
	agent.setCurrentEmoji(stored.getEmoji());
	agent.getMemoryStream().setLastReflectedAt(stored.getLastReflectedAt());

	for (MemorySnapshot memory : stored.getMemories()) {
	    Memory restored = restoreMemory(memory);

	    if (restored != null) {
		restored.setImportance(memory.getImportance());
		agent.getMemoryStream().add(restored);
	    }
	}

	world.create(agent);
    }

    private static <T extends Enum<T>> T enumOr(String value, Class<T> type, T fallback) {
	try {
	    return Enum.valueOf(type, value == null ? "" : value.trim().toUpperCase());
	} catch (IllegalArgumentException e) {
	    return fallback;
	}
    }

    private static Memory restoreMemory(MemorySnapshot stored) {
	String description = stored.getDescription() == null ? "" : stored.getDescription();

	if (stored.getType() == null) {
	    return null;
	}

	// Plans and observations carry a final timestamp that everything
	// temporal reads without a null check, so a missing one is filled in
	// rather than allowed to become a crash on the next tick.
	LocalDateTime time = stored.getTime() == null ? SimulationTime.now() : stored.getTime();

	switch (stored.getType()) {
	case "Plan":
	    PlanType type = PlanType.LONG_TERM;

	    if (stored.getPlanType() != null) {
		try {
		    type = PlanType.valueOf(stored.getPlanType());
		} catch (IllegalArgumentException e) {
		    LOG.warn("Unknown plan type '" + stored.getPlanType() + "', treating as long term");
		}
	    }

	    Plan plan = new Plan(description, time, type);
	    plan.setLocation(stored.getPlanLocation());
	    plan.setAddressed(stored.isPlanAddressed());

	    if (stored.getPlanCreatedAt() != null) {
		plan.setCreatedAt(stored.getPlanCreatedAt());
	    }

	    if (stored.getPlanCreatedAt() != null) {
		plan.setCreatedAt(stored.getPlanCreatedAt());
	    }

	    return plan;
	case "Observation":
	    Observation observation = new Observation(description, time, stored.getImportance());
	    observation.setDialog(stored.isDialog());
	    observation.setReactable(stored.isReactable());
	    return observation;
	case "Concern":
	    java.time.Duration lifetime = stored.getExpiresAt() == null ? java.time.Duration.ofHours(12)
		    : java.time.Duration.between(time, stored.getExpiresAt());

	    return new Concern(description, time, lifetime.isNegative() ? java.time.Duration.ZERO : lifetime,
		    enumOr(stored.getSource(), Concern.Source.class, Concern.Source.CHANCE),
		    enumOr(stored.getValence(), Concern.Valence.class, Concern.Valence.AMBIGUOUS),
		    enumOr(stored.getDemand(), Concern.Demand.class, Concern.Demand.NOTHING),
		    enumOr(stored.getPrivacy(), Concern.Privacy.class, Concern.Privacy.PRIVATE));
	case "Reflection":
	    return new Reflection(description);
	case "Characteristic":
	    return new Characteristic(description);
	default:
	    // Written by a newer version. Skipping one memory is better than
	    // refusing to load the entire town.
	    LOG.warn("Skipping memory of unknown type '" + stored.getType() + "'");
	    return null;
	}
    }
}
