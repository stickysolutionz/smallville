package io.github.nickm980.smallville.llm;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.nickm980.smallville.Settings;
import io.github.nickm980.smallville.config.GeneralConfig;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.events.EventBus;
import io.github.nickm980.smallville.events.llm.PromptReceievedEvent;
import io.github.nickm980.smallville.exceptions.SmallvilleException;
import io.github.nickm980.smallville.prompts.PromptRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatGPT implements LLM {
    private final static Logger LOG = LoggerFactory.getLogger(ChatGPT.class);
    private final static ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One client for the whole process. A new OkHttpClient per request builds
     * a fresh connection pool and dispatcher thread each time and throws away
     * the previous one, so nothing is ever reused - and this is called several
     * times per agent per tick.
     */
    private final static OkHttpClient CLIENT = new OkHttpClient.Builder()
	.connectTimeout(10, TimeUnit.SECONDS)
	.writeTimeout(3, TimeUnit.MINUTES)
	.readTimeout(3, TimeUnit.MINUTES)
	.build();

    private final EventBus events = EventBus.getEventBus();

    /**
     * Cheap calls go to the smaller model when one is configured. Ranking
     * memories, choosing an activity and judging tone are classification, not
     * the creative work the larger model is worth paying for.
     */
    private static String modelFor(PromptRequest prompt, GeneralConfig config) {
	String cheap = config.getCheapModel();

	if (prompt.isCheap() && cheap != null && !cheap.isBlank()) {
	    return cheap;
	}

	return config.getModel();
    }
    
    @Override
    public String sendChat(PromptRequest prompt, double temperature) {
	int maxRetries = SmallvilleConfig.getConfig().getMaxRetries();
	int retryCount = 0;

	while (retryCount < maxRetries) {
	    // Pausing the simulation calls shutdownNow(), which interrupts this
	    // thread mid-request. That is a cancellation, not a failure worth
	    // retrying - the old loop retried anyway and then swallowed the
	    // InterruptedException from its own sleep, so every pause burned
	    // through the full retry budget logging stack traces.
	    if (Thread.currentThread().isInterrupted()) {
		throw new SmallvilleException("Request cancelled");
	    }

	    try {
		return attemptRequest(prompt, temperature);
	    } catch (InterruptedIOException e) {
		Thread.currentThread().interrupt();
		throw new SmallvilleException("Request cancelled");
	    } catch (IOException | SmallvilleException e) {
		retryCount++;
		LOG.error("Request failed. Retrying... (Attempt " + retryCount + ")", e);

		if (retryCount >= maxRetries) {
		    break;
		}

		// Exponential backoff. A flat 2s retry against a rate limit
		// mostly just spends the budget faster.
		try {
		    Thread.sleep(1000L * (1L << retryCount));
		} catch (InterruptedException ex) {
		    Thread.currentThread().interrupt();
		    throw new SmallvilleException("Request cancelled");
		}
	    }
	}

	LOG.error("Failed to get a successful response after " + maxRetries + " attempts.");
	throw new SmallvilleException("Failed to get a successful response.");
    }

    
    private String attemptRequest(PromptRequest prompt, double temperature) throws IOException, SmallvilleException {
	long start = System.currentTimeMillis();

	GeneralConfig config = SmallvilleConfig.getConfig();

	// Built with Jackson rather than spliced into a string template. The
	// template approach silently produced invalid JSON whenever a
	// substitution was missed - the function-calling branch did exactly
	// that, emitting a literal "%functions" - and it has now been dropped
	// since nothing ever called setFunction.
	ObjectNode payload = MAPPER.createObjectNode();
	payload.put("model", modelFor(prompt, config));
	payload.set("messages", MAPPER.valueToTree(List.of(prompt.build())));
	payload.put("temperature", temperature);
	payload.put("max_tokens", 8000);

	if (prompt.isJsonResponse()) {
	    payload.set("response_format", MAPPER.createObjectNode().put("type", "json_object"));
	}

	// Left off entirely unless configured. Reasoning tokens bill as output
	// and almost nothing here benefits from deliberation, but the disable
	// value is not documented on the pages describing the feature, so the
	// safe default is to send nothing and let this be set deliberately.
	if (config.getThinking() != null && !config.getThinking().isBlank()) {
	    payload.set("thinking", MAPPER.createObjectNode().put("type", config.getThinking()));
	}

	String json = MAPPER.writeValueAsString(payload);

	LOG.debug("[Chat Request Original]" + json);
	LOG.debug("[Chat Request]" + prompt.getContent());

	RequestBody body = RequestBody.create(json.getBytes(StandardCharsets.UTF_8));
	Request request = new Request.Builder()
	    .url(config.getApiPath())
	    .addHeader("Content-Type", "application/json")
	    .addHeader("Authorization", "Bearer " + Settings.getApiKey())
	    .post(body)
	    .build();

	String result = "";

	Response response = CLIENT.newCall(request).execute();
	String responseBody = response.body().string();

	JsonNode node = MAPPER.readTree(responseBody);

	if (node.get("choices") == null) {
	    LOG.debug(node.toPrettyString());
	    throw new SmallvilleException(
		    "Invalid api token, rate limit reached, or the LLM is overloaded with requests.");
	}

	result = node.get("choices").get(0).get("message").get("content").asText();

	UsageTracker.record(prompt.getLabel(), TokenUsage.from(node));

	LOG.debug("[Chat Response]" + node.get("choices").toPrettyString());

	long end = System.currentTimeMillis();
	LOG.debug("[Chat] Response took " + String.valueOf(start - end) + "ms");
//	Analytics.addPrompt(prompt.getContent());
//	Analytics.addPrompt(result);
	PromptReceievedEvent promptReceievedEvent = new PromptReceievedEvent(prompt.getContent(), result, start-end);
	events.postEvent(promptReceievedEvent);
	
	return promptReceievedEvent.getResult();
    }
}