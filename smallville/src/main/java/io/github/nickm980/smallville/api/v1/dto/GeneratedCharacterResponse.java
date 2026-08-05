package io.github.nickm980.smallville.api.v1.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * A generated character, in the six parts a person needs to be playable here.
 * <p>
 * Traits used to come back as a loose list, and the loose lists people write by
 * hand have a consistent shape: adjectives. "Calm and collected", "above
 * average intelligence", "sincere and honest" - none of which produce anything
 * at nine in the morning. The parts below are the ones that actually drive
 * this simulation.
 */
public class GeneratedCharacterResponse {

    private String name;
    /**
     * A job or routine that puts them somewhere at a predictable time. The most
     * load-bearing of the six: where an agent means to be is what puts them in
     * a room with somebody, and that is where everything else comes from.
     */
    private String anchor;
    /** Something they are chasing or avoiding that a day will not settle. */
    private String want;
    /** Something they visibly do, rather than something they are. */
    private String behavior;
    /** Something that costs them, and that they do anyway. */
    private String flaw;
    /** Somebody off-screen. Gives the events system something to reach for. */
    private String tie;
    /**
     * Something small and physical another agent could witness. Traits are
     * private now, so anything unobservable can never be found out.
     */
    private String tell;

    public String getName() {
	return name;
    }

    public void setName(String name) {
	this.name = name;
    }

    public String getAnchor() {
	return anchor;
    }

    public void setAnchor(String anchor) {
	this.anchor = anchor;
    }

    public String getWant() {
	return want;
    }

    public void setWant(String want) {
	this.want = want;
    }

    public String getBehavior() {
	return behavior;
    }

    public void setBehavior(String behavior) {
	this.behavior = behavior;
    }

    public String getFlaw() {
	return flaw;
    }

    public void setFlaw(String flaw) {
	this.flaw = flaw;
    }

    public String getTie() {
	return tie;
    }

    public void setTie(String tie) {
	this.tie = tie;
    }

    public String getTell() {
	return tell;
    }

    public void setTell(String tell) {
	this.tell = tell;
    }

    /**
     * The six as a plain list, which is how they are stored and how every
     * prompt reads them. The sections shape what gets written; they are not
     * worth carrying into the memory stream, where a label like "Tell:" would
     * only read oddly inside a conversation prompt.
     */
    public List<String> getMemories() {
	List<String> memories = new ArrayList<>();

	for (String part : new String[] { anchor, want, behavior, flaw, tie, tell }) {
	    if (part != null && !part.isBlank()) {
		memories.add(part.trim());
	    }
	}

	return memories;
    }
}
