package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaHandlerTest {

    @Test
    void buildsOneButtonPerChoiceWithRoundIdAndIndex() {
        List<Button> buttons = TriviaHandler.buildButtons(
                "rid12345",
                List.of("Ottawa", "Toronto", "Vancouver", "Montreal"));
        assertEquals(4, buttons.size());
        for (int i = 0; i < buttons.size(); i++) {
            String id = buttons.get(i).getCustomId();
            assertEquals("trivia:answer:rid12345:" + i, id,
                    "custom_id encodes only roundId and index — never the answer text");
        }
        // Labels are visible — but indices are zero-based so users see A/B/C/D.
        assertTrue(buttons.get(0).getLabel().startsWith("A. "));
        assertTrue(buttons.get(3).getLabel().startsWith("D. "));
    }

    @Test
    void buttonLabelsAreTruncatedToFitDiscordLimit() {
        // Discord caps button labels at 80 chars. Our prefix ("X. ") plus the
        // truncated label needs to leave room for the ellipsis.
        String longAnswer = "x".repeat(120);
        List<Button> buttons = TriviaHandler.buildButtons("rid12345", List.of(longAnswer));
        assertTrue(buttons.get(0).getLabel().length() <= 80,
                "label must respect Discord's 80-char button cap");
        assertTrue(buttons.get(0).getLabel().endsWith("…"),
                "truncation should be marked with an ellipsis");
    }

    @Test
    void booleanRoundsRenderTwoButtons() {
        List<Button> buttons = TriviaHandler.buildButtons("rid12345", List.of("True", "False"));
        assertEquals(2, buttons.size());
        assertEquals("trivia:answer:rid12345:0", buttons.get(0).getCustomId());
        assertEquals("trivia:answer:rid12345:1", buttons.get(1).getCustomId());
    }
}
