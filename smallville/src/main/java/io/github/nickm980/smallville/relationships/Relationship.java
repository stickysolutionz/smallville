package io.github.nickm980.smallville.relationships;

import java.time.LocalDateTime;

/**
 * What one pair of agents is to each other, as far as the simulation knows.
 *
 * @param familiarity how many conversations they have had
 * @param affinity    -1 (hostile) to 1 (warm), 0 for neutral or unknown
 * @param lastSpokeAt simulated time they last talked, or null if never
 */
public record Relationship(int familiarity, double affinity, LocalDateTime lastSpokeAt) {

    public static final Relationship STRANGERS = new Relationship(0, 0, null);

    public boolean haveMet() {
	return familiarity > 0;
    }

    /**
     * A short phrase for the conversation prompt, so tone reflects history
     * rather than every exchange starting from nothing.
     */
    public String describe(String a, String b) {
	if (!haveMet()) {
	    return a + " and " + b + " have not spoken before.";
	}

	String closeness = familiarity >= 8 ? "know each other well"
		: familiarity >= 3 ? "have talked a number of times" : "have only spoken once or twice";

	String warmth = affinity > 0.35 ? " and get on well"
		: affinity < -0.35 ? ", and there is friction between them" : "";

	return a + " and " + b + " " + closeness + warmth + ".";
    }

    Relationship after(double affinityShift, LocalDateTime when) {
	double blended = Math.max(-1, Math.min(1, affinity + affinityShift));

	return new Relationship(familiarity + 1, blended, when);
    }
}
