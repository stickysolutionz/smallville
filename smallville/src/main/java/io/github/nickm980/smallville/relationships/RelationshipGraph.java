package io.github.nickm980.smallville.relationships;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who knows whom, and how well.
 * <p>
 * Without this, conversation triggering has nothing to go on but a timer: any
 * two co-located agents talk on a fixed cooldown forever, regardless of whether
 * they have anything to say to each other. The graph is what lets the
 * simulation decide rather than the clock.
 * <p>
 * Keyed on the unordered pair, so a relationship is one fact about two people
 * rather than two facts that can drift apart.
 */
public class RelationshipGraph {

    /**
     * A canonically ordered pair. A record rather than a joined string,
     * because agent names contain spaces and any delimiter chosen for a string
     * key is a name that cannot be used.
     */
    public record Pair(String first, String second) {
	public static Pair of(String a, String b) {
	    return a.compareTo(b) <= 0 ? new Pair(a, b) : new Pair(b, a);
	}

	public boolean involves(String name) {
	    return first.equals(name) || second.equals(name);
	}
    }

    private final Map<Pair, Relationship> relationships = new ConcurrentHashMap<>();

    public Relationship get(String a, String b) {
	return relationships.getOrDefault(Pair.of(a, b), Relationship.STRANGERS);
    }

    /**
     * Records that everyone listed took part in one conversation together,
     * nudging every pair among them.
     *
     * @param affinityShift how much warmer (or cooler) the exchange left them
     */
    public void recordConversation(List<String> participants, double affinityShift, LocalDateTime when) {
	for (int i = 0; i < participants.size(); i++) {
	    for (int j = i + 1; j < participants.size(); j++) {
		relationships.compute(Pair.of(participants.get(i), participants.get(j)),
			(ignored, existing) -> (existing == null ? Relationship.STRANGERS : existing)
			    .after(affinityShift, when));
	    }
	}
    }

    /**
     * Sets a relationship outright, for restoring a saved world.
     */
    public void put(String a, String b, Relationship relationship) {
	relationships.put(Pair.of(a, b), relationship);
    }

    public void removeAgent(String name) {
	relationships.keySet().removeIf(pair -> pair.involves(name));
    }

    public void clear() {
	relationships.clear();
    }

    public Map<Pair, Relationship> asMap() {
	return relationships;
    }
}
