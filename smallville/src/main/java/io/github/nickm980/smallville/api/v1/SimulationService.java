package io.github.nickm980.smallville.api.v1;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.memory.Memory;
import io.github.nickm980.smallville.memory.MemoryStream;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.TemporalMemory;
import io.github.nickm980.smallville.prompts.PromptRequest;
import io.github.nickm980.smallville.update.UpdateService;

public class SimulationService {

    private Logger LOG = LoggerFactory.getLogger(SimulationService.class);

    private final ModelMapper mapper;
    private final UpdateService prompts;
    private final World world;
    private final LLM chat;
    private int progress;

    // Tracks the last simulated time two agents talked, keyed by their sorted
    // names, so co-located agents don't restart a fresh conversation every
    // single tick they happen to remain in the same room.
    private final Map<String, LocalDateTime> lastConversationAt = new HashMap<>();
    private static final Duration CONVERSATION_COOLDOWN = Duration.ofMinutes(60);

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
	    result.add(mapper.fromLocation(location));
	}

	return result;
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

	triggerProximityReactions();
    }

    /**
     * Nothing else in the update pipeline notices when two agents end up in the
     * same location - conversations only ever happen if something explicitly
     * feeds an agent a reactable observation. This is that missing nudge: after
     * every tick, check for co-located agents and give each pair a chance to
     * notice each other and start talking.
     */
    private void triggerProximityReactions() {
	List<Agent> agents = world.getAgents();

	// Caps each agent to at most one proximity reaction per tick. Without this,
	// a location with k co-located agents triggers k*(k-1)/2 full reaction
	// chains (one per pair) - with several agents clustered together that
	// turns a single tick into a very long queue of sequential LLM calls.
	Set<String> reactedThisTick = new HashSet<>();

	for (int i = 0; i < agents.size(); i++) {
	    for (int j = i + 1; j < agents.size(); j++) {
		Agent a = agents.get(i);
		Agent b = agents.get(j);

		if (reactedThisTick.contains(a.getFullName()) || reactedThisTick.contains(b.getFullName())) {
		    continue;
		}

		if (a.getLocation() == null || b.getLocation() == null) {
		    continue;
		}

		if (!a.getLocation().getFullPath().equals(b.getLocation().getFullPath())) {
		    continue;
		}

		String pairKey = pairKey(a.getFullName(), b.getFullName());
		LocalDateTime lastTime = lastConversationAt.get(pairKey);
		LocalDateTime now = SimulationTime.now();

		if (lastTime != null && Duration.between(lastTime, now).compareTo(CONVERSATION_COOLDOWN) < 0) {
		    continue;
		}

		lastConversationAt.put(pairKey, now);
		reactedThisTick.add(a.getFullName());
		reactedThisTick.add(b.getFullName());

		try {
		    // Deliberately just the name, not the other agent's activity text -
		    // that text is agent-authored and can itself mention names (including
		    // this agent's own), which confuses the downstream name-extraction
		    // logic used to figure out who the observation is about.
		    prompts.react(a, b.getFullName() + " is here.");
		} catch (Exception e) {
		    LOG.error("Failed to trigger proximity reaction between " + a.getFullName() + " and "
			    + b.getFullName(), e);
		}
	    }
	}
    }

    private String pairKey(String a, String b) {
	return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
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
