package io.github.nickm980.smallville.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Conversation {

    private List<Dialog> messages;
    private List<String> participants;
    private LocalDateTime time;

    public Conversation(List<String> participants, List<Dialog> messages) {
	this(participants, messages, SimulationTime.now());
    }

    public Conversation(List<String> participants, List<Dialog> messages, LocalDateTime time) {
	this.messages = new ArrayList<Dialog>();
	this.participants = participants;
	this.messages = messages;
	this.time = time;
    }

    public LocalDateTime getTime() {
	return time;
    }

    public List<Dialog> getDialog() {
	return messages;
    }

    public List<String> getParticipants() {
	return participants;
    }

    public int size() {
	return messages.size();
    }
}
