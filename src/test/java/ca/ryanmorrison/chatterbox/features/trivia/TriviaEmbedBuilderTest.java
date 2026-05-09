package ca.ryanmorrison.chatterbox.features.trivia;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriviaEmbedBuilderTest {

    private static String fieldValue(MessageEmbed embed, String name) {
        for (MessageEmbed.Field f : embed.getFields()) {
            if (name.equals(f.getName())) return f.getValue();
        }
        throw new AssertionError("Missing field: " + name);
    }

    private static TriviaRound roundFor(TriviaQuestion q, List<String> labels, int correct) {
        return new TriviaRound("rid12345", 100L, 200L, q, labels, correct, Collections.emptySet());
    }

    @Test
    void questionEmbedListsAllChoicesWithLetters() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "easy", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
        TriviaRound round = roundFor(q,
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"), 1);

        MessageEmbed embed = TriviaEmbedBuilder.question(round);
        assertEquals("Trivia: Geography", embed.getTitle());
        assertEquals("Capital of Canada?", embed.getDescription());
        assertEquals(TriviaEmbedBuilder.COLOR_LIVE, embed.getColorRaw());

        String choices = fieldValue(embed, "Choices");
        // Letters in display order, choices spelled out exactly.
        assertTrue(choices.contains("**A.** Toronto"), choices);
        assertTrue(choices.contains("**B.** Ottawa"), choices);
        assertTrue(choices.contains("**C.** Vancouver"), choices);
        assertTrue(choices.contains("**D.** Montreal"), choices);
        // Footer mentions difficulty and source.
        assertNotNull(embed.getFooter());
        String footer = embed.getFooter().getText();
        assertTrue(footer.contains("Easy"), footer);
        assertTrue(footer.toLowerCase().contains("open trivia db"), footer);
        assertTrue(footer.toLowerCase().contains("first correct click"), footer);
    }

    @Test
    void questionEmbedDoesNotLeakCorrectAnswer() {
        // The "Choices" field shouldn't single out the correct one in any way
        // — visually all four lines look identical. The correct index is only
        // on the round, not the rendered text.
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "hard", "Cat",
                "Q?", "RIGHT",
                List.of("WRONG1", "WRONG2", "WRONG3"));
        TriviaRound round = roundFor(q,
                List.of("WRONG1", "RIGHT", "WRONG2", "WRONG3"), 1);

        MessageEmbed embed = TriviaEmbedBuilder.question(round);
        String choices = fieldValue(embed, "Choices");
        // Each entry uses the same prefix shape. No "*correct*", no checkmark.
        long boldCount = choices.chars().filter(c -> c == '*').count();
        // 4 lines × 4 asterisks (**X.**) = 16
        assertEquals(16L, boldCount,
                "all four choices should be rendered with identical bold-letter prefix");
        assertTrue(!choices.contains("✅") && !choices.contains("✓"),
                "no answer indicators in question phase");
    }

    @Test
    void winnerEmbedShowsCorrectAnswerAndMentions() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.MULTIPLE, "medium", "Geography",
                "Capital of Canada?", "Ottawa",
                List.of("Toronto", "Vancouver", "Montreal"));
        TriviaRound round = roundFor(q,
                List.of("Toronto", "Ottawa", "Vancouver", "Montreal"), 1);

        MessageEmbed embed = TriviaEmbedBuilder.winner(round, "<@123456>");
        assertEquals(TriviaEmbedBuilder.COLOR_CORRECT, embed.getColorRaw());
        assertTrue(fieldValue(embed, "Correct answer").contains("Ottawa"));
        assertEquals("<@123456>", fieldValue(embed, "Winner"));
    }

    @Test
    void timeoutEmbedShowsAnswerAndGreyAccent() {
        TriviaQuestion q = new TriviaQuestion(
                TriviaQuestion.Type.BOOLEAN, "easy", "Cat",
                "Sky is blue.", "True", List.of("False"));
        TriviaRound round = roundFor(q, List.of("True", "False"), 0);

        MessageEmbed embed = TriviaEmbedBuilder.timeout(round);
        assertEquals(TriviaEmbedBuilder.COLOR_TIMEOUT, embed.getColorRaw());
        assertTrue(fieldValue(embed, "Correct answer").contains("True"));
        assertTrue(fieldValue(embed, "Result").toLowerCase().contains("no winner"));
    }
}
