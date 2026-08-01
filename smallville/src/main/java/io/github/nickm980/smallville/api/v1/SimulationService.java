package io.github.nickm980.smallville.api.v1;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.api.v1.dto.*;
import io.github.nickm980.smallville.entities.*;
import io.github.nickm980.smallville.exceptions.AgentNotFoundException;
import io.github.nickm980.smallville.exceptions.LocationNotFoundException;
import io.github.nickm980.smallville.exceptions.SmallvilleException;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.locations.LocationImageMeta;
import io.github.nickm980.smallville.locations.LocationImageStore;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.MemoryStream;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.TemporalMemory;
import io.github.nickm980.smallville.prompts.PromptRequest;
import io.github.nickm980.smallville.story.StorySnapshot;
import io.github.nickm980.smallville.story.StoryStore;
import io.github.nickm980.smallville.update.UpdateService;

public class SimulationService {

    private Logger LOG = LoggerFactory.getLogger(SimulationService.class);

    private final ModelMapper mapper;
    private final UpdateService prompts;
    private final World world;
    private final LLM chat;
    private int progress;

    // Tracks the last simulated time a location had a conversation, keyed by
    // location name, so agents lingering in the same room don't restart a
    // fresh conversation every single tick. Keyed per-location rather than
    // per-participant-pair since who actually joins a group conversation is
    // probabilistic and changes tick to tick.
    private final Map<String, LocalDateTime> lastConversationAt = new HashMap<>();
    private static final Duration CONVERSATION_COOLDOWN = Duration.ofMinutes(60);
    private final StoryStore storyStore = new StoryStore();
    private final LocationImageStore imageStore = new LocationImageStore();

    public SimulationService(LLM llm, World world) {
	this.world = world;
	this.mapper = new ModelMapper();
	this.prompts = new UpdateService(llm, world);
	this.chat = llm;
	this.progress = 0;
    }

    public void createMemory(CreateMemoryRequest request) {
	Agent agent = world.getAgent(request.getName()).orElseThrow();
	Observation observation = new Observation(request.getDescription());
	observation.setReactable(request.isReactable());
	agent.getMemoryStream().add(observation);

	if (observation.isReactable()) {
	    SimulationTime.update();
	    prompts.react(agent, observation.getDescription());
	}
    }

    public AgentStateResponse getAgentState(String name) {
	Agent agent = world.getAgent(name).orElseThrow(() -> new AgentNotFoundException(name));
	return mapper.fromAgent(agent);
    }

    public List<AgentStateResponse> getAgents() {
	List<Agent> agents = world.getAgents();

	return agents.stream().map(mapper::fromAgent).collect(Collectors.toList());
    }

    public List<LocationStateResponse> getAllLocations() {
	List<LocationStateResponse> result = new ArrayList<LocationStateResponse>();

	for (Location location : world.getLocations()) {
	    LocationStateResponse response = mapper.fromLocation(location);
	    response.setHasImage(imageStore.hasImage(location.getFullPath()));
	    result.add(response);
	}

	return result;
    }

    public void saveLocationImage(String locationName, byte[] bytes, String contentType) {
	imageStore.save(locationName, bytes, contentType);
    }

    public Optional<LocationImageMeta> findLocationImage(String locationName) {
	return imageStore.find(locationName);
    }

    public Optional<byte[]> readLocationImageBytes(String locationName) {
	return imageStore.readBytes(locationName);
    }

    public void createAgent(CreateAgentRequest request) {
	List<Characteristic> characteristics = request
	    .getMemories()
	    .stream()
	    .map(c -> new Characteristic(c))
	    .collect(Collectors.toList());
	// Location : Object
	Location location = world.getLocation(request.getLocation()).orElse(null);

	if (location == null) {
	    LOG.error("Could not find location " + request.getLocation());
	    throw new LocationNotFoundException(request.getLocation());
	}

	Agent agent = new Agent(request.getName(), characteristics, request.getActivity(), location);

	if (world.create(agent)) {
	    String traits = prompts.createTraitsWithCharacteristics(agent);
	    agent.setTraits(traits);
	}
    }

    public GeneratedCharacterResponse generateCharacter() {
	List<String> existingNames = world
	    .getAgents()
	    .stream()
	    .map(Agent::getFullName)
	    .collect(Collectors.toList());

	GeneratedCharacterResponse result = null;
	int attempts = 0;

	while (result == null && attempts < 3) {
	    attempts++;

	    try {
		String raw = chat.sendChat(new PromptRequest.User(buildGenerateCharacterPrompt(existingNames)), 1.0);
		GeneratedCharacterResponse candidate = parseGeneratedCharacter(raw);

		boolean collides = existingNames
		    .stream()
		    .anyMatch(name -> name.equalsIgnoreCase(candidate.getName()));

		if (!collides) {
		    result = candidate;
		} else {
		    LOG.warn("Generated character name '" + candidate.getName() + "' collides with an existing agent, retrying");
		}
	    } catch (Exception e) {
		LOG.error("Failed to generate character, retrying", e);
	    }
	}

	if (result == null) {
	    throw new SmallvilleException("Could not generate a unique character after several attempts");
	}

	return result;
    }

    private String buildGenerateCharacterPrompt(List<String> existingNames) {
	String avoidNames = existingNames.isEmpty() ? "none yet"
		: String.join(", ", existingNames);

	return """
		Invent an original resident of a small town for a life simulation game.

		Names already in use, your character's name (first and last) must NOT match or closely resemble any of these: %s

		Respond with ONLY raw JSON, no markdown code fences, no commentary, in exactly this shape:
		{"name": "First Last", "memories": ["...", "...", "...", "...", "...", "..."]}

		Rules for "memories":
		- Provide 5 to 7 entries.
		- Each entry is a separate, specific sentence written in third person, starting with the character's first name.
		- Together they must cover: their job or daily role, at least two distinct personality traits (not just "friendly" or "kind" alone - be specific and a little unusual), one meaningful relationship or social dynamic (family, friend, rival, unrequited crush, etc.), one personal goal or desire, and one flaw, fear, secret, or contradiction that makes them feel like a real person rather than a stock character.
		- Avoid generic filler like "X likes reading" as the only trait - go deeper into why, or pair it with something more surprising.
		- Do not repeat the same idea across multiple entries.
		""".formatted(avoidNames);
    }

    private GeneratedCharacterResponse parseGeneratedCharacter(String raw) {
	String cleaned = raw.trim();

	if (cleaned.startsWith("```")) {
	    cleaned = cleaned.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
	}

	ObjectMapper objectMapper = new ObjectMapper();

	try {
	    JsonNode node = objectMapper.readTree(cleaned);
	    GeneratedCharacterResponse result = new GeneratedCharacterResponse();

	    String name = node.get("name").asText().trim();
	    List<String> memories = new ArrayList<>();

	    node.get("memories").forEach(memory -> memories.add(memory.asText()));

	    if (name.isEmpty() || memories.isEmpty()) {
		throw new SmallvilleException("Generated character was missing a name or memories");
	    }

	    result.setName(name);
	    result.setMemories(memories);

	    return result;
	} catch (Exception e) {
	    throw new SmallvilleException("Could not parse generated character: " + e.getMessage());
	}
    }

    private static final DateTimeFormatter STORY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static final DateTimeFormatter STORY_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter STORY_FULL_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a");

    public StoryResponse getStory() {
	Optional<StorySnapshot> snapshot = storyStore.load();
	StoryResponse result = new StoryResponse();

	if (snapshot.isEmpty()) {
	    result.setExists(false);
	    result.setStory("");
	    return result;
	}

	StorySnapshot current = snapshot.get();
	result.setExists(true);
	result.setStory(current.getStory());
	result.setAsOfDate(current.getAsOf().format(STORY_DATE_FORMAT));
	result.setAsOfTime(current.getAsOf().format(STORY_TIME_FORMAT));
	result.setMinutesSinceUpdate(Duration.between(current.getAsOf(), SimulationTime.now()).toMinutes());
	return result;
    }

    // Synchronized so two overlapping requests (e.g. an impatient double
    // click, or two browser tabs) can't both read the same prior snapshot
    // before either has saved, which would let them independently "discover"
    // the same new material and race to overwrite each other's save.
    public synchronized StoryResponse generateStory() {
	Optional<StorySnapshot> previous = storyStore.load();
	LocalDateTime since = previous.map(StorySnapshot::getAsOf).orElse(LocalDateTime.MIN);

	String newDiaryText = collectNewDiaryText(since);
	String newConversationText = collectNewConversationText(since);

	if (newDiaryText.isBlank() && newConversationText.isBlank()) {
	    StoryResponse result = getStory();
	    result.setUpdated(false);
	    result.setMessage(previous.isPresent() ? "No new developments since the last recap."
		    : "Nothing has happened in the town yet.");
	    return result;
	}

	String roster = world.getAgents().stream().map(Agent::getFullName).collect(Collectors.joining(", "));

	String prompt = previous.isPresent()
		? buildContinuationPrompt(previous.get().getStory(), newDiaryText, newConversationText, roster)
		: buildFirstStoryPrompt(newDiaryText, newConversationText, roster);

	String passage;
	try {
	    passage = chat.sendChat(new PromptRequest.User(prompt), 0.7).trim();
	} catch (Exception e) {
	    LOG.error("Failed to generate story", e);
	    throw new SmallvilleException("Could not generate the story right now");
	}

	String fullStory = previous.isPresent() ? previous.get().getStory() + "\n\n" + passage : passage;
	StorySnapshot snapshot = new StorySnapshot(fullStory, SimulationTime.now());
	storyStore.save(snapshot);

	StoryResponse result = new StoryResponse();
	result.setExists(true);
	result.setUpdated(true);
	result.setStory(fullStory);
	result.setAsOfDate(snapshot.getAsOf().format(STORY_DATE_FORMAT));
	result.setAsOfTime(snapshot.getAsOf().format(STORY_TIME_FORMAT));
	result.setMinutesSinceUpdate(0);
	return result;
    }

    // Same diary definition getDiary() uses (excludes Characteristic memories
    // and conversation-derived Dialog observations), applied directly against
    // the domain objects so we can filter by real timestamp instead of the
    // already-formatted display string MemoryResponse exposes.
    private String collectNewDiaryText(LocalDateTime since) {
	StringBuilder sb = new StringBuilder();

	for (Agent agent : world.getAgents()) {
	    List<Memory> entries = agent
		.getMemoryStream()
		.getMemories()
		.stream()
		.filter(m -> !(m instanceof Characteristic))
		.filter(m -> !(m instanceof Observation && ((Observation) m).isDialog()))
		// Plan.getTime() is the scheduled time the plan describes, not when
		// it was written - a plan for 8pm looks "new" forever once 8pm is
		// after the last snapshot, even if it was already narrated. A
		// recap should cover what agents actually did, not what they
		// merely scheduled, so plans are excluded here entirely.
		.filter(m -> !(m instanceof Plan))
		.filter(m -> m instanceof TemporalMemory)
		.filter(m -> ((TemporalMemory) m).getTime().isAfter(since))
		.sorted(Comparator.comparing(m -> ((TemporalMemory) m).getTime()))
		.collect(Collectors.toList());

	    if (entries.isEmpty()) {
		continue;
	    }

	    sb.append(agent.getFullName()).append(":\n");
	    for (Memory memory : entries) {
		sb.append("- ").append(memory.getDescription()).append("\n");
	    }
	    sb.append("\n");
	}

	return sb.toString();
    }

    private String collectNewConversationText(LocalDateTime since) {
	StringBuilder sb = new StringBuilder();

	world
	    .getAllConversations()
	    .stream()
	    .filter(conversation -> conversation.getTime() != null && conversation.getTime().isAfter(since))
	    .sorted(Comparator.comparing(Conversation::getTime))
	    .forEach(conversation -> {
		sb.append(String.join(", ", conversation.getParticipants())).append(" talked:\n");
		for (Dialog line : conversation.getDialog()) {
		    sb.append("  ").append(line.getName()).append(": ").append(line.getMessage()).append("\n");
		}
		sb.append("\n");
	    });

	return sb.toString();
    }

    private String buildFirstStoryPrompt(String diaryText, String conversationText, String roster) {
	String now = SimulationTime.now().format(STORY_FULL_FORMAT);

	return """
		You are narrating the story of a small town for a life simulation game, based on real events from a simulation, not fiction you invent.

		The only real residents of this town are: %s
		Do not introduce, name, or refer to any person who is not on this list. Every character in the recap must be one of these people.

		The current in-world date and time is: %s
		Everything below happened on or before this date and time. Do not invent an earlier scene, a different day, or a "meanwhile, earlier that week" flashback that isn't directly supported by the material below - if something isn't given below, it did not happen and must not appear.

		Here is what has happened so far, drawn from each resident's diary:

		%s

		Here are the conversations that took place:

		%s

		Write a clear, engaging prose recap of what has happened, in past tense, third person, like a narrator catching a reader up on a story. Prioritize what actually happened and what people said over describing scenery, weather, or mood - a touch of atmosphere is fine, but don't let it crowd out the events. If a stretch had little or nothing happen, say so briefly rather than inventing scene-setting to fill the space. Keep it only as long as the material actually warrants - do not pad it out. Only use what is given above - do not invent characters, events, locations, or details that are not directly supported by this material, and do not repurpose a real conversation into a different day or a different set of participants than it actually happened with. You can mention the current in-world time naturally if it's useful, but do not end with a formal paragraph summarizing where everyone stands.
		""".formatted(roster, now, diaryText, conversationText);
    }

    private String buildContinuationPrompt(String existingStory, String diaryText, String conversationText, String roster) {
	String now = SimulationTime.now().format(STORY_FULL_FORMAT);

	return """
		You are continuing the story of a small town for a life simulation game, based on real new events from a simulation, not fiction you invent.

		The only real residents of this town are: %s
		Do not introduce, name, or refer to any person who is not on this list. Every character in this passage must be one of these people.

		STORY SO FAR (already written and established - do not restate, summarize, or rewrite any of this, it is here only so you understand the tone and continuity):
		%s

		The current in-world date and time is: %s
		The new material below happened on or before this date and time. Do not invent an earlier scene, a different day, or a "meanwhile, earlier" flashback that isn't directly supported by the material below - if something isn't given below or in the story so far, it did not happen and must not appear.

		Here is what is NEW since the story above was last updated, drawn from each resident's diary:

		%s

		Here are the new conversations that took place:

		%s

		Write ONLY the next passage of the story, continuing directly from where it left off, in the same voice and tense. Prioritize what actually happened and what people said over describing scenery, weather, or mood - a touch of atmosphere is fine, but don't let it crowd out the events, and don't re-describe light, weather, or setting you've already established recently. If this stretch had little or nothing happen, say so briefly in a sentence or two rather than inventing a full scene to fill the space. Keep the length proportional to how much actually happened - do not pad it out. Do not include a heading or any re-summary of what came before. Only use what is given above as new material - do not invent events, locations, or details that are not directly supported by this material, do not repurpose a real conversation into a different day or a different set of participants than it actually happened with, and do not contradict anything already established. You can mention the current in-world time naturally if it's useful, but do not end with a formal paragraph summarizing where everyone stands.
		""".formatted(roster, existingStory, now, diaryText, conversationText);
    }

    public void createLocation(CreateLocationRequest request) {
	if (world.getLocation(request.getName()).isPresent()) {
	    throw new SmallvilleException("Location already exists");
	}

	world.create(new Location(request.getName()));
    }

    public void deleteAgent(String name) {
	if (!world.getAgent(name).isPresent()) {
	    throw new AgentNotFoundException(name);
	}

	world.deleteAgent(name);
    }

    public void deleteLocation(String name) {
	if (!world.getLocation(name).isPresent()) {
	    throw new LocationNotFoundException(name);
	}

	world.deleteLocation(name);
    }

    // Wipes conversations, every agent's diary, and the generated story -
    // agents, locations, and simulation timing/state are left untouched.
    public void resetSimulationData() {
	world.resetSimulationData();
	storyStore.clear();
    }

    public List<Map<String, Object>> getCharacteristics(String name) {
	Agent agent = world.getAgent(name).orElseThrow(() -> new AgentNotFoundException(name));
	List<Characteristic> characteristics = agent.getMemoryStream().getCharacteristics();
	List<Map<String, Object>> result = new ArrayList<>();

	for (int i = 0; i < characteristics.size(); i++) {
	    result.add(Map.of("index", i, "description", characteristics.get(i).getDescription()));
	}

	return result;
    }

    public void addCharacteristic(String name, String description) {
	Agent agent = world.getAgent(name).orElseThrow(() -> new AgentNotFoundException(name));
	agent.getMemoryStream().add(new Characteristic(description));
    }

    public void removeCharacteristic(String name, int index) {
	Agent agent = world.getAgent(name).orElseThrow(() -> new AgentNotFoundException(name));
	List<Characteristic> characteristics = agent.getMemoryStream().getCharacteristics();

	if (index < 0 || index >= characteristics.size()) {
	    throw new SmallvilleException("Invalid characteristic index: " + index);
	}

	agent.getMemoryStream().remove(characteristics.get(index));
    }

    public List<MemoryResponse> getMemoriesOfAgent(String agentName) {
	List<MemoryResponse> result = world
	    .getAgent(agentName)
	    .orElseThrow(() -> new AgentNotFoundException(agentName))
	    .getMemoryStream()
	    .getMemories()
	    .stream()
	    // Sort by the real timestamp before formatting - sorting the already-formatted
	    // "hh:mm a" string instead breaks ordering across the AM/PM boundary.
	    .sorted(Comparator.comparing(
		    (Memory m) -> (m instanceof TemporalMemory) ? ((TemporalMemory) m).getTime() : null,
		    Comparator.nullsFirst(Comparator.naturalOrder())))
	    .map(mapper::fromMemory)
	    .collect(Collectors.toList());

	return result;
    }

    public List<MemoryResponse> getDiary(String agentName) {
	return getMemoriesOfAgent(agentName)
	    .stream()
	    .filter(memory -> !"Characteristic".equals(memory.getType()))
	    .filter(memory -> !"Dialog".equals(memory.getType()))
	    .collect(Collectors.toList());
    }

    public String askQuestion(String name, String question) {
	Agent agent = world.getAgent(name).orElseThrow(() -> new AgentNotFoundException(name));
	String result = prompts.ask(agent, question);

	return result;
    }

    public void updateState() throws SmallvilleException {
//	AnalyticsListener.refresh();
	if (world.getAgents().size() == 0) {
	    throw new SmallvilleException("Must create an agent before changing the state");
	}

	SimulationTime.update();

	for (Agent agent : world.getAgents()) {
	    try {
		prompts.updateAgent(agent);
	    } catch (Exception e) {
		LOG.error("Failed to update agent " + agent.getFullName() + ", skipping for this tick", e);
	    }
	}

	triggerGroupConversations();
    }

    private static final int MAX_GROUP_PARTICIPANTS = 5;
    private static final double EXTRA_PARTICIPANT_JOIN_CHANCE = 0.5;

    /**
     * Nothing else in the update pipeline notices when several agents end up
     * in the same location - conversations only ever happen if something
     * explicitly feeds an agent a reactable observation. This is that missing
     * nudge: after every tick, group agents by location and give each
     * multi-person location a chance to strike up a conversation. Two
     * co-located agents always talk (matches the old pairwise behavior
     * exactly); anyone beyond that has a per-person chance of joining in,
     * capped at MAX_GROUP_PARTICIPANTS so a very crowded location doesn't
     * produce an unwieldy prompt.
     */
    private void triggerGroupConversations() {
	Map<String, List<Agent>> byLocation = new HashMap<>();

	for (Agent agent : world.getAgents()) {
	    if (agent.getLocation() == null) {
		continue;
	    }
	    byLocation.computeIfAbsent(agent.getLocation().getFullPath(), k -> new ArrayList<>()).add(agent);
	}

	for (Map.Entry<String, List<Agent>> entry : byLocation.entrySet()) {
	    String location = entry.getKey();
	    List<Agent> here = entry.getValue();

	    if (here.size() < 2) {
		continue;
	    }

	    LocalDateTime lastTime = lastConversationAt.get(location);
	    LocalDateTime now = SimulationTime.now();

	    if (lastTime != null && Duration.between(lastTime, now).compareTo(CONVERSATION_COOLDOWN) < 0) {
		continue;
	    }

	    List<Agent> participants = selectParticipants(here);
	    lastConversationAt.put(location, now);

	    try {
		prompts.triggerGroupConversation(participants, "Everyone listed is gathered here together right now.");
	    } catch (Exception e) {
		LOG.error("Failed to trigger group conversation at " + location, e);
	    }
	}
    }

    private List<Agent> selectParticipants(List<Agent> here) {
	List<Agent> shuffled = new ArrayList<>(here);
	Collections.shuffle(shuffled);

	List<Agent> selected = new ArrayList<>(shuffled.subList(0, 2));

	for (int i = 2; i < shuffled.size() && selected.size() < MAX_GROUP_PARTICIPANTS; i++) {
	    if (Math.random() < EXTRA_PARTICIPANT_JOIN_CHANCE) {
		selected.add(shuffled.get(i));
	    }
	}

	return selected;
    }

    public List<ConversationGroupResponse> getAllConversations() {
	return world
	    .getAllConversations()
	    .stream()
	    .sorted(Comparator.comparing(Conversation::getTime, Comparator.nullsFirst(Comparator.naturalOrder())))
	    .map(mapper::fromConversationGroup)
	    .collect(Collectors.toList());
    }

    public List<ConversationResponse> getConversations() {
	List<ConversationResponse> result = new ArrayList<ConversationResponse>();
	List<Conversation> conversations = world
	    .getConversationsAfter(SimulationTime.now().minus(SimulationTime.getStepDuration()));

	for (Conversation conversation : conversations) {
	    result.addAll(mapper.fromConversation(conversation));
	}

	return result;
    }

    public void setTimestep(SetTimestepRequest request) {
	long durationValue = Long.parseLong(request.getNumOfMinutes());
	Duration duration = Duration.ofMinutes(durationValue);
	SimulationTime.setStep(duration);
    }

    public int getProgress() {
	return progress;
    }

    public void setState(String location, String state) {
	world.setState(location, state);
    }

    Map<UUID, MemoryStream> memories = new HashMap<UUID, MemoryStream>();

    public UUID createMemoryStream() {
	UUID uuid = UUID.randomUUID();
	memories.put(uuid, new MemoryStream());
	return uuid;
    }

    public List<String> getMemories(UUID uuid, String query) {
	MemoryStream stream = memories.get(uuid);
	
	return stream
	    .getRelevantMemories(query)
	    .stream()
	    .map(memory -> memory.getDescription())
	    .collect(Collectors.toList());
    }
}
