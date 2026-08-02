package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Agent;
import io.github.nickm980.smallville.entities.Conversation;
import io.github.nickm980.smallville.entities.Location;
import io.github.nickm980.smallville.llm.LLM;
import io.github.nickm980.smallville.memory.Characteristic;
import io.github.nickm980.smallville.prompts.ChatService;
import io.github.nickm980.smallville.prompts.PromptRequest;

/**
 * Dialogue has to survive whatever shape the model answers in.
 * <p>
 * Asked for "Name: line" dialogue, the model would sometimes write a narrative
 * scene instead, with the speech embedded in prose. That parsed to zero lines,
 * the conversation was rejected as empty, and an expensive call was thrown
 * away - which is what left four agents in a Walmart with no conversation while
 * their diaries claimed otherwise.
 */
public class ConversationParsingTest {

    /** Returns a canned response, standing in for the model. */
    private static LLM replying(String response) {
	return new LLM() {
	    @Override
	    public String sendChat(PromptRequest prompt, double temperature) {
		return response;
	    }
	};
    }

    private static World townWith(Agent... agents) {
	World world = new World();
	Location walmart = new Location("Walmart");
	world.create(walmart);

	for (Agent agent : agents) {
	    world.create(agent);
	}

	return world;
    }

    private static Agent agent(String name, World world) {
	return new Agent(name, List.of(new Characteristic(name + " is a resident")), "idle",
		world.getLocation("Walmart").orElseThrow());
    }

    private static Conversation groupConversationFrom(String response) {
	World world = new World();
	world.create(new Location("Walmart"));

	Agent joan = agent("Joan", world);
	Agent paul = agent("Paul", world);
	world.create(joan);
	world.create(paul);

	return new ChatService(world, replying(response)).getGroupConversation(joan, List.of(paul), "they are here");
    }

    @Test
    public void dialogue_is_read_from_the_json_shape() {
	Conversation conversation = groupConversationFrom("""
		{"lines": [
		  {"speaker": "Joan", "text": "Do you know where the cat food is?"},
		  {"speaker": "Paul", "text": "Aisle six, past the cleaning stuff."}
		]}
		""");

	assertEquals(2, conversation.size());
	assertEquals("Joan", conversation.getDialog().get(0).getName());
	assertEquals("Aisle six, past the cleaning stuff.", conversation.getDialog().get(1).getMessage());
    }

    @Test
    public void json_wrapped_in_a_code_fence_still_parses() {
	Conversation conversation = groupConversationFrom("""
		```json
		{"lines": [{"speaker": "Joan", "text": "Evening."}]}
		```
		""");

	assertEquals(1, conversation.size());
    }

    @Test
    public void the_old_line_format_still_parses_as_a_fallback() {
	Conversation conversation = groupConversationFrom("""
		Joan: Do you know where the cat food is?
		Paul: Aisle six, past the cleaning stuff.
		""");

	assertEquals(2, conversation.size());
	assertEquals("Joan", conversation.getDialog().get(0).getName());
    }

    @Test
    public void a_narrative_answer_yields_no_dialogue_rather_than_nonsense() {
	// The actual failing response shape. There is nothing sensible to
	// extract here, so the caller must be able to see that and skip.
	Conversation conversation = groupConversationFrom("""
		The four of them sat around a folding table in the church basement, the
		fluorescent lights humming overhead. For a long moment, nobody spoke.
		""");

	assertTrue(conversation.size() <= 1,
		"narrative prose should not be mistaken for a full conversation, got " + conversation.size());
    }

    @Test
    public void entries_missing_a_speaker_or_text_are_dropped() {
	Conversation conversation = groupConversationFrom("""
		{"lines": [
		  {"speaker": "Joan", "text": "Evening."},
		  {"speaker": "", "text": "orphaned line"},
		  {"speaker": "Paul", "text": ""}
		]}
		""");

	assertEquals(1, conversation.size());
    }
}
