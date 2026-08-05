package io.github.nickm980.smallville.memory;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Something that happened to an agent from outside the town, and is still
 * weighing on them.
 * <p>
 * Nothing in this simulation is ever at stake. Agents move around, talk, and
 * form opinions, but nobody wants anything they might not get, so nothing can
 * turn. Modelling that properly would mean an economy - money, hunger, work -
 * and a whole off-screen world for it to sit in.
 * <p>
 * This is the cheap version, and it is closer to how life actually works: most
 * of what changes your day arrives from outside it. A parent calls. A letter
 * turns up. You find a wallet. Nothing is simulated - a fact simply lands on
 * somebody, and what they do about it comes from who they are.
 * <p>
 * A concern is a memory like any other, so it is retrieved, remembered and
 * narrated by machinery that already exists. What makes it different is that it
 * stays live for a while, and the planning prompts are told about it while it
 * does.
 */
public class Concern extends Memory implements TemporalMemory {

    /**
     * Who it came from. Determines how hard it is to shrug off - family is
     * heaviest, an institution cannot be argued with, chance asks nothing.
     */
    public enum Source {
	FAMILY, FRIEND, INSTITUTION, CHANCE
    }

    /**
     * AMBIGUOUS is the most useful of the three: "your sister called twice and
     * left no voicemail" is all pressure and no information, and it can sit
     * unresolved for a day without going stale.
     */
    public enum Valence {
	GOOD, BAD, AMBIGUOUS
    }

    /** What it asks of them, if anything. */
    public enum Demand {
	NOTHING, SMALL, COSTLY
    }

    /**
     * Whether they would talk about it.
     * <p>
     * The most productive of the four. SHAMEFUL puts an agent in conflict with
     * the system that spreads information: they behave differently, others can
     * see that they are behaving differently, and somebody eventually asks.
     * Concealment is a choice, which is where character shows.
     */
    public enum Privacy {
	OPEN, PRIVATE, SHAMEFUL
    }

    private final LocalDateTime time;
    private final LocalDateTime expiresAt;
    private final Source source;
    private final Valence valence;
    private final Demand demand;
    private final Privacy privacy;

    public Concern(String description, LocalDateTime time, Duration lifetime, Source source, Valence valence,
	    Demand demand, Privacy privacy) {
	super(description);
	this.time = time;
	this.expiresAt = time.plus(lifetime);
	this.source = source;
	this.valence = valence;
	this.demand = demand;
	this.privacy = privacy;
    }

    @Override
    public LocalDateTime getTime() {
	return time;
    }

    public LocalDateTime getExpiresAt() {
	return expiresAt;
    }

    public Source getSource() {
	return source;
    }

    public Valence getValence() {
	return valence;
    }

    public Demand getDemand() {
	return demand;
    }

    public Privacy getPrivacy() {
	return privacy;
    }

    /**
     * Whether this is still hanging over them.
     * <p>
     * Concerns expire rather than resolve. There is no money in this world, so
     * nobody can actually pay a repair bill - but "something was weighing on
     * her and then it passed" is most of life, and asking for more than that
     * would mean simulating the economy this exists to avoid.
     */
    public boolean isActive() {
	return io.github.nickm980.smallville.entities.SimulationTime.now().isBefore(expiresAt);
    }

    /** How it should be described to a prompt, with its weight attached. */
    public String describe() {
	return getDescription() + " (" + valence.name().toLowerCase() + ", "
		+ (demand == Demand.NOTHING ? "asks nothing of them" : "asks something of them") + ", "
		+ switch (privacy) {
		case OPEN -> "they would mention it to anyone";
		case PRIVATE -> "they would only tell someone they trusted";
		case SHAMEFUL -> "they would rather nobody knew";
		} + ")";
    }

    @Override
    double getRecency() {
	return recencyOf(time);
    }
}
