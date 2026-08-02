package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.config.SmallvilleConfig;
import io.github.nickm980.smallville.config.prompts.Prompts;

/**
 * prompts.yaml is loaded lazily on first use, so a typo in it surfaces as a
 * NullPointerException in the middle of a tick - or, for the story prompts,
 * only when someone clicks Generate. These assertions move that to build time.
 */
public class PromptsConfigTest {

    @Test
    public void every_prompt_section_loads() {
	Prompts prompts = SmallvilleConfig.getPrompts();

	assertNotNull(prompts.getReactions(), "reactions");
	assertNotNull(prompts.getPlans(), "plans");
	assertNotNull(prompts.getAgent(), "agent");
	assertNotNull(prompts.getWorld(), "world");
	assertNotNull(prompts.getMisc(), "misc");
	assertNotNull(prompts.getStory(), "story");
    }

    @Test
    public void the_prompts_used_by_each_feature_are_present() {
	Prompts prompts = SmallvilleConfig.getPrompts();

	assertFilled(prompts.getReactions().getConversation(), "reactions.conversation");
	assertFilled(prompts.getReactions().getGroupConversation(), "reactions.groupConversation");
	assertFilled(prompts.getReactions().getConversationTone(), "reactions.conversationTone");
	assertFilled(prompts.getPlans().getShortTerm(), "plans.shortTerm");
	assertFilled(prompts.getPlans().getLongTerm(), "plans.longTerm");
	assertFilled(prompts.getPlans().getCurrent(), "plans.current");
	assertFilled(prompts.getStory().getFirst(), "story.first");
	assertFilled(prompts.getStory().getContinuation(), "story.continuation");
	assertFilled(prompts.getStory().getCompact(), "story.compact");
	assertFilled(prompts.getStory().getGenerateCharacter(), "story.generateCharacter");
    }

    @Test
    public void prompts_asking_for_json_say_so() {
	// The API rejects response_format json_object unless the word appears
	// in the prompt, so these three would fail outright at runtime.
	Prompts prompts = SmallvilleConfig.getPrompts();

	assertMentionsJson(prompts.getPlans().getShortTerm(), "plans.shortTerm");
	assertMentionsJson(prompts.getPlans().getLongTerm(), "plans.longTerm");
	assertMentionsJson(prompts.getStory().getGenerateCharacter(), "story.generateCharacter");
	assertMentionsJson(prompts.getReactions().getConversation(), "reactions.conversation");
	assertMentionsJson(prompts.getReactions().getGroupConversation(), "reactions.groupConversation");
    }

    @Test
    public void conversation_prompts_are_told_where_the_conversation_happens() {
	// Without this the model invents a setting. Four agents standing in a
	// Walmart were once given a church basement and a neighbourhood safety
	// forum, because nothing in the prompt said otherwise.
	assertTrue(SmallvilleConfig.getPrompts().getReactions().getConversation().contains("{{location}}"),
		"reactions.conversation is missing {{location}}");
	assertTrue(SmallvilleConfig.getPrompts().getReactions().getGroupConversation().contains("{{location}}"),
		"reactions.groupConversation is missing {{location}}");
    }

    @Test
    public void story_prompts_declare_the_values_they_are_given() {
	String continuation = SmallvilleConfig.getPrompts().getStory().getContinuation();

	for (String key : new String[] { "roster", "now", "storySoFar", "diary", "conversations" }) {
	    assertTrue(continuation.contains("{{" + key + "}}"), "story.continuation is missing {{" + key + "}}");
	}

	String compact = SmallvilleConfig.getPrompts().getStory().getCompact();

	assertTrue(compact.contains("{{summary}}"), "story.compact is missing {{summary}}");
	assertTrue(compact.contains("{{passages}}"), "story.compact is missing {{passages}}");
    }

    @Test
    public void conversation_prompts_declare_the_values_they_are_given() {
	String group = SmallvilleConfig.getPrompts().getReactions().getGroupConversation();

	assertTrue(group.contains("{{history}}"), "reactions.groupConversation is missing {{history}}");
	assertTrue(group.contains("{{observation}}"), "reactions.groupConversation is missing {{observation}}");

	assertTrue(SmallvilleConfig.getPrompts().getReactions().getConversationTone().contains("{{transcript}}"),
		"reactions.conversationTone is missing {{transcript}}");
    }

    private static void assertFilled(String prompt, String name) {
	assertNotNull(prompt, name + " is missing from prompts.yaml");
	assertTrue(!prompt.isBlank(), name + " is empty");
    }

    private static void assertMentionsJson(String prompt, String name) {
	assertFilled(prompt, name);
	assertTrue(prompt.toLowerCase().contains("json"), name + " requests json_object but never says \"json\"");
    }
}
