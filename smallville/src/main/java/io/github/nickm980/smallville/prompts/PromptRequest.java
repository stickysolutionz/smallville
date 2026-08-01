package io.github.nickm980.smallville.prompts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PromptRequest {
    private String content;
    private String assistant;
    private boolean jsonResponse;

    public PromptRequest(String content) {
	this.content = content;
    }

    abstract String getRole();

    public void setAssistant(String assistant) {
	this.assistant = assistant;
    }

    /**
     * Whether the model should be constrained to return a JSON object.
     * <p>
     * Only set for prompts that actually ask for JSON - the API rejects the
     * request if the word "json" never appears in the prompt itself.
     */
    public boolean isJsonResponse() {
	return jsonResponse;
    }

    public PromptRequest asJsonResponse() {
	this.jsonResponse = true;
	return this;
    }

    public String getContent() {
	return content;
    }

    public Map<String, String> build() {
	Map<String, String> map = new HashMap<>();
	map.put("role", getRole());
	map.put("content", content);

	return map;
    }

    public static class User extends PromptRequest {
	public User(String content) {
	    super(content);
	}

	@Override
	String getRole() {
	    return "user";
	}
    }

    public static class System extends PromptRequest {
	public System(String content) {
	    super(content);
	}

	@Override
	String getRole() {
	    return "system";
	}
    }
}
