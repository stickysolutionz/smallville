package io.github.nickm980.smallville.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.entities.SimulationTime;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Includes plans, observations, and characteristics
 */
public class MemoryStream {
    /**
     * Copy-on-write because the simulation thread appends here throughout a
     * tick while HTTP threads iterate it to render diaries and build the
     * story. Snapshot iteration is what matters; the copy cost on append is
     * irrelevant next to the LLM call that produced the memory.
     */
    private List<Memory> memories;

    public MemoryStream() {
	this.memories = new CopyOnWriteArrayList<Memory>();
    }

    public void prunePlans(PlanType type) {
	memories.removeIf(memory -> memory instanceof Plan && ((Plan) memory).getType() == type);
    }

    public List<Memory> getRelevantMemories(String query) {
	int defaultMinImportance = 0;

	return getRelevantMemories(query, defaultMinImportance);
    }

    public List<Memory> getRelevantMemories(String query, int minImportance) {
	return getRelevantMemories(query, minImportance, SmallvilleConfig.getConfig().getRetrievalCount());
    }

    /**
     * Prunes the weaker, less poingnant memories and returns the strongest ones
     * based on observations and updated plans.
     * <p>
     * Will run several comparisons. First, will extract names from the query and
     * compare the token embeddings of the names to each memory. Then will do the
     * same for the full query.
     * 
     * @return
     */
    /**
     * The {@code limit} highest scoring memories for a query, strongest first.
     * <p>
     * Scores are carried alongside their memory rather than used as map keys.
     * The previous implementation collected candidates into a
     * {@code Map<Double, Integer>} keyed by score, so any two memories that
     * happened to score identically destroyed one another before ranking even
     * began - and identical scores are common, since unweighted memories with
     * no relevance to the query all score exactly the same. It also returned
     * every memory unranked whenever the stream held three or fewer
     * candidates, and was hardcoded to three otherwise.
     */
    public List<Memory> getRelevantMemories(String query, int minImportance, int limit) {
	return memories
	    .stream()
	    .filter(memory -> memory.getImportance() >= minImportance)
	    // Scored once per memory: getScore runs a BERT embedding comparison,
	    // so it must not be called again from inside the sort comparator.
	    .map(memory -> Map.entry(memory, memory.getScore(query)))
	    .sorted(Map.Entry.<Memory, Double>comparingByValue().reversed())
	    .limit(Math.max(0, limit))
	    .map(Map.Entry::getKey)
	    .collect(Collectors.toList());
    }

    public List<Memory> getUnweightedMemories() {
	return memories.stream().filter(memory -> {
	    return memory.getImportance() == 0 && !(memory instanceof Plan);
	}).collect(Collectors.toList());
    }

    /**
     * When this agent last reflected. Everything before it has already been
     * thought about.
     */
    private LocalDateTime lastReflectedAt;

    public LocalDateTime getLastReflectedAt() {
	return lastReflectedAt;
    }

    public void setLastReflectedAt(LocalDateTime lastReflectedAt) {
	this.lastReflectedAt = lastReflectedAt;
    }

    public void markReflected() {
	this.lastReflectedAt = SimulationTime.now();
    }

    /**
     * How much has happened, by weight, since the agent last reflected.
     * <p>
     * This used to sum importance across a sliding window of recent memories,
     * which grows without limit as an agent accumulates them. A threshold tuned
     * against one run was wrong by the next: a cutoff that fired on 9% of
     * updates fired on 57% a day later, because the median sum had climbed from
     * 325 to 706. No fixed number survives that - the measure itself has to
     * reset.
     * <p>
     * Counting only what has arrived since the last reflection is
     * self-limiting: reflecting drops it back to nothing and it builds again.
     */
    public double importanceSinceLastReflection() {
	return memories
	    .stream()
	    .filter(memory -> !(memory instanceof Plan))
	    .filter(memory -> memory instanceof TemporalMemory)
	    .filter(memory -> lastReflectedAt == null
		    || ((TemporalMemory) memory).getTime().isAfter(lastReflectedAt))
	    .mapToDouble(Memory::getImportance)
	    .sum();
    }

    public List<Memory> getRecentMemories() {
	List<Memory> result = memories
	    .stream()
	    .filter(memory -> memory.getRecency() > .4 && !(memory instanceof Plan))
	    .collect(Collectors.toList());
	return result;
    }

    public List<Memory> getMemories() {
	return memories;
    }

    public List<Observation> getObservations() {
	return filterMemoriesByType(Observation.class).collect(Collectors.toList());
    }

    public List<Characteristic> getCharacteristics() {
	return filterMemoriesByType(Characteristic.class).collect(Collectors.toList());
    }

    public List<Plan> getPlans() {
	return filterMemoriesByType(Plan.class).sorted(new TemporalMemory.TemporalComparator())
		.collect(Collectors.toList());
    }

    private <T extends Memory> Stream<T> filterMemoriesByType(Class<T> memoryType) {
	return memories.stream().filter(memoryType::isInstance).map(memoryType::cast);
    }

    public void addAll(List<? extends Memory> memories) {
	this.memories.addAll(memories);
    }

    public void add(Memory memory) {
	this.memories.add(memory);
    }

    public boolean remove(Memory memory) {
	return this.memories.remove(memory);
    }

    /**
     * Wipes accumulated diary history (observations, plans, reflections)
     * while keeping the agent's Characteristics, since those define who
     * the agent is rather than what's happened to them so far.
     */
    public void clearDiary() {
	memories.removeIf(memory -> !(memory instanceof Characteristic));
    }

    public void setPlans(List<Plan> plans, PlanType type) {
	List<Plan> removed = getPlans(type);
	memories.removeAll(removed);
	memories.addAll(plans);
    }

    public List<? extends TemporalMemory> sortByTime(List<? extends TemporalMemory> mems) {
	return mems.stream().sorted(new Comparator<TemporalMemory>() {
	    @Override
	    public int compare(TemporalMemory o1, TemporalMemory o2) {
		return o1.getTime().compareTo(o2.getTime());
	    }
	}).collect(Collectors.toList());
    }

    public List<Plan> getPlans(PlanType term) {
	return getPlans().stream().filter(plan -> plan.getType() == term).collect(Collectors.toList());
    }

    public Observation getLastObservation() {
	List<Observation> observations = getObservations();

	if (observations == null || observations.isEmpty()) {
	    return new Observation("");
	}

	return observations.get(observations.size() - 1);
    }
}
