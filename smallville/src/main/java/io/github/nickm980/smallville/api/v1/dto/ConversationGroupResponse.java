package io.github.nickm980.smallville.api.v1.dto;

import java.util.List;

public class ConversationGroupResponse {

    private String talker;
    private String talkee;
    private String time;
    private List<ConversationResponse> dialog;

    public String getTalker() {
	return talker;
    }

    public void setTalker(String talker) {
	this.talker = talker;
    }

    public String getTalkee() {
	return talkee;
    }

    public void setTalkee(String talkee) {
	this.talkee = talkee;
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
