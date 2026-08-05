package io.github.nickm980.smallville.prompts;

import java.time.Duration;
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
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Concern;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
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

	String response = chat.sendChat(prompt.labelled("rankMemories").asCheap(), .1);
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

	return chat.sendChat(prompt.labelled("askQuestion"), .5);
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

	String response = chat.sendChat(prompt.labelled("planDaily").asJsonResponse(), .6);
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
	    .with("outstandingGoals", describeOutstandingGoals(agent))
	    .setPrompt(SmallvilleConfig.getPrompts().getPlans().getShortTerm())
	    .build();

	String response = chat.sendChat(prompt.labelled("planNextHour").asJsonResponse(), .7);
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
		// Hourly plans carry a clock "time" and an "activity"; daily
		// goals carry a "when" time of day and an "intent". Same shape
		// otherwise, so one reader handles both.
		String time = firstNonBlank(entry, "time", "when");
		String location = entry.path("location").asText("").trim();
		String what = firstNonBlank(entry, "activity", "intent");
		LocalDateTime start = parseTime(time);

		if (start == null) {
		    start = startOfTimeOfDay(time);
		}

		if (start == null || what.isEmpty()) {
		    LOG.warn("[Plans] Skipping entry with no usable time or activity: " + entry);
		    continue;
		}

		// Rebuilt into the same one-line shape the rest of the prompts
		// show as examples, so downstream prompts see a consistent
		// format regardless of which parse path produced the plan.
		String description = location.isEmpty() ? time + ", " + what : time + " at " + location + ", " + what;

		Plan plan = new Plan(description, start);
		plan.setLocation(location.isEmpty() ? null : location);
		plans.add(plan);
	    }
	} catch (Exception e) {
	    LOG.warn("[Plans] Response was not valid JSON: " + e.getMessage());
	}

	return plans;
    }

    /**
     * How long, in simulated minutes, the agent has been working from their
     * current hourly plan.
     * <p>
     * Gives the activity step a sense of elapsed time. Without it every tick
     * looks like the beginning of the intention, so an agent starts the same
     * thing over rather than finishing it.
     */
    private static long minutesOnCurrentPlan(Agent agent) {
	return agent
	    .getMemoryStream()
	    .getPlans(PlanType.SHORT_TERM)
	    .stream()
	    .map(Plan::getCreatedAt)
	    .filter(java.util.Objects::nonNull)
	    .max(LocalDateTime::compareTo)
	    .map(madeAt -> Math.max(0, java.time.Duration.between(madeAt, SimulationTime.now()).toMinutes()))
	    .orElse(0L);
    }

    /**
     * Invents something that has just happened to this agent from outside the
     * town.
     * <p>
     * Returns null rather than throwing if the model gives back something
     * unusable - nothing is broken when an event fails to arrive, the town
     * simply has a quieter day.
     */
    public Concern generateEvent(Agent agent, Concern.Valence valence) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .with("location", locationOf(agent))
	    .with("time", SimulationTime.now().format(DateTimeFormatter.ofPattern("EEEE, h:mm a")))
	    .with("valence", describeValence(valence))
	    .setPrompt(SmallvilleConfig.getPrompts().getEvents().getGenerate())
	    .build();

	try {
	    JsonNode node = new ObjectMapper()
		.readTree(Util.stripCodeFence(chat.sendChat(prompt.labelled("externalEvent").asJsonResponse(), .9)));

	    String description = node.path("event").asText("").trim();

	    if (description.isEmpty()) {
		return null;
	    }

	    long hours = Math.max(4, Math.min(48, node.path("hours").asLong(12)));

	    // Valence is the caller's, not the model's. Left to the model the mix
	    // is whatever it happens to lean toward, which is neither known nor
	    // tunable - and the balance between good and bad news is the main
	    // thing that decides what kind of town this is.
	    return new Concern(description, SimulationTime.now(), Duration.ofHours(hours),
		    readEnum(node, "source", Concern.Source.class, Concern.Source.CHANCE), valence,
		    readEnum(node, "demand", Concern.Demand.class, Concern.Demand.NOTHING),
		    readEnum(node, "privacy", Concern.Privacy.class, Concern.Privacy.PRIVATE));
	} catch (Exception e) {
	    LOG.warn("[Events] Could not read a generated event: " + e.getMessage());
	    return null;
	}
    }

    private static String describeValence(Concern.Valence valence) {
	return switch (valence) {
	case GOOD -> "Something good, or at least welcome. It does not have to be large - "
		+ "most good news is small.";
	case BAD -> "Something unwelcome. Again it does not have to be large: a fee, a "
		+ "cancellation, a bill, an awkward moment. Save the heavy ones for rarely.";
	case AMBIGUOUS -> "Something they cannot read either way. No information, all "
		+ "weight - a call with no voicemail, a note with no explanation, a "
		+ "message that says only \"can we talk\".";
	};
    }

    private static <T extends Enum<T>> T readEnum(JsonNode node, String field, Class<T> type, T fallback) {
	try {
	    return Enum.valueOf(type, node.path(field).asText("").trim().toUpperCase());
	} catch (IllegalArgumentException e) {
	    return fallback;
	}
    }

    /**
     * The day's goals the agent has not yet been to the place for, so the hourly
     * planner knows what is still hanging over them.
     */
    private static String describeOutstandingGoals(Agent agent) {
	String outstanding = agent
	    .getMemoryStream()
	    .getPlans(PlanType.LONG_TERM)
	    .stream()
	    .filter(plan -> !plan.isAddressed())
	    .map(Plan::getDescription)
	    .collect(Collectors.joining("; "));

	return outstanding.isBlank() ? "nothing in particular" : outstanding;
    }

    private static String firstNonBlank(JsonNode entry, String... fields) {
	for (String field : fields) {
	    String value = entry.path(field).asText("").trim();

	    if (!value.isEmpty()) {
		return value;
	    }
	}

	return "";
    }

    /**
     * Daily goals say "morning" rather than a clock time, but Plan is a
     * TemporalMemory and everything that orders plans needs an instant. Maps a
     * time of day onto a representative hour of the simulated day.
     */
    private static LocalDateTime startOfTimeOfDay(String when) {
	if (when == null) {
	    return null;
	}

	Integer hour = switch (when.toLowerCase().trim()) {
	case "early morning" -> 6;
	case "morning" -> 9;
	case "midday", "noon" -> 12;
	case "afternoon" -> 15;
	case "evening" -> 19;
	case "night", "late night" -> 22;
	default -> null;
	};

	return hour == null ? null : LocalDateTime.of(SimulationTime.now().toLocalDate(), LocalTime.of(hour, 0));
    }

    @Override
    public CurrentActivity getCurrentActivity(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .withWorld(world)
	    .withLocations(world.getLocations())
	    .with("minutesOnThis", minutesOnCurrentPlan(agent))
	    .setPrompt(SmallvilleConfig.getPrompts().getPlans().getCurrent())
	    .build();

	String response = chat.sendChat(prompt.labelled("currentActivity").asCheap(), .5);

	CurrentActivity activity = Util.parseAsClass(response, CurrentActivity.class);
	LOG.info(activity.getActivity() + activity.getLocation());

	// Deliberately not past-tensed. convertToPastTense mangles anything it
	// does not recognise - a live run produced "eyes closeded", "shreded
	// cheese" and "setting the bowl aside to rested" - and it ran over every
	// observation an agent ever formed. A memory reads perfectly well in the
	// tense the activity was written in.
	activity.setLastActivity(agent.getCurrentActivity());

	return activity;
    }

    @Override
    public Conversation getConversationIfExists(Agent agent, Agent other, String topic) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .withOther(other)
	    .withObservation(topic)
	    .with("location", locationOf(agent))
	    .with("history", describeHistory(agent, List.of(other)))
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getConversation())
	    .build();

	String response = chat.sendChat(prompt.labelled("conversationPair").asJsonResponse(), .7);

	return new Conversation(List.of(agent.getFullName(), other.getFullName()), parseDialog(response));
    }

    private static String locationOf(Agent agent) {
	return agent.getLocation() == null ? "somewhere in town" : agent.getLocation().getFullPath();
    }

    /**
     * Reads the {@code {"lines": [{"speaker","text"}]}} shape both conversation
     * prompts ask for, falling back to the older {@code Name: message} line
     * format if the model ignores it.
     * <p>
     * The line format alone was not survivable: asked for dialogue, the model
     * would sometimes answer with a narrative scene instead, with the speech
     * embedded in prose. That parsed to zero lines and the entire conversation
     * was discarded after the call had already been paid for.
     */
    private List<Dialog> parseDialog(String response) {
	List<Dialog> dialogs = new ArrayList<>();

	try {
	    JsonNode lines = new ObjectMapper().readTree(Util.stripCodeFence(response)).path("lines");

	    if (lines.isArray()) {
		for (JsonNode line : lines) {
		    String speaker = line.path("speaker").asText("").trim();
		    String text = line.path("text").asText("").trim();

		    if (!speaker.isEmpty() && !text.isEmpty()) {
			dialogs.add(new Dialog(speaker, text));
		    }
		}
	    }
	} catch (Exception e) {
	    LOG.warn("[Conversation] Response was not valid JSON: " + e.getMessage());
	}

	if (!dialogs.isEmpty()) {
	    return dialogs;
	}

	LOG.warn("[Conversation] Could not read dialogue as JSON, falling back to line parsing");

	for (String line : response.split("\\r?\\n")) {
	    String[] parts = line.split(":\\s+", 2);

	    if (parts.length == 2) { // ignores anything before the conversation
		dialogs.add(new Dialog(parts[0].trim(), parts[1].trim()));
	    }
	}

	return dialogs;
    }

    @Override
    public Conversation getGroupConversation(Agent initiator, List<Agent> others, String topic) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(initiator)
	    .withOthers(others)
	    .withObservation(topic)
	    .with("history", describeHistory(initiator, others))
	    .with("location", locationOf(initiator))
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getGroupConversation())
	    .build();

	String response = chat.sendChat(prompt.labelled("conversationGroup").asJsonResponse(), .7);
	List<Dialog> dialogs = parseDialog(response);

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

    /**
     * One line per pair describing how well they know each other, so tone
     * reflects their history instead of every exchange reading like a first
     * meeting.
     */
    private String describeHistory(Agent initiator, List<Agent> others) {
	List<Agent> everyone = new ArrayList<>();
	everyone.add(initiator);
	everyone.addAll(others);

	StringBuilder sb = new StringBuilder();

	for (int i = 0; i < everyone.size(); i++) {
	    for (int j = i + 1; j < everyone.size(); j++) {
		String a = everyone.get(i).getFullName();
		String b = everyone.get(j).getFullName();

		sb.append(world.getRelationships().get(a, b).describe(a, b)).append("\n");
	    }
	}

	for (Agent agent : everyone) {
	    for (Agent other : everyone) {
		if (agent == other) {
		    continue;
		}

		String known = whatIsKnownAbout(agent, other);

		if (!known.isBlank()) {
		    sb
			.append(agent.getFullName())
			.append(" has seen or been told this about ")
			.append(other.getFullName())
			.append(": ")
			.append(known)
			.append("\n");
		}
	    }
	}

	return sb.toString().trim();
    }

    /**
     * What one agent has actually picked up about another, from their own
     * memories.
     * <p>
     * Every conversation prompt used to list every participant's full
     * characteristics to everybody, so an agent walked into a first meeting
     * holding the other's secrets. It showed: in one run Paul greeted Ricky by
     * referring to what Ricky does to people, having never met him, and the
     * model invented a shared past - "that night at the docks" - to justify
     * knowing it. Handed knowledge a character should not have, a model will
     * manufacture a reason they have it.
     * <p>
     * People are learned, not looked up. This returns only what is in the
     * agent's own memory stream about the other, which is what they said in
     * earshot and what they were seen doing.
     */
    private static String whatIsKnownAbout(Agent agent, Agent other) {
	String fullName = other.getFullName();
	String firstName = fullName.split("\\s+")[0];

	List<String> known = agent
	    .getMemoryStream()
	    .getMemories()
	    .stream()
	    .map(Memory::getDescription)
	    .filter(description -> description.contains(firstName) || description.contains(fullName))
	    .collect(Collectors.toList());

	// The most recent few. All of them would swamp the prompt once agents
	// have shared a room for a while.
	int from = Math.max(0, known.size() - 4);

	return String.join("; ", known.subList(from, known.size()));
    }

    /**
     * Judges how an exchange left its participants, as an affinity shift
     * between -1 and 1.
     * <p>
     * Kept to a single word of output on purpose: this runs once per
     * conversation on top of everything else, so it should be the cheapest
     * call in the system.
     */
    public double classifyConversationTone(Conversation conversation) {
	StringBuilder transcript = new StringBuilder();

	for (Dialog line : conversation.getDialog()) {
	    transcript.append(line.getName()).append(": ").append(line.getMessage()).append("\n");
	}

	PromptRequest prompt = new PromptBuilder()
	    .with("transcript", transcript.toString().trim())
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getConversationTone())
	    .build();

	String answer = chat.sendChat(prompt.labelled("conversationTone").asCheap(), .1).trim().toLowerCase();

	if (answer.contains("warm")) {
	    return 0.15;
	}

	if (answer.contains("tense")) {
	    return -0.15;
	}

	return 0;
    }

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

	// The simulated date, not the wall clock. The simulated clock advances
	// by a timestep every tick, so it crosses midnight within minutes of
	// real time - after which wall-clock dating stamped every new plan onto
	// a day the simulation had already left behind.
	return LocalDateTime.of(SimulationTime.now().toLocalDate(), LocalTime.of(hour, minute));
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

	String tenses = chat.sendChat(tensesPrompt.labelled("combineSentences").asCheap(), .1);

	PromptRequest changedPrompt = new PromptBuilder()
	    .withAgent(agent)
	    .withTense(tenses)
	    .withWorld(world)
	    .withLocations(world.getLocations())
	    .setPrompt(SmallvilleConfig.getPrompts().getWorld().getObjectStates())
	    .build();

	String response = chat.sendChat(changedPrompt.labelled("objectStates").asCheap(), .3);

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

	String query = lastNonBlankLine(chat.sendChat(prompt.labelled("reflectionQuestion").asCheap(), .1));

	LOG.debug("[Reflections] Question: " + query);

	// Previously this stripped two leading characters twice - once when
	// picking the line and again when querying - so the question was cut
	// four characters in, and any short answer threw
	// StringIndexOutOfBoundsException out of the middle of a tick.
	Set<Memory> filter = new HashSet<Memory>();
	filter.addAll(agent.getMemoryStream().getRelevantMemories(query));
	List<Memory> memories = new ArrayList<>(filter); // Convert the set back to a list

	LOG.debug(String.join(",", memories.stream().map(m -> m.getDescription()).collect(Collectors.toList())));

	PromptRequest secondPrompt = new PromptBuilder()
	    .withAgent(agent)
	    .withStatements(memories.stream().map(m -> m.getDescription()).collect(Collectors.toList()))
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getReflectionResult())
	    .build();

	String description = chat.sendChat(secondPrompt.labelled("reflectionInsight"), .8);

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

	String response = chat.sendChat(prompt.labelled("shouldReact").asCheap(), .2);
	Reaction result = Util.parseAsClass(response, Reaction.class);

	LOG.debug("reacting " + result.getAnswer());
	return result;
    }

    /**
     * The last line with content, stripped of any list marker the model put in
     * front of it ("3. ", "- ", "* ").
     */
    private static String lastNonBlankLine(String response) {
	String[] lines = response.split("\\r?\\n");

	for (int i = lines.length - 1; i >= 0; i--) {
	    String line = lines[i].trim();

	    if (!line.isBlank()) {
		return line.replaceFirst("^(?:\\d+[.)]|[-*•])\\s*", "");
	    }
	}

	return response.trim();
    }

    public String createTraits(Agent agent) {
	PromptRequest prompt = new PromptBuilder()
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getAgent().getCharacteristics())
	    .build();

	return chat.sendChat(prompt.labelled("characteristics"), .5);
    }

    @Override
    public Dialog saySomething(Agent agent, String observation) {
	PromptRequest request = new PromptBuilder()
	    .withObservation(observation)
	    .withAgent(agent)
	    .setPrompt(SmallvilleConfig.getPrompts().getReactions().getSay())
	    .build();
	
	String result = chat.sendChat(request.labelled("saySomething"), .5);
	
	return new Dialog(agent.getFullName(), result);
    }
}
