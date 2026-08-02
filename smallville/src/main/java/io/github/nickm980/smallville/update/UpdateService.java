package io.github.nickm980.smallville.update;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.entities.SimulationTime;
import io.github.nickm980.smallville.events.EventBus;
import io.github.nickm980.smallville.events.agent.AgentUpdateEvent;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.prompts.ChatService;

/**
 * 
 * This class represents a service that handles the updating of agents
 * 
 * It provides methods for updating an agent's state based on different types of
 * observations as well as a method for asking a question to an agent
 */
public class UpdateService {

    private final World world;
    private final ChatService chatService;
    private final Logger LOG = LoggerFactory.getLogger(UpdateService.class);
    private final EventBus events = EventBus.getEventBus();
    
    public UpdateService(LLM chat, World world) {
	this.world = world;
	this.chatService = new ChatService(world, chat);
    }

    /**
     * Update the agents future plans, memory weights, and current activity /
     * location / emoji. Everything that needs to be done when updating an agent
     * automatically is done here
     * <p>
     * 
     * @param agent
     */
    public void updateAgent(Agent agent) {
	LOG
	    .info("Starting update for " + agent.getFullName() + " at time "
		    + SimulationTime
			.now()
			.format(DateTimeFormatter.ofPattern(SmallvilleConfig.getConfig().getTimeFormat())));
	
	Location oldLocation = agent.getLocation();

	ensureTraits(agent);

	AgentUpdate update = new UpdateMemoryWeights()
	    .setNext(new UpdatePlans())
	    .setNext(new UpdateCurrentActivity())
	    .setNext(new UpdateConversation())
	    .setNext(new UpdateReflection());

	update.start(chatService, world, agent, new UpdateInfo());

	events.postEvent(new AgentUpdateEvent(agent, oldLocation, agent.getLocation()));
	LOG.info("Agent updated");
    }

    public void react(Agent agent, String observation) {
	LOG.info("Starting reaction for " + agent.getFullName());

	UpdateInfo info = new UpdateInfo();
	info.setObservation(observation);
	Location oldLocation = agent.getLocation();

	AgentUpdate update = new UpdatePlans().setNext(new UpdateConversation());

	update.start(chatService, world, agent, info);

	if (info.isPlansUpdated()) {
	    update = new UpdateCurrentActivity();
	    update.start(chatService, world, agent, info);
	}

	events.postEvent(new AgentUpdateEvent(agent, oldLocation, agent.getLocation()));
	LOG.info("Agent updated");
    }

    /**
     * Bypasses the normal AgentUpdate chain entirely - the proximity trigger
     * that calls this already knows exactly who's participating, so there's
     * no need to route through UpdatePlans/UpdateConversation's single-name
     * NLP extraction (that pipeline stays reserved for the organic,
     * agent-generated-observation case). Mirrors what UpdateConversation
     * does for the pairwise case, generalized to N participants.
     */
    public void triggerGroupConversation(List<Agent> participants, String topic) {
	LOG.info("Starting group conversation with " + participants.size() + " participants");

	Agent initiator = participants.get(0);
	List<Agent> others = participants.subList(1, participants.size());

	Conversation conversation = chatService.getGroupConversation(initiator, others, topic);

	if (conversation.size() == 0) {
	    // One retry, then give up quietly. This used to propagate World's
	    // "Cannot have an empty conversation" out of the tick, which logged
	    // a stack trace and threw away a call that had already been paid
	    // for. Nothing is broken when it happens - the group simply doesn't
	    // talk this time.
	    LOG.warn("No dialogue came back for the group at " + initiator.getLocation().getFullPath() + ", retrying");
	    conversation = chatService.getGroupConversation(initiator, others, topic);
	}

	if (conversation.size() == 0) {
	    LOG.warn("Still no dialogue on retry, skipping this conversation");
	    return;
	}

	// Built per participant rather than once and shared. Beyond needing each
	// agent's own point of view, the memories must be distinct objects:
	// UpdateMemoryWeights calls setImportance on them, so sharing instances
	// would let one agent's weighting silently rewrite everyone else's.
	for (Agent participant : participants) {
	    List<Observation> memories = conversation
		.getDialog()
		.stream()
		.map(dialog -> {
		    Observation dialogMemory = new Observation(dialog.asMemoryFor(participant.getFullName()));
		    dialogMemory.setDialog(true);
		    return dialogMemory;
		})
		.collect(Collectors.toList());

	    participant.getMemoryStream().addAll(memories);
	}

	world.create(conversation);
	recordRelationships(conversation);
    }

    /**
     * Updates how everyone who just talked feels about each other.
     * <p>
     * Costs one extra, deliberately small model call per conversation. Failure
     * is not fatal - familiarity still increases, the pair is simply recorded
     * as having had a neutral exchange, because knowing that they spoke at all
     * is most of the value.
     */
    private void recordRelationships(Conversation conversation) {
	double shift = 0;

	try {
	    shift = chatService.classifyConversationTone(conversation);
	} catch (Exception e) {
	    LOG.warn("Could not judge the tone of a conversation, recording it as neutral: " + e.getMessage());
	}

	world.getRelationships().recordConversation(conversation.getParticipants(), shift, conversation.getTime());
    }

    /**
     *
     * Asks a question to an agent and returns the response.
     * 
     * @param agent    The agent to ask the question to
     * @param question The question to ask
     * @return The response from the agent
     */
    public String ask(Agent agent, String question) {
	return chatService.ask(agent, question);
    }

    public String createTraitsWithCharacteristics(Agent agent) {
	return chatService.createTraits(agent);
    }

    /**
     * Fills in an agent's trait summary the first time they are updated.
     * <p>
     * Generating this is a model call, and doing it while creating the agent
     * made adding someone to the town block on a network round trip for a
     * value that only decorates one line of one prompt. Doing it here means
     * creation is instant and the simulation catches up on its own schedule.
     * <p>
     * Also self-healing: an agent restored from a save written before traits
     * existed, or one whose trait call failed previously, picks them up on the
     * next tick rather than going without forever.
     */
    private void ensureTraits(Agent agent) {
	if (agent.getTraits() != null && !agent.getTraits().isBlank()) {
	    return;
	}

	try {
	    agent.setTraits(createTraitsWithCharacteristics(agent));
	    LOG.info("Generated trait summary for " + agent.getFullName());
	} catch (Exception e) {
	    // Not fatal - the prompt renders the traits line empty and the
	    // characteristics below it still carry the real detail. Retried on
	    // the next tick.
	    LOG.warn("Could not generate traits for " + agent.getFullName() + ", will retry next tick");
	}
    }
}
