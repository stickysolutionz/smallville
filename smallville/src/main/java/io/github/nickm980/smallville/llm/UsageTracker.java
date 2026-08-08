package io.github.nickm980.smallville.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.nickm980.smallville.config.SmallvilleConfig;

/**
 * Running total of what the simulation has spent, broken down by which prompt
 * spent it.
 * <p>
 * A tick is several sequential calls per agent, so cost scales with agents
 * times tick rate and climbs quickly without anything on screen changing.
 * Knowing which prompt dominates is the difference between guessing at an
 * optimisation and choosing one.
 */
public final class UsageTracker {

    private static final Map<String, AtomicLong> CALLS = new ConcurrentHashMap<>();
    private static final Map<String, TokenUsage> USAGE = new ConcurrentHashMap<>();

    private UsageTracker() {
    }

    public static void record(String label, TokenUsage usage) {
	String key = label == null ? "unlabelled" : label;

	CALLS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
	USAGE.merge(key, usage, TokenUsage::plus);
    }

    public static void reset() {
	CALLS.clear();
	USAGE.clear();
    }

    /**
     * Per-prompt breakdown plus a total, with estimated cost in USD.
     */
    public static Map<String, Object> snapshot() {
	Map<String, Object> byPrompt = new java.util.TreeMap<>();
	TokenUsage total = TokenUsage.NONE;
	long totalCalls = 0;

	for (Map.Entry<String, TokenUsage> entry : USAGE.entrySet()) {
	    long calls = CALLS.getOrDefault(entry.getKey(), new AtomicLong()).get();

	    byPrompt.put(entry.getKey(), describe(calls, entry.getValue()));
	    total = total.plus(entry.getValue());
	    totalCalls += calls;
	}

	return Map.of("byPrompt", byPrompt, "total", describe(totalCalls, total));
    }

    private static Map<String, Object> describe(long calls, TokenUsage usage) {
	return Map
	    .of("calls", calls, "promptTokens", usage.promptTokens(), "completionTokens", usage.completionTokens(),
		    "reasoningTokens", usage.reasoningTokens(), "cacheHitTokens", usage.cacheHitTokens(),
		    "cacheMissTokens", usage.cacheMissTokens(), "estimatedCostUsd",
		    Math.round(estimateCost(usage) * 10000.0) / 10000.0);
    }

    /**
     * Rough running cost, from the per-million-token prices in config.yaml.
     * <p>
     * Approximate on purpose: it ignores DeepSeek's peak-hour surcharge, which
     * doubles prices during two windows each day. It is meant for comparing
     * prompts against each other, not for reconciling a bill.
     */
    private static double estimateCost(TokenUsage usage) {
	var config = SmallvilleConfig.getConfig();

	// Older responses, and any model that does not report the cache split,
	// leave both counters at zero - fall back to charging all input at the
	// miss rate rather than reporting zero cost.
	long miss = usage.cacheMissTokens();
	long hit = usage.cacheHitTokens();

	if (miss == 0 && hit == 0) {
	    miss = usage.promptTokens();
	}

	return (miss / 1_000_000.0 * config.getInputPricePerMillion())
		+ (hit / 1_000_000.0 * config.getCachedInputPricePerMillion())
		+ (usage.completionTokens() / 1_000_000.0 * config.getOutputPricePerMillion());
    }
}
