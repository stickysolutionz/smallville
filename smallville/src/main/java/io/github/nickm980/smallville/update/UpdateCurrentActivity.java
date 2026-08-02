package io.github.nickm980.smallville.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.github.nickm980.smallville.Util;
import io.github.nickm980.smallville.World;
import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.memory.Observation;
import io.github.nickm980.smallville.memory.Plan;
import io.github.nickm980.smallville.memory.PlanType;
import io.github.nickm980.smallville.prompts.Prompts;
import io.github.nickm980.smallville.prompts.dto.CurrentActivity;

public class UpdateCurrentActivity extends AgentUpdate {

    @Override
    public boolean update(Prompts service, World world, Agent agent, UpdateInfo info) {
	LOG.info("[Activity] Updating current activity and emoji");

	CurrentActivity activity = service.getCurrentActivity(agent);
	LOG.debug(activity.getLocation());

	// The memory written at the end of this describes what the agent was
	// doing BEFORE this update, so it belongs to where they were doing it.
	// Reading the location afterwards attributed the previous activity to
	// the new place: an agent walking from the Cottage to Walmart
	// remembered making coffee at Walmart.
	Location whereItHappened = agent.getLocation();

	if (activity.getActivity() != null && !activity.getActivity().isBlank()) {
	    agent.setCurrentActivity(activity.getActivity());
	} else {
	    LOG.warn("[Activity] Model response didn't include an activity. Keeping previous activity for "
		    + agent.getFullName());
	}

	agent.setCurrentEmoji(activity.getEmoji());

	String locationName = activity.getLocation();
	Optional<Location> location = world.getLocation(locationName);

	if (location.isEmpty() && locationName != null && locationName.contains(":")) {
	    location = world.getLocation(locationName.split(":")[0].trim());
	}

	if (location.isPresent()) {
	    agent.setLocation(location.get());
	} else {
	    LOG.warn("[Activity] Model returned unknown location '" + locationName + "'. Keeping agent at "
		    + agent.getLocation().getFullPath());
	}

	remember(agent, world, whereItHappened, activity.getLastActivity());
	markGoalsReached(agent);

	return next(service, world, agent, info);
    }

    /**
     * Files what the agent was doing, unless it is the same thing they were
     * already recorded doing.
     * <p>
     * A plan now covers several ticks on purpose, so an agent legitimately
     * spends three or four of them on one activity. Writing a near-identical
     * memory each time fills the stream with duplicates that then get retrieved
     * together and crowd everything else out - an overnight run recorded "lying
     * on the hallway floor, breathing slowly" three times in forty-five
     * minutes.
     */
    private static void remember(Agent agent, World world, Location where, String activity) {
	String record = recordOf(activity, where, world, agent);
	String previous = agent.getMemoryStream().getLastObservation().getDescription();

	if (record.equalsIgnoreCase(previous)) {
	    return;
	}

	agent.getMemoryStream().add(new Observation(record));
    }

    /**
     * Marks any daily goal whose location the agent is now standing in as
     * addressed.
     * <p>
     * Deliberately crude - being somewhere is not the same as having done the
     * thing. But without any notion of a goal being met, "pick up cat food"
     * is restated every hour for the rest of the day, because nothing records
     * having gone to the shop.
     */
    private static void markGoalsReached(Agent agent) {
	if (agent.getLocation() == null) {
	    return;
	}

	String here = agent.getLocation().getFullPath();

	for (Plan plan : agent.getMemoryStream().getPlans(PlanType.LONG_TERM)) {
	    if (plan.isAddressed() || plan.getLocation() == null) {
		continue;
	    }

	    // Goals name a location which may be more or less specific than
	    // where the agent ended up, so either containing the other counts.
	    String there = plan.getLocation();

	    if (here.equalsIgnoreCase(there) || here.toLowerCase().startsWith(there.toLowerCase())
		    || there.toLowerCase().startsWith(here.toLowerCase())) {
		plan.setAddressed(true);
	    }
	}
    }

    /**
     * Builds what the agent will remember of this moment.
     * <p>
     * This used to be the model's own activity text, past-tensed and filed
     * straight into the memory stream. Nothing checked it against the
     * simulation, so a plan saying "join in the conversation and share a joke"
     * became a first-hand memory of a conversation that never happened - and
     * from there fed reflection, retrieval and the story. Two agents in the
     * same aisle ended up with contradictory recollections of the same minute.
     * <p>
     * The activity half is still the model's, because that is where the texture
     * lives, but the prompt now forbids it referring to anyone else. Who was
     * actually there comes from the world, so the social half of the memory is
     * something the simulation can stand behind.
     */
    private static String recordOf(String activity, Location location, World world, Agent agent) {
	String where = location == null ? null : location.getFullPath();
	String what = activity == null ? "" : activity.trim();

	StringBuilder record = new StringBuilder(what.isEmpty() ? "Spent time" : what);

	if (where != null) {
	    record.append(what.toLowerCase().contains(where.toLowerCase()) ? "" : " at " + where);
	}

	// What the others were doing, not just that they were there. This is the
	// only way anybody learns anything about anybody: people are worked out
	// by watching them, not looked up. Before this, an agent could stand
	// beside somebody for hours and come away knowing nothing, while the
	// conversation prompt quietly handed over that person's whole inner life
	// instead.
	//
	// Note this is deliberately NOT fed into the prompt that decides what to
	// do next - putting other people's activities there made agents copy
	// each other. Seeing what somebody did belongs in memory; it has no
	// business steering your own choice.
	List<String> witnessed = whatOthersWereDoing(agent, location, world);

	if (!witnessed.isEmpty()) {
	    record.append(", where ").append(join(witnessed));
	}

	return record.toString();
    }

    /**
     * Up to a few of the people present and what each appeared to be doing.
     */
    private static List<String> whatOthersWereDoing(Agent agent, Location location, World world) {
	List<String> seen = new ArrayList<>();

	for (String name : othersAt(agent, location, world)) {
	    Agent other = world.getAgent(name).orElse(null);
	    String doing = other == null ? null : other.getCurrentActivity();

	    seen.add(doing == null || doing.isBlank() ? name + " was there" : name + " was " + doing);

	    // A crowded room would otherwise bury the agent's own memory of
	    // themselves under everyone else's business.
	    if (seen.size() == 3) {
		break;
	    }
	}

	return seen;
    }

    private static List<String> othersAt(Agent agent, Location location, World world) {
	if (location == null) {
	    return List.of();
	}

	String here = location.getFullPath();

	return world
	    .getAgents()
	    .stream()
	    .filter(other -> !other.getFullName().equals(agent.getFullName()))
	    .filter(other -> other.getLocation() != null && here.equals(other.getLocation().getFullPath()))
	    .map(Agent::getFullName)
	    .sorted()
	    .collect(Collectors.toList());
    }

    private static String join(List<String> names) {
	if (names.size() == 1) {
	    return names.get(0);
	}

	return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
