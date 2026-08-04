package io.github.nickm980.smallville.persistence;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The whole simulation, flattened for storage.
 * <p>
 * Deliberately a set of plain DTOs rather than Jackson annotations on the
 * domain classes. Memory and its subclasses have final fields and no no-arg
 * constructors, and Agent owns a MemoryStream and a Location by reference -
 * making those directly serialisable would mean reshaping the domain around
 * the persistence format. Mapping explicitly costs more lines and keeps the
 * file format something we choose rather than something the class layout
 * happens to imply.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldSnapshot {

    private LocalDateTime simulationTime;
    private long stepMinutes = 1;
    private List<LocationSnapshot> locations = new ArrayList<>();
    private List<AgentSnapshot> agents = new ArrayList<>();
    private List<ConversationSnapshot> conversations = new ArrayList<>();
    private List<RelationshipSnapshot> relationships = new ArrayList<>();

    public LocalDateTime getSimulationTime() {
	return simulationTime;
    }

    public void setSimulationTime(LocalDateTime simulationTime) {
	this.simulationTime = simulationTime;
    }

    public long getStepMinutes() {
	return stepMinutes;
    }

    public void setStepMinutes(long stepMinutes) {
	this.stepMinutes = stepMinutes;
    }

    public List<LocationSnapshot> getLocations() {
	return locations;
    }

    public void setLocations(List<LocationSnapshot> locations) {
	this.locations = locations == null ? new ArrayList<>() : locations;
    }

    public List<AgentSnapshot> getAgents() {
	return agents;
    }

    public void setAgents(List<AgentSnapshot> agents) {
	this.agents = agents == null ? new ArrayList<>() : agents;
    }

    public List<ConversationSnapshot> getConversations() {
	return conversations;
    }

    public void setConversations(List<ConversationSnapshot> conversations) {
	this.conversations = conversations == null ? new ArrayList<>() : conversations;
    }

    public List<RelationshipSnapshot> getRelationships() {
	return relationships;
    }

    public void setRelationships(List<RelationshipSnapshot> relationships) {
	this.relationships = relationships == null ? new ArrayList<>() : relationships;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationSnapshot {
	private String name;
	private String state;

	public String getName() {
	    return name;
	}

	public void setName(String name) {
	    this.name = name;
	}

	public String getState() {
	    return state;
	}

	public void setState(String state) {
	    this.state = state;
	}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AgentSnapshot {
	private String name;
	private String traits;
	private String location;
	private String activity;
	private String lastActivity;
	private String emoji;
	private LocalDateTime lastReflectedAt;
	private List<MemorySnapshot> memories = new ArrayList<>();

	public String getName() {
	    return name;
	}

	public void setName(String name) {
	    this.name = name;
	}

	public String getTraits() {
	    return traits;
	}

	public void setTraits(String traits) {
	    this.traits = traits;
	}

	public String getLocation() {
	    return location;
	}

	public void setLocation(String location) {
	    this.location = location;
	}

	public String getActivity() {
	    return activity;
	}

	public void setActivity(String activity) {
	    this.activity = activity;
	}

	public String getLastActivity() {
	    return lastActivity;
	}

	public void setLastActivity(String lastActivity) {
	    this.lastActivity = lastActivity;
	}

	public String getEmoji() {
	    return emoji;
	}

	public void setEmoji(String emoji) {
	    this.emoji = emoji;
	}

	public LocalDateTime getLastReflectedAt() {
	    return lastReflectedAt;
	}

	public void setLastReflectedAt(LocalDateTime lastReflectedAt) {
	    this.lastReflectedAt = lastReflectedAt;
	}

	public List<MemorySnapshot> getMemories() {
	    return memories;
	}

	public void setMemories(List<MemorySnapshot> memories) {
	    this.memories = memories == null ? new ArrayList<>() : memories;
	}
    }

    /**
     * One memory of any kind. {@code type} carries the subclass, rather than
     * Jackson polymorphic typing, so the file stays readable and a future
     * unknown type can be skipped instead of failing the whole load.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MemorySnapshot {
	private String type;
	private String description;
	private int importance;
	private LocalDateTime time;
	private String planType;
	private String planLocation;
	private boolean planAddressed;
	private LocalDateTime planCreatedAt;
	private boolean dialog;
	private boolean reactable;

	public String getType() {
	    return type;
	}

	public void setType(String type) {
	    this.type = type;
	}

	public String getDescription() {
	    return description;
	}

	public void setDescription(String description) {
	    this.description = description;
	}

	public int getImportance() {
	    return importance;
	}

	public void setImportance(int importance) {
	    this.importance = importance;
	}

	public LocalDateTime getTime() {
	    return time;
	}

	public void setTime(LocalDateTime time) {
	    this.time = time;
	}

	public String getPlanType() {
	    return planType;
	}

	public void setPlanType(String planType) {
	    this.planType = planType;
	}

	public String getPlanLocation() {
	    return planLocation;
	}

	public void setPlanLocation(String planLocation) {
	    this.planLocation = planLocation;
	}

	public LocalDateTime getPlanCreatedAt() {
	    return planCreatedAt;
	}

	public void setPlanCreatedAt(LocalDateTime planCreatedAt) {
	    this.planCreatedAt = planCreatedAt;
	}

	public boolean isPlanAddressed() {
	    return planAddressed;
	}

	public void setPlanAddressed(boolean planAddressed) {
	    this.planAddressed = planAddressed;
	}

	public boolean isDialog() {
	    return dialog;
	}

	public void setDialog(boolean dialog) {
	    this.dialog = dialog;
	}

	public boolean isReactable() {
	    return reactable;
	}

	public void setReactable(boolean reactable) {
	    this.reactable = reactable;
	}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConversationSnapshot {
	private List<String> participants = new ArrayList<>();
	private List<DialogSnapshot> dialog = new ArrayList<>();
	private LocalDateTime time;

	public List<String> getParticipants() {
	    return participants;
	}

	public void setParticipants(List<String> participants) {
	    this.participants = participants == null ? new ArrayList<>() : participants;
	}

	public List<DialogSnapshot> getDialog() {
	    return dialog;
	}

	public void setDialog(List<DialogSnapshot> dialog) {
	    this.dialog = dialog == null ? new ArrayList<>() : dialog;
	}

	public LocalDateTime getTime() {
	    return time;
	}

	public void setTime(LocalDateTime time) {
	    this.time = time;
	}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DialogSnapshot {
	private String name;
	private String message;

	public String getName() {
	    return name;
	}

	public void setName(String name) {
	    this.name = name;
	}

	public String getMessage() {
	    return message;
	}

	public void setMessage(String message) {
	    this.message = message;
	}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelationshipSnapshot {
	private String first;
	private String second;
	private int familiarity;
	private double affinity;
	private LocalDateTime lastSpokeAt;

	public String getFirst() {
	    return first;
	}

	public void setFirst(String first) {
	    this.first = first;
	}

	public String getSecond() {
	    return second;
	}

	public void setSecond(String second) {
	    this.second = second;
	}

	public int getFamiliarity() {
	    return familiarity;
	}

	public void setFamiliarity(int familiarity) {
	    this.familiarity = familiarity;
	}

	public double getAffinity() {
	    return affinity;
	}

	public void setAffinity(double affinity) {
	    this.affinity = affinity;
	}

	public LocalDateTime getLastSpokeAt() {
	    return lastSpokeAt;
	}

	public void setLastSpokeAt(LocalDateTime lastSpokeAt) {
	    this.lastSpokeAt = lastSpokeAt;
	}
    }
}
