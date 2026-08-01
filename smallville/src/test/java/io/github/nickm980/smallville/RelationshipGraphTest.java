package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.relationships.Relationship;
import io.github.nickm980.smallville.relationships.RelationshipGraph;

public class RelationshipGraphTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Test
    public void two_people_who_have_not_met_are_strangers() {
	RelationshipGraph graph = new RelationshipGraph();
	Relationship relationship = graph.get("Maria Lopez", "Klaus Mueller");

	assertFalse(relationship.haveMet());
	assertEquals(0, relationship.familiarity());
    }

    @Test
    public void a_relationship_is_one_fact_about_two_people() {
	// Order of lookup must not matter, or the pair accumulates two
	// independent histories that drift apart.
	RelationshipGraph graph = new RelationshipGraph();
	graph.recordConversation(List.of("Maria Lopez", "Klaus Mueller"), 0.15, NOON);

	assertEquals(1, graph.get("Maria Lopez", "Klaus Mueller").familiarity());
	assertEquals(1, graph.get("Klaus Mueller", "Maria Lopez").familiarity());
    }

    @Test
    public void names_containing_spaces_are_handled() {
	// Agent names are full names. A graph keyed on a joined string has to
	// pick a delimiter, and every delimiter is a character a name might
	// contain - so the key is a record instead.
	RelationshipGraph graph = new RelationshipGraph();
	graph.recordConversation(List.of("Maria Lopez", "Klaus Mueller"), 0, NOON);

	graph.removeAgent("Maria Lopez");

	assertFalse(graph.get("Maria Lopez", "Klaus Mueller").haveMet());
	assertTrue(graph.asMap().isEmpty());
    }

    @Test
    public void a_group_conversation_links_every_pair_in_it() {
	RelationshipGraph graph = new RelationshipGraph();
	graph.recordConversation(List.of("Maria Lopez", "Klaus Mueller", "Bill Ward"), 0.15, NOON);

	assertEquals(3, graph.asMap().size(), "three people should produce three pairings");
	assertTrue(graph.get("Maria Lopez", "Bill Ward").haveMet());
	assertTrue(graph.get("Klaus Mueller", "Bill Ward").haveMet());
    }

    @Test
    public void repeated_warm_conversations_build_affinity_without_running_away() {
	RelationshipGraph graph = new RelationshipGraph();

	for (int i = 0; i < 50; i++) {
	    graph.recordConversation(List.of("Maria Lopez", "Klaus Mueller"), 0.15, NOON);
	}

	Relationship relationship = graph.get("Maria Lopez", "Klaus Mueller");

	assertEquals(50, relationship.familiarity());
	assertTrue(relationship.affinity() <= 1.0, "affinity must stay bounded, was " + relationship.affinity());
	assertTrue(relationship.affinity() > 0.5, "warm exchanges should accumulate");
    }

    @Test
    public void tense_conversations_push_affinity_negative_and_stay_bounded() {
	RelationshipGraph graph = new RelationshipGraph();

	for (int i = 0; i < 50; i++) {
	    graph.recordConversation(List.of("Maria Lopez", "Klaus Mueller"), -0.15, NOON);
	}

	assertTrue(graph.get("Maria Lopez", "Klaus Mueller").affinity() >= -1.0);
	assertTrue(graph.get("Maria Lopez", "Klaus Mueller").affinity() < -0.5);
    }

    @Test
    public void the_description_reflects_how_the_pair_actually_stands() {
	RelationshipGraph graph = new RelationshipGraph();

	assertTrue(graph.get("A", "B").describe("A", "B").contains("not spoken before"));

	for (int i = 0; i < 10; i++) {
	    graph.recordConversation(List.of("A", "B"), 0.15, NOON);
	}

	String warm = graph.get("A", "B").describe("A", "B");

	assertTrue(warm.contains("know each other well"), warm);
	assertTrue(warm.contains("get on well"), warm);
    }

    @Test
    public void the_time_they_last_spoke_is_kept() {
	RelationshipGraph graph = new RelationshipGraph();
	graph.recordConversation(List.of("A", "B"), 0, NOON);

	assertEquals(NOON, graph.get("A", "B").lastSpokeAt());
    }
}
