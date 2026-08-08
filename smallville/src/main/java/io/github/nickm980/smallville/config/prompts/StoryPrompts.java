package io.github.nickm980.smallville.config.prompts;

public class StoryPrompts {

    private String first;
    private String continuation;
    private String compact;
    private String generateCharacter;

    public String getFirst() {
	return first;
    }

    public void setFirst(String first) {
	this.first = first;
    }

    public String getContinuation() {
	return continuation;
    }

    public void setContinuation(String continuation) {
	this.continuation = continuation;
    }

    public String getCompact() {
	return compact;
    }

    public void setCompact(String compact) {
	this.compact = compact;
    }

    public String getGenerateCharacter() {
	return generateCharacter;
    }

    public void setGenerateCharacter(String generateCharacter) {
	this.generateCharacter = generateCharacter;
    }
}
