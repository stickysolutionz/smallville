package io.github.nickm980.smallville.config.prompts;

public class ReactionPrompts {

    private String reaction;
    private String conversation;
    private String groupConversation;
    private String conversationTone;
    private String say;

    public String getConversationTone() {
	return conversationTone;
    }

    public void setConversationTone(String conversationTone) {
	this.conversationTone = conversationTone;
    }

    public String getSay() {
	return say;
    }

    public void setSay(String say) {
	this.say = say;
    }

    public String getReaction() {
	return reaction;
    }

    public void setReaction(String reaction) {
	this.reaction = reaction;
    }

    public String getConversation() {
	return conversation;
    }

    public void setConversation(String conversation) {
	this.conversation = conversation;
    }

    public String getGroupConversation() {
	return groupConversation;
    }

    public void setGroupConversation(String groupConversation) {
	this.groupConversation = groupConversation;
    }
}
