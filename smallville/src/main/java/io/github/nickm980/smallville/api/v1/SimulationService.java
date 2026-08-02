package io.github.nickm980.smallville.api.v1;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.nickm980.smallville.Settings;
import io.github.nickm980.smallville.Util;
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
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.persistence.WorldMapper;
import io.github.nickm980.smallville.persistence.WorldSnapshot;
import io.github.nickm980.smallville.persistence.WorldStore;
import io.github.nickm980.smallville.prompts.PromptBuilder;
import io.github.nickm980.smallville.prompts.PromptRequest;
import io.github.nickm980.smallville.relationships.Relationship;
import io.github.nickm980.smallville.story.StorySnapshot;
import io.github.nickm980.smallville.story.StoryStore;
import io.github.nickm980.smallville.update.UpdateService;

public class SimulationService {

    private Logger LOG = LoggerFactory.getLogger(SimulationService.class);

    private final ModelMapper mapper;
    private final UpdateService prompts;
    private final World world;
    private final LLM chat;

    // Tracks the last simulated time a location had a conversation, keyed by
    // location name, so agents lingering in the same room don't restart a
    // fresh conversation every single tick. Keyed per-location rather than
    // per-participant-pair since who actually joins a group conversation is
    // probabilistic and changes tick to tick.
    // Concurrent because deleteLocation prunes it without taking the
    // simulation lock, while the tick reads and writes it holding that lock.
    private final Map<String, LocalDateTime> lastConversationAt = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * The shortest gap between two conversations in the same place. Only a
     * floor to stop one room producing a conversation on every consecutive
     * tick - whether a conversation happens at all is decided by
     * {@link #conversationUrge}, not by this.
     */
    private static final Duration CONVERSATION_FLOOR = Duration.ofMinutes(20);

    /**
     * Seeded from {@link Settings#getSeed()} so a run can be replayed when
     * working out why a particular conversation happened. Bare Math.random()
     * and an unseeded shuffle made that impossible.
     */
    private final Random random = new Random(Settings.getSeed());
    private final StoryStore storyStore = new StoryStore();
    private final LocationImageStore imageStore = new LocationImageStore();
    private final WorldStore worldStore = new WorldStore();

    /**
     * Guards every mutation of world state.
     * <p>
     * SimulationRunner drives ticks on its own scheduled thread while Javalin
     * serves the dashboard from a separate pool, and both reach the same
     * agents, locations and conversations. Reset-vs-tick is the collision a
     * user actually hits. Fair so that a queued control action can't be
     * starved by back-to-back ticks.
     * <p>
     * Reads are deliberately left unlocked - they may see a tick partway
     * through, which is harmless for display, and locking them would stall the
     * dashboard behind every LLM call. The underlying collections are
     * concurrent so those reads are still safe.
     */
    private final ReentrantLock simulationLock = new ReentrantLock(true);

    /**
     * Long enough to outlast one agent's update (several sequential LLM calls,
     * each with a 3 minute read timeout in the worst case) without leaving a
     * clicked button hanging indefinitely.
     */
    private static final long LOCK_TIMEOUT_SECONDS = 90;

    public SimulationService(LLM llm, World world) {
	this.world = world;
	this.mapper = new ModelMapper();
	this.prompts = new UpdateService(llm, world);
	this.chat = llm;
    }

    /**
     * Runs {@code action} with exclusive access to world state.
     *
     * @throws SmallvilleException if the simulation stays busy past the timeout
     */
    private void exclusively(Runnable action) {
	exclusivelyGet(() -> {
	    action.run();
	    return null;
	});
    }

    private <T> T exclusivelyGet(Supplier<T> action) {
	boolean acquired;

	try {
	    acquired = simulationLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	} catch (InterruptedException e) {
	    Thread.currentThread().interrupt();
	    throw new SmallvilleException("Interrupted while waiting for the simulation");
	}

	if (!acquired) {
	    throw new SmallvilleException("The simulation is busy processing a tick. Try again in a moment.");
	}

	try {
	    return action.get();
	} finally {
	    simulationLock.unlock();
	}
    }

    /**
     * Restores a previously saved world. Called once at startup, before the
     * server accepts requests.
     */
    public void loadWorld() {
	Optional<WorldSnapshot> snapshot = worldStore.load();

	if (snapshot.isEmpty()) {
	    LOG.info("No saved world found, starting fresh");
	    return;
	}

	exclusively(() -> {
	    WorldMapper.restore(world, snapshot.get());
	    imageStore.pruneMissing(
		    world.getLocations().stream().map(Location::getFullPath).collect(Collectors.toSet()));
	});

	LOG.info("Restored " + world.getAgents().size() + " agents and " + world.getLocations().size()
		+ " locations from world-data/world.json");
    }

    /**
     * Snapshots the world under the lock, then writes outside it - the file
     * write must not hold up a tick.
     */
    public void saveWorld() {
	try {
	    worldStore.save(exclusivelyGet(() -> WorldMapper.toSnapshot(world)));
	} catch (Exception e) {
	    LOG.error("Failed to save the world", e);
	}
    }

    public void createMemory(CreateMemoryRequest request) {
	exclusively(() -> {
	    Agent agent = world.getAgent(request.getName()).orElseThrow();
	    Observation observation = new Observation(request.getDescription());
	    observation.setReactable(request.isReactable());
	    agent.getMemoryStream().add(observation);

	    if (observation.isReactable()) {
		SimulationTime.update();
		prompts.react(agent, observation.getDescription());
	    }
	});
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

	// No model call here on purpose. Creating an agent is a change to world
	// state, and it should cost what that costs - the agent's trait summary
	// is derived from the characteristics the user just typed, is used on a
	// single line of one prompt, and is filled in by the next tick (see
	// UpdateService.updateAgent). Blocking the request on it made adding
	// someone to the town take tens of seconds for no reason.
	//
	// This also means a failed trait call no longer means a failed
	// creation: previously the exception propagated and the agent was never
	// added at all.
	//
	// No lock either. The agent repository is a ConcurrentHashMap and this
	// is a single putIfAbsent, and updateState snapshots the agent list at
	// the start of a tick - so someone added midway through simply joins
	// from the next tick rather than racing the current one. Taking the
	// lock meant a local map insert waited out an in-flight agent update,
	// which is 30-45 seconds of model calls.
	world.create(agent);
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
		PromptRequest request = new PromptBuilder()
		    .with("existingNames", existingNames.isEmpty() ? "none yet" : String.join(", ", existingNames))
		    .setPrompt(SmallvilleConfig.getPrompts().getStory().getGenerateCharacter())
		    .build();

		GeneratedCharacterResponse candidate = parseGeneratedCharacter(chat.sendChat(request.labelled("generateCharacter").asJsonResponse(), 1.0));

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

    private GeneratedCharacterResponse parseGeneratedCharacter(String raw) {
	String cleaned = Util.stripCodeFence(raw);
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

    /** Everything the story prompt needs, snapshotted in one locked read. */
    private record StoryMaterial(String diary, String conversations, String roster, String span) {
    }

    /**
     * How much simulated time the material actually covers, in words.
     * <p>
     * Without it the model guesses, and guesses generously - a single
     * simulated day came back titled "A Week at the Cottage". It is told the
     * current date and time but never how far back the record goes.
     */
    private String describeSpan(LocalDateTime since) {
	LocalDateTime now = SimulationTime.now();
	LocalDateTime from = since.isAfter(LocalDateTime.MIN) ? since : earliestMaterialTime();

	if (from == null || !from.isBefore(now)) {
	    return "a brief moment";
	}

	long minutes = Duration.between(from, now).toMinutes();
	String length = minutes < 90 ? minutes + " minutes"
		: minutes < 60 * 36 ? (minutes / 60) + " hours" : (minutes / 60 / 24) + " days";

	return length + ", from " + from.format(STORY_FULL_FORMAT) + " to " + now.format(STORY_FULL_FORMAT);
    }

    /** The oldest thing anybody remembers, for a first recap. */
    private LocalDateTime earliestMaterialTime() {
	return world
	    .getAgents()
	    .stream()
	    .flatMap(agent -> agent.getMemoryStream().getMemories().stream())
	    .filter(memory -> memory instanceof TemporalMemory)
	    .map(memory -> ((TemporalMemory) memory).getTime())
	    .filter(java.util.Objects::nonNull)
	    .min(LocalDateTime::compareTo)
	    .orElse(null);
    }

    private static final DateTimeFormatter STORY_DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static final DateTimeFormatter STORY_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter STORY_FULL_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a");

    /**
     * How many recent passages are sent to the model word for word. Everything
     * older is represented by the rolling summary instead.
     */
    private static final int PASSAGES_KEPT_VERBATIM = 6;

    public StoryResponse getStory() {
	Optional<StorySnapshot> snapshot = storyStore.load();
	StoryResponse result = new StoryResponse();

	if (snapshot.isEmpty() || snapshot.get().isEmpty()) {
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
	StorySnapshot previous = storyStore.load().orElseGet(StorySnapshot::new);
	boolean hasPrevious = !previous.isEmpty();
	LocalDateTime since = storyCursor(previous, hasPrevious);

	// Gathered under the lock so the material can't be a half-updated view
	// of a tick in progress. The LLM call below deliberately runs outside
	// the lock - holding it across a network round trip would stall ticks
	// for as long as the model takes to answer.
	StoryMaterial material = exclusivelyGet(() -> new StoryMaterial(collectNewDiaryText(since),
		collectNewConversationText(since),
		world.getAgents().stream().map(Agent::getFullName).collect(Collectors.joining(", ")),
		describeSpan(since)));

	String newDiaryText = material.diary();
	String newConversationText = material.conversations();

	if (newDiaryText.isBlank() && newConversationText.isBlank()) {
	    StoryResponse result = getStory();
	    result.setUpdated(false);
	    result.setMessage(hasPrevious ? "No new developments since the last recap."
		    : "Nothing has happened in the town yet.");
	    return result;
	}

	PromptBuilder builder = new PromptBuilder()
	    .with("roster", material.roster())
	    .with("now", SimulationTime.now().format(STORY_FULL_FORMAT))
	    .with("span", material.span())
	    .with("diary", newDiaryText)
	    .with("conversations", newConversationText);

	if (hasPrevious) {
	    // Only the recent passages plus a summary of the rest, never the
	    // whole accumulated story - see PASSAGES_KEPT_VERBATIM.
	    builder
		.with("storySoFar", previous.getPromptContext())
		.setPrompt(SmallvilleConfig.getPrompts().getStory().getContinuation());
	} else {
	    builder.setPrompt(SmallvilleConfig.getPrompts().getStory().getFirst());
	}

	String passage;
	try {
	    passage = chat.sendChat(builder.build().labelled("story"), 0.7).trim();
	} catch (Exception e) {
	    LOG.error("Failed to generate story", e);
	    throw new SmallvilleException("Could not generate the story right now");
	}

	previous.getPassages().add(passage);
	previous.setAsOf(SimulationTime.now());
	compactIfNeeded(previous);
	storyStore.save(previous);

	StoryResponse result = new StoryResponse();
	result.setExists(true);
	result.setUpdated(true);
	result.setStory(previous.getStory());
	result.setAsOfDate(previous.getAsOf().format(STORY_DATE_FORMAT));
	result.setAsOfTime(previous.getAsOf().format(STORY_TIME_FORMAT));
	result.setMinutesSinceUpdate(0);
	return result;
    }

    /**
     * How far the story is already caught up.
     */
    private LocalDateTime storyCursor(StorySnapshot previous, boolean hasPrevious) {
	if (!hasPrevious || previous.getAsOf() == null) {
	    return LocalDateTime.MIN;
	}

	// The cursor is simulated time, and the simulated clock restarts at the
	// wall clock on every boot. If it now sits behind the last recap, a
	// cursor in the future would make every future regeneration report "no
	// new developments" forever.
	if (SimulationTime.now().isBefore(previous.getAsOf())) {
	    LOG.warn("Simulation clock is behind the last story update, recapping everything still on record");
	    return LocalDateTime.MIN;
	}

	return previous.getAsOf();
    }

    /**
     * Folds older passages into the rolling summary once too many have built
     * up, so the continuation prompt stays a bounded size no matter how long
     * the town runs. The passages themselves are kept - only the prompt is
     * shortened, not the story the dashboard displays.
     */
    private void compactIfNeeded(StorySnapshot snapshot) {
	int foldThrough = snapshot.getPassages().size() - PASSAGES_KEPT_VERBATIM;

	if (foldThrough <= snapshot.getSummarisedThrough()) {
	    return;
	}

	List<String> toFold = new ArrayList<>(
		snapshot.getPassages().subList(snapshot.getSummarisedThrough(), foldThrough));

	PromptRequest request = new PromptBuilder()
	    .with("summary", snapshot.getSummary())
	    .with("passages", String.join("\n\n", toFold))
	    .setPrompt(SmallvilleConfig.getPrompts().getStory().getCompact())
	    .build();

	try {
	    snapshot.setSummary(chat.sendChat(request.labelled("storyCompact"), 0.3).trim());
	    snapshot.setSummarisedThrough(foldThrough);
	    LOG.info("Compacted " + toFold.size() + " story passages into the running summary");
	} catch (Exception e) {
	    // Not fatal. The story is complete on disk either way; the next
	    // regeneration just carries a slightly longer prompt and retries.
	    LOG.error("Failed to compact the story summary, leaving it for next time", e);
	}
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

    /*
     * Adding and removing agents and locations does not take the simulation
     * lock, for the same reason characteristic edits don't: these are single
     * operations on concurrent collections, and a tick already tolerates the
     * world changing underneath it - updateState snapshots the agent list up
     * front and re-checks each agent still exists before updating it.
     *
     * The create-agent form can add a location and an agent in one submit, so
     * locking these meant waiting out an in-flight agent update twice over.
     */
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
	// Otherwise the cooldown entry keeps the deleted location's name alive
	// forever, and a location later recreated under the same name inherits
	// a stale conversation timestamp.
	lastConversationAt.remove(name);
    }

    // Wipes conversations, every agent's diary, and the generated story -
    // agents, locations, and simulation timing/state are left untouched.
    public void resetSimulationData() {
	exclusively(() -> {
	    world.resetSimulationData();
	    storyStore.clear();
	    worldStore.clear();
	    // Without this the cooldown survives the reset and the town stays
	    // silent for up to an hour of simulated time afterwards, which
	    // reads as the reset having broken conversations.
	    lastConversationAt.clear();
	});
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

    /*
     * Characteristic edits deliberately do NOT take the simulation lock.
     *
     * The lock is held for a whole agent update - several sequential LLM calls,
     * routinely 30-45 seconds - so taking it made a single list insert wait
     * that long for no benefit. There is nothing to protect against: the tick
     * never creates or removes Characteristics (clearDiary explicitly keeps
     * them), the memory stream is a CopyOnWriteArrayList so add and remove are
     * atomic, and removal is by object identity rather than by index, so a
     * concurrent append cannot make it delete the wrong entry.
     */
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

	exclusively(SimulationTime::update);

	// Locked per agent rather than across the whole tick. A tick is several
	// sequential LLM calls per agent and can run for minutes; holding the
	// lock throughout would make Reset or Add Agent block for that long. The
	// agent update is the meaningful atomic unit anyway, and releasing
	// between agents lets a control action land cleanly in the gap.
	List<String> names = world.getAgents().stream().map(Agent::getFullName).collect(Collectors.toList());

	for (String name : names) {
	    exclusively(() -> {
		// May have been deleted, or the whole world reset, while an
		// earlier agent in this same tick was still being updated.
		Optional<Agent> agent = world.getAgent(name);

		if (agent.isEmpty()) {
		    LOG.info("Agent " + name + " disappeared mid-tick, skipping");
		    return;
		}

		try {
		    prompts.updateAgent(agent.get());
		} catch (Exception e) {
		    LOG.error("Failed to update agent " + name + ", skipping for this tick", e);
		}
	    });
	}

	exclusively(this::triggerGroupConversations);
    }

    private static final int MAX_GROUP_PARTICIPANTS = 5;

    /**
     * Nothing else in the update pipeline notices when several agents end up in
     * the same location - conversations only ever happen if something
     * explicitly feeds an agent a reactable observation. This is that missing
     * nudge.
     * <p>
     * Whether a conversation actually happens is decided by the agents rather
     * than by the clock. Previously any two co-located agents talked on a fixed
     * hourly cooldown, forever, whatever else was true - which in a town where
     * people spend all day in the same place is a metronome, not a social
     * model. Now a pair is more likely to talk if they have never met, if
     * something has happened to either of them since they last spoke, and if
     * they get on; and agents who are asleep do not talk at all.
     */
    private void triggerGroupConversations() {
	Map<String, List<Agent>> byLocation = new HashMap<>();

	for (Agent agent : world.getAgents()) {
	    if (agent.getLocation() == null || isAsleep(agent)) {
		continue;
	    }

	    byLocation.computeIfAbsent(agent.getLocation().getFullPath(), k -> new ArrayList<>()).add(agent);
	}

	for (Map.Entry<String, List<Agent>> entry : byLocation.entrySet()) {
	    String location = entry.getKey();
	    List<Agent> here = entry.getValue();
	    LocalDateTime now = SimulationTime.now();

	    if (here.size() < 2) {
		continue;
	    }

	    // A floor, not the mechanism: it only stops the same room producing
	    // a fresh conversation on consecutive ticks.
	    LocalDateTime lastTime = lastConversationAt.get(location);

	    if (lastTime != null && Duration.between(lastTime, now).compareTo(CONVERSATION_FLOOR) < 0) {
		continue;
	    }

	    List<Agent> participants = chooseParticipants(here);

	    if (participants.isEmpty()) {
		continue;
	    }

	    lastConversationAt.put(location, now);

	    try {
		prompts.triggerGroupConversation(participants, "Everyone listed is gathered here together right now.");
	    } catch (Exception e) {
		LOG.error("Failed to trigger group conversation at " + location, e);
	    }
	}
    }

    /**
     * Picks who talks, or returns empty if nobody feels like it this tick.
     */
    private List<Agent> chooseParticipants(List<Agent> here) {
	List<Agent> shuffled = new ArrayList<>(here);
	Collections.shuffle(shuffled, random);

	Agent initiator = shuffled.get(0);
	Agent partner = null;
	double bestUrge = -1;

	// The initiator talks to whoever they most have reason to talk to,
	// rather than to whoever the iteration order happened to reach first.
	for (int i = 1; i < shuffled.size(); i++) {
	    double urge = conversationUrge(initiator, shuffled.get(i));

	    if (urge > bestUrge) {
		bestUrge = urge;
		partner = shuffled.get(i);
	    }
	}

	if (partner == null || random.nextDouble() >= bestUrge) {
	    return List.of();
	}

	List<Agent> selected = new ArrayList<>(List.of(initiator, partner));

	for (Agent other : shuffled) {
	    if (selected.size() >= MAX_GROUP_PARTICIPANTS) {
		break;
	    }

	    if (selected.contains(other)) {
		continue;
	    }

	    // Someone joins a conversation in progress based on how they feel
	    // about the people already in it.
	    double urge = selected
		.stream()
		.mapToDouble(member -> conversationUrge(member, other))
		.average()
		.orElse(0);

	    if (random.nextDouble() < urge) {
		selected.add(other);
	    }
	}

	return selected;
    }

    /**
     * How strongly these two would start talking right now, as a probability.
     */
    private double conversationUrge(Agent a, Agent b) {
	Relationship relationship = world.getRelationships().get(a.getFullName(), b.getFullName());

	if (!relationship.haveMet()) {
	    // Strangers in the same room almost always introduce themselves.
	    return 0.9;
	}

	// Warmth pulls both ways: people who get on talk more, people who don't
	// avoid each other.
	double warmth = (relationship.affinity() + 1) / 2;
	double novelty = noveltySince(relationship.lastSpokeAt(), a, b);
	double urge = 0.1 + (0.45 * novelty) + (0.35 * warmth);

	return Math.max(0.02, Math.min(0.95, urge));
    }

    /**
     * How much has happened to either agent since they last spoke, saturating
     * at a handful of new memories. Two people who have just talked and done
     * nothing since have little reason to start again.
     */
    private double noveltySince(LocalDateTime lastSpokeAt, Agent a, Agent b) {
	if (lastSpokeAt == null) {
	    return 1;
	}

	long fresh = Stream
	    .concat(a.getMemoryStream().getMemories().stream(), b.getMemoryStream().getMemories().stream())
	    .filter(memory -> memory instanceof TemporalMemory)
	    .filter(memory -> ((TemporalMemory) memory).getTime().isAfter(lastSpokeAt))
	    .count();

	return Math.min(1, fresh / 6.0);
    }

    /**
     * Whether the agent is asleep, judged from the activity text the model
     * wrote. Nothing in the model marks sleep explicitly, and without this
     * check agents hold conversations in the middle of the night.
     */
    private static boolean isAsleep(Agent agent) {
	String activity = agent.getCurrentActivity();

	if (activity == null) {
	    return false;
	}

	String lower = activity.toLowerCase();

	return lower.contains("sleep") || lower.contains("asleep") || lower.contains("dozing")
		|| lower.contains("napping") || lower.contains("in bed");
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

    public void setState(String location, String state) {
	exclusively(() -> world.setState(location, state));
    }

    private static final int MAX_STANDALONE_STREAMS = 100;

    /**
     * Standalone memory streams for the client libraries, which use the server
     * as a memory store without running a simulation.
     * <p>
     * Bounded and access-ordered: these are created by an endpoint with no
     * matching delete, so an unbounded map here is a leak that grows for as
     * long as the server runs.
     */
    private final Map<UUID, MemoryStream> memories = Collections
	.synchronizedMap(new LinkedHashMap<UUID, MemoryStream>(16, 0.75f, true) {
	    private static final long serialVersionUID = 1L;

	    @Override
	    protected boolean removeEldestEntry(Map.Entry<UUID, MemoryStream> eldest) {
		return size() > MAX_STANDALONE_STREAMS;
	    }
	});

    public UUID createMemoryStream() {
	UUID uuid = UUID.randomUUID();
	memories.put(uuid, new MemoryStream());
	return uuid;
    }

    public List<String> getMemories(UUID uuid, String query) {
	MemoryStream stream = memories.get(uuid);

	if (stream == null) {
	    throw new SmallvilleException("No memory stream with id " + uuid);
	}

	return stream
	    .getRelevantMemories(query)
	    .stream()
	    .map(memory -> memory.getDescription())
	    .collect(Collectors.toList());
    }
}
