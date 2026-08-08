package io.github.nickm980.smallville.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What one API call cost, as reported in the response's usage block.
 * <p>
 * Reasoning tokens are billed as output. On a thinking model they can dwarf
 * the visible answer - a six line daily plan has been observed carrying
 * several hundred tokens of deliberation - and nothing in the system made that
 * visible before.
 *
 * @param cacheHitTokens  input tokens served from DeepSeek's context cache, at
 *                        a fraction of the normal input price
 * @param cacheMissTokens input tokens charged at full price
 */
public record TokenUsage(long promptTokens, long completionTokens, long reasoningTokens, long cacheHitTokens,
	long cacheMissTokens) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0, 0);

    public static TokenUsage from(JsonNode response) {
	JsonNode usage = response == null ? null : response.get("usage");

	if (usage == null) {
	    return NONE;
	}

	// The completion details block is where reasoning tokens are reported
	// when the model is thinking; absent otherwise.
	long reasoning = usage.path("completion_tokens_details").path("reasoning_tokens").asLong(0);

	return new TokenUsage(usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0),
		reasoning, usage.path("prompt_cache_hit_tokens").asLong(0),
		usage.path("prompt_cache_miss_tokens").asLong(0));
    }

    public TokenUsage plus(TokenUsage other) {
	return new TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens,
		reasoningTokens + other.reasoningTokens, cacheHitTokens + other.cacheHitTokens,
		cacheMissTokens + other.cacheMissTokens);
    }
}
