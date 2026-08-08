package io.github.nickm980.smallville.memory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import io.github.nickm980.smallville.config.GeneralConfig;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.math.SmallvilleMath;

public abstract class Memory implements Comparable<Memory> {

    private String description;
    private int weight;

    public Memory(String description) {
	this.description = withoutListMarker(description);
	this.weight = 0;
    }

    /**
     * Drops a list marker the model put in front of the text.
     * <p>
     * This previously deleted every hyphen anywhere in the description, which
     * quietly rewrote "well-known" as "wellknown", "half-finished" as
     * "halffinished" and "3:00-4:00" as "3:004:00" in every memory the agent
     * ever formed. The intent was only ever to strip the leading "- " that
     * models put in front of list items.
     */
    private static String withoutListMarker(String description) {
	if (description == null) {
	    return "";
	}

	return description.trim().replaceFirst("^(?:[-*•]|\\d+[.)])\\s+", "").trim();
    }

    /**
     * The largest weight {@code getWeights()} is asked to assign. Used to bring
     * importance onto the same 0-1 scale as the other two components.
     */
    public static final int MAX_IMPORTANCE = 10;

    /**
     * Score this memory against a query, as the weighted sum of how recent,
     * how important, and how relevant it is.
     * <p>
     * Each component is independently bounded to 0-1 before weighting. That
     * was previously not true of any of them: importance was the raw 0-10
     * weight, so it swamped the other two outright; relevance could go
     * unboundedly negative; and recency produced values so large that the
     * whole sum came back NaN or infinite, which tripped a fallback that
     * quietly discarded everything except relevance. In practice retrieval had
     * therefore been running on relevance alone.
     *
     * @return a value between 0 and the sum of the configured weights, where
     *         higher is a stronger match
     */
    public double getScore(String query) {
	GeneralConfig config = SmallvilleConfig.getConfig();

	double score = config.getRecencyWeight() * getRecency() + config.getImportanceWeight() * getImportanceScore()
		+ config.getRelevanceWeight() * getRelevance(query);

	return Double.isNaN(score) ? 0 : score;
    }

    /**
     * How recent this memory is, from 0 (long past) to 1 (right now).
     */
    abstract double getRecency();

    /**
     * Exponential decay over elapsed simulated time, halving every
     * {@code recencyHalfLifeHours}.
     * <p>
     * Absolute elapsed time is used so a plan two hours ahead scores like an
     * observation two hours behind, rather than producing a value above 1.
     */
    protected static double recencyOf(LocalDateTime time) {
	double hours = Math.abs(ChronoUnit.MINUTES.between(time, SimulationTime.now())) / 60.0;
	double halfLife = SmallvilleConfig.getConfig().getRecencyHalfLifeHours();

	if (halfLife <= 0) {
	    return 1;
	}

	return Math.pow(0.5, hours / halfLife);
    }

    /**
     * The raw 0-10 weight assigned by the ranking prompt.
     */
    public double getImportance() {
	return weight;
    }

    /** Importance rescaled to 0-1 so it can be combined with the others. */
    double getImportanceScore() {
	return clamp(weight / (double) MAX_IMPORTANCE);
    }

    double getRelevance(String query) {
	return clamp(SmallvilleMath.calculateSentenceSimilarity(query, description));
    }

    private static double clamp(double value) {
	return Math.max(0, Math.min(1, value));
    }

    public String getDescription() {
	return description;
    }

    public void setDescription(String description) {
	this.description = description;
    }

    public void setImportance(int weight) {
	this.weight = weight;
    }

    @Override
    public int compareTo(Memory o) {
	double score = getScore(description);
	double other = getScore(o.getDescription());
	if (score > other) {
	    return 1;
	}
	if (score < other) {
	    return -1;
	}
	return 0;
    }
}
