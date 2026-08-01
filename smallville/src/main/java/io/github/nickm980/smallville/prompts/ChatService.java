package io.github.nickm980.smallville.prompts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.nickm980.smallville.Util;
import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Dialog;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.Reflection;
import io.github.nickm980.smallville.nlp.LocalNLP;
import io.github.nickm980.smallville.prompts.dto.CurrentActivity;
import io.github.nickm980.smallville.prompts.dto.ObjectChangeResponse;
import io.github.nickm980.smallville.prompts.dto.Reaction;
import io.github.nickm980.smallville.update.UpdateService;

public class ChatService implements Prompts {

    private final LLM chat;
    private final static Logger LOG = LoggerFactory.getLogger(UpdateService.class);
    private final World world;

    public ChatService(World world, LLM chat) {
	this.chat = chat;
	this.world = world;
    }

    @Override
    public int[] getWeights(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getMisc().getRankMemories())
	    .build();

	String response = chat.sendChat(prompt, .1);
	response = response.replace(",]", "]");

	ObjectMapper objectMapper = new ObjectMapper();
	int[] result = new int[0];

	if (!response.contains("[")) {
	    result = new int[1];
	    result[0] = Integer.parseInt(response);
	    return result;
	}

	try {
	    result = objectMapper.readValue(response, int[].class);
	} catch (JsonProcessingException e) {
	    LOG.error("Failed to parse json for memory ranking. Continuing anyways...");
	}

	return result;
    }

    @Override
    public String ask(Agent agent, String question) {
	PromptRequest prompt = new PromptBuilder()
	    .withObservation(question.replace("?", ""))
	    .withQuestion(question)
	    .withLocations(world.getLocations())
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getAskQuestion())
	    .build();

	return chat.sendChat(prompt, .5);
    }

    @Override
    public List<Plan> getPlans(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withLocations(world.getLocations())
	    .withObservation(agent.getMemoryStream().getLastObservation().getDescription())
	    .withAgent(agent)
	    .withWorld(world)
	    .setPrompt(SmallvilleConfig.getPrompts().getPlans().getLongTerm())
	    .build();

	String response = chat.sendChat(prompt.asJsonResponse(), .6);
	List<Plan> plans = parsePlansJson(response);

	if (plans.isEmpty()) {
	    LOG.warn("[Plans] Could not read the daily plan as JSON, falling back to line parsing");
	    plans = parsePlans(response);
	}

	if (plans.isEmpty()) {
	    // Last resort, and the old behaviour: keep the whole answer as one
	    // memory rather than losing the day's plan entirely.
	    return List.of(new Plan(response.replace("\n", " "), LocalDateTime.now()));
	}

	return plans;
    }

    @Override
    public List<Plan> getShortTermPlans(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withLocations(world.getLocations())
	    .withObservation(agent.getMemoryStream().getLastObservation().getDescription())
	    .withAgent(agent)
	    .withWorld(world)
	    .setPrompt(SmallvilleConfig.getPrompts().getPlans().getShortTerm())
	    .build();

	String response = chat.sendChat(prompt.asJsonResponse(), .7);
	List<Plan> plans = parsePlansJson(response);

	if (plans.isEmpty()) {
	    // The model ignored the format request or returned a shape we can't
	    // use. Falling back keeps the tick productive instead of leaving the
	    // agent with no plans at all.
	    LOG.warn("[Plans] Could not read plans as JSON, falling back to line parsing");
	    plans = parsePlans(response);
	}

	return plans;
    }

    /**
     * Reads the {@code {"plans": [{"time","location","activity"}, ...]}} shape
     * the short-term prompt asks for.
     * <p>
     * Returns an empty list rather than throwing, so the caller can fall back
     * to line parsing.
     */
    private List<Plan> parsePlansJson(String response) {
	List<Plan> plans = new ArrayList<>();

	try {
	    JsonNode entries = new ObjectMapper().readTree(Util.stripCodeFence(response)).path("plans");

	    if (!entries.isArray()) {
		return plans;
	    }

	    for (JsonNode entry : entries) {
		String time = entry.path("time").asText("").trim();
		String location = entry.path("location").asText("").trim();
		String activity = entry.path("activity").asText("").trim();
		LocalDateTime start = parseTime(time);

		if (start == null || activity.isEmpty()) {
		    LOG.warn("[Plans] Skipping entry with no usable time or activity: " + entry);
		    continue;
		}

		// Rebuilt into the same one-line shape the rest of the prompts
		// show as examples, so downstream prompts see a consistent
		// format regardless of which parse path produced the plan.
		String description = location.isEmpty() ? time + ", " + activity
			: time + " at " + location + ", " + activity;

		plans.add(new Plan(description, start));
	    }
	} catch (Exception e) {
	    LOG.warn("[Plans] Response was not valid JSON: " + e.getMessage());
	}

	return plans;
    }

    @Override
    public CurrentActivity getCurrentActivity(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .withWorld(world)
	    .withLocations(world.getLocations())
	    .setPrompt(SmallvilleConfig.getPrompts().getPlans().getCurrent())
	    .build();

	String response = chat.sendChat(prompt, .5);

	LocalNLP nlp = new LocalNLP();
	CurrentActivity activity = Util.parseAsClass(response, CurrentActivity.class);
	LOG.info(activity.getActivity() + activity.getLocation());
	activity.setLastActivity(nlp.convertToPastTense(agent.getCurrentActivity()));

	return activity;
    }

    @Override
    public Conversation getConversationIfExists(Agent agent, Agent other, String topic) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .withOther(other)
	    .withObservation(topic)
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getConversation())
	    .build();

	String response = chat.sendChat(prompt, .7);
	String[] lines = response.split("\\r?\\n");

	List<Dialog> dialogs = new ArrayList<>();
	for (String line : lines) {
	    String[] parts = line.split(":\\s+", 2);
	    if (parts.length == 2) { // ignores all lines before the conversation
		dialogs.add(new Dialog(parts[0], parts[1]));
	    }
	}

	Conversation conversation = new Conversation(List.of(agent.getFullName(), other.getFullName()), dialogs);
	return conversation;
    }

    @Override
    public Conversation getGroupConversation(Agent initiator, List<Agent> others, String topic) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(initiator)
	    .withOthers(others)
	    .withObservation(topic)
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getGroupConversation())
	    .build();

	String response = chat.sendChat(prompt, .7);
	String[] lines = response.split("\\r?\\n");

	List<Dialog> dialogs = new ArrayList<>();
	for (String line : lines) {
	    String[] parts = line.split(":\\s+", 2);
	    if (parts.length == 2) { // ignores all lines before the conversation
		dialogs.add(new Dialog(parts[0], parts[1]));
	    }
	}

	List<String> participantNames = new ArrayList<>();
	participantNames.add(initiator.getFullName());
	for (Agent other : others) {
	    participantNames.add(other.getFullName());
	}

	return new Conversation(participantNames, dialogs);
    }

    /**
     * A clock time anywhere in a line: "9:00 am", "2:30PM", "14:00". The
     * meridiem is optional so a model that answers in 24-hour time still
     * parses - format strictness is not what keeps prose out of the plan list,
     * {@link #stripPreamble} is.
     */
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\s*([AaPp])\\.?[Mm]\\.?)?");

    /**
     * A line that opens with its timestamp, allowing for bullets and list
     * numbering ("- 9:00 am ...", "3. 9:00 am ...").
     */
    private static final Pattern ANCHORED_TIME = Pattern
	.compile("^[\\s\\-*•>]*(?:\\d+[.)]\\s+)?\\d{1,2}:\\d{2}");

    @Override
    public List<Plan> parsePlans(String input) {
	List<Plan> plans = new ArrayList<>();

	for (String line : stripPreamble(input.split("\\r?\\n"))) {
	    LocalDateTime start = parseTime(line);

	    if (start == null) {
		continue;
	    }

	    plans.add(new Plan(line.trim(), start));
	}

	return plans;
    }

    /**
     * Drops any conversational preamble written before the first real plan
     * line.
     * <p>
     * Models sometimes answer with "Alright, let me figure out what's going on
     * here. It's 10:30 PM and Maria is at the cafe..." before listing the
     * plan. Treating every line containing a time as a plan stored that
     * sentence verbatim as a diary entry; requiring the time to start the line
     * instead broke the equally common style where the time trails the
     * activity ("feed the animals from 3:00 PM - 4:00 PM").
     * <p>
     * Both are handled by anchoring only to find where the plan begins: if any
     * line opens with a timestamp, everything before the first such line is
     * preamble and is discarded. Otherwise nothing is dropped and every line
     * carrying a time is considered, which is the trailing-timestamp style.
     */
    private static List<String> stripPreamble(String[] lines) {
	List<String> all = List.of(lines);

	for (int i = 0; i < lines.length; i++) {
	    if (ANCHORED_TIME.matcher(lines[i]).find()) {
		return all.subList(i, all.size());
	    }
	}

	return all;
    }

    /**
     * The first clock time in {@code text}, or null if there isn't a usable
     * one. Never throws - an unparseable line is skipped, not fatal.
     */
    private static LocalDateTime parseTime(String text) {
	if (text == null || text.isBlank()) {
	    return null;
	}

	Matcher matcher = TIME.matcher(text);

	if (!matcher.find()) {
	    return null;
	}

	int hour = Integer.parseInt(matcher.group(1));
	int minute = Integer.parseInt(matcher.group(2));
	String meridiem = matcher.group(3);

	if (minute > 59) {
	    return null;
	}

	if (meridiem != null) {
	    if (hour < 1 || hour > 12) {
		return null;
	    }

	    hour = hour % 12;

	    if (meridiem.equalsIgnoreCase("p")) {
		hour += 12;
	    }
	} else if (hour > 23) {
	    return null;
	}

	return LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute));
    }

    @Override
    public ObjectChangeResponse[] getObjectsChangedBy(Agent agent) {
	if (agent.getCurrentActivity().equals(agent.getLastActivity())) {
	    return new ObjectChangeResponse[0];
	}

	PromptRequest tensesPrompt = new PromptBuilder()
	    .withAgent(agent)
	    .withWorld(world)
	    .setPrompt(SmallvilleConfig.getPrompts().getMisc().getCombineSentences())
	    .build(); // might be able to use LocalNLP for this

	String tenses = chat.sendChat(tensesPrompt, .1);

	PromptRequest changedPrompt = new PromptBuilder()
	    .withAgent(agent)
	    .withTense(tenses)
	    .withWorld(world)
	    .withLocations(world.getLocations())
	    .setPrompt(SmallvilleConfig.getPrompts().getWorld().getObjectStates())
	    .build();

	String response = chat.sendChat(changedPrompt, .3);

	String[] lines = response.split("\n");
	ObjectChangeResponse[] objects = new ObjectChangeResponse[lines.length];

	for (int i = 0; i < lines.length; i++) {
	    String line = lines[i];

	    if (line.isBlank()) {
		continue;
	    }

	    String[] parts = line.split(":");

	    if (parts.length < 2) {
		continue;
	    }

	    String item = parts[0].trim();
	    String value = parts[1].trim();

	    LOG.debug("Trying to change " + item + " to " + value);

	    if (item != null && value != null && !value.equalsIgnoreCase("Unchanged")) {
		objects[i] = new ObjectChangeResponse(item, value);
	    }
	}

	if (objects.length == 0) {
	    LOG.warn("No objects were updated");
	}

	return objects;
    }

    @Override
    public Reflection createReflectionFor(Agent agent) {
	Reflection reflection = new Reflection("");
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getReflectionQuestion())
	    .build();

	String query = chat.sendChat(prompt, .1);
	String[] lines = query.split("\n");
	query = query.split("\n")[lines.length - 1].substring(2);

	LOG.debug("[Reflections] Question: " + query);

	Set<Memory> filter = new HashSet<Memory>();
	filter.addAll(agent.getMemoryStream().getRelevantMemories(query.substring(2)));
	List<Memory> memories = new ArrayList<>(filter); // Convert the set back to a list

	LOG.debug(String.join(",", memories.stream().map(m -> m.getDescription()).collect(Collectors.toList())));

	PromptRequest secondPrompt = new PromptBuilder()
	    .withAgent(agent)
	    .withStatements(memories.stream().map(m -> m.getDescription()).collect(Collectors.toList()))
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getReflectionResult())
	    .build();

	String description = chat.sendChat(secondPrompt, .8);

	// retrieve just the insight. remove the because clause and the key
	int index = description.lastIndexOf(":");

	if (index != -1) {
	    description = description.substring(index);
	}

	description = description.replaceAll(":", "").trim();

	reflection.setDescription(description);

	return reflection;
    }

    @Override
    public Reaction shouldUpdatePlans(Agent agent, String observation) {
	PromptRequest prompt = new PromptBuilder()
	    .withObservation(observation)
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getReaction())
	    .build();

	String response = chat.sendChat(prompt, .2);
	Reaction result = Util.parseAsClass(response, Reaction.class);

	LOG.debug("reacting " + result.getAnswer());
	return result;
    }

    public String createTraits(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getCharacteristics())
	    .build();

	return chat.sendChat(prompt, .5);
    }

    @Override
    public Dialog saySomething(Agent agent, String observation) {
	PromptRequest request = new PromptBuilder()
	    .withObservation(observation)
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getSay())
	    .build();
	
	String result = chat.sendChat(request, .5);
	
	return new Dialog(agent.getFullName(), result);
    }
}
