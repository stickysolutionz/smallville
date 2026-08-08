package io.github.nickm980.smallville.entities;

public class Dialog {

    private final String name;
    private final String message;

    public Dialog(String name, String message) {
	super();
	this.name = name;
	this.message = message;
    }

    public String getName() {
	return name;
    }

    public String getMessage() {
	return message;
    }

    /**
     * Renders this line the way {@code listenerFullName} should remember it.
     * <p>
     * Every line of a conversation is copied into every participant's memory
     * stream. Storing the bare message drops the speaker entirely, so the
     * stream fills with unattributed fragments ("No, what happened?") that
     * nobody - not even the person who said them - can make sense of later.
     * Everything downstream reads from those memories: reflection, retrieval,
     * and the generated story.
     *
     * @param listenerFullName full name of the agent whose memory this is
     */
    public String asMemoryFor(String listenerFullName) {
	return isSpokenBy(listenerFullName) ? "I said: " + message : name + " said: " + message;
    }

    /**
     * Whether this line was spoken by the named agent.
     * <p>
     * The speaker label is whatever the model wrote before the colon, so it is
     * often a first name ("Maria") where the agent is registered under a full
     * name ("Maria Lopez"). An exact match alone would attribute every one of
     * an agent's own lines to someone else.
     */
    public boolean isSpokenBy(String agentFullName) {
	if (name == null || agentFullName == null) {
	    return false;
	}

	String speaker = name.trim();
	String agent = agentFullName.trim();

	if (speaker.equalsIgnoreCase(agent)) {
	    return true;
	}

	// Match on a leading name part in either direction, but only on a whole
	// part - "Mari" must not match "Maria Lopez", and "Maria" must not match
	// "Marian Hill".
	return startsWithNamePart(agent, speaker) || startsWithNamePart(speaker, agent);
    }

    private static boolean startsWithNamePart(String longer, String shorter) {
	return longer.length() > shorter.length() && longer.regionMatches(true, 0, shorter, 0, shorter.length())
		&& longer.charAt(shorter.length()) == ' ';
    }
}
