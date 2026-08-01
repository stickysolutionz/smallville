package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.Dialog;

public class DialogTest {

    @Test
    public void speaker_recognises_their_own_line_by_full_name() {
	Dialog dialog = new Dialog("Maria Lopez", "Did you hear about the market?");

	assertTrue(dialog.isSpokenBy("Maria Lopez"));
	assertEquals("I said: Did you hear about the market?", dialog.asMemoryFor("Maria Lopez"));
    }

    @Test
    public void speaker_recognises_their_own_line_from_a_first_name_label() {
	// The model routinely writes "Maria:" for an agent registered as
	// "Maria Lopez", so an exact match alone would attribute every one of
	// her own lines to somebody else.
	Dialog dialog = new Dialog("Maria", "Did you hear about the market?");

	assertTrue(dialog.isSpokenBy("Maria Lopez"));
	assertEquals("I said: Did you hear about the market?", dialog.asMemoryFor("Maria Lopez"));
    }

    @Test
    public void listeners_remember_the_line_attributed_to_its_speaker() {
	Dialog dialog = new Dialog("Maria Lopez", "Did you hear about the market?");

	assertFalse(dialog.isSpokenBy("Klaus Mueller"));
	assertEquals("Maria Lopez said: Did you hear about the market?", dialog.asMemoryFor("Klaus Mueller"));
    }

    @Test
    public void partial_name_overlap_is_not_treated_as_the_same_person() {
	// "Mari" is a prefix of "Maria Lopez" but is not her, and "Maria" is a
	// prefix of "Marian Hill" but is not her either. Matching on raw
	// startsWith would get both of these wrong.
	assertFalse(new Dialog("Mari", "hello").isSpokenBy("Maria Lopez"));
	assertFalse(new Dialog("Maria", "hello").isSpokenBy("Marian Hill"));
    }

    @Test
    public void matching_is_case_insensitive_and_ignores_surrounding_space() {
	assertTrue(new Dialog("  maria lopez ", "hello").isSpokenBy("Maria Lopez"));
    }

    @Test
    public void a_missing_speaker_label_is_never_attributed_to_anyone() {
	assertFalse(new Dialog(null, "hello").isSpokenBy("Maria Lopez"));
    }
}
