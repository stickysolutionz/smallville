package io.github.nickm980.smallville.api.v1.dto;

import java.util.List;

public class ConversationGroupResponse {

    private List<String> participants;
    private String time;
    private List<ConversationResponse> dialog;

    public List<String> getParticipants() {
	return participants;
    }

    public void setParticipants(List<String> participants) {
	this.participants = participants;
    }

    public String getTime() {
	return time;
    }

    public void setTime(String time) {
	this.time = time;
    }

    public List<ConversationResponse> getDialog() {
	return dialog;
    }

    public void setDialog(List<ConversationResponse> dialog) {
	this.dialog = dialog;
    }
}
