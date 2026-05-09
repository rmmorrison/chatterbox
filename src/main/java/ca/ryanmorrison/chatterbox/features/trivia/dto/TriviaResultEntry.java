package ca.ryanmorrison.chatterbox.features.trivia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One trivia question as returned by Open Trivia DB. All string fields are
 * URL-encoded ({@code encode=url3986}) on the wire — decoding happens at the
 * client boundary so the rest of the code never sees raw percent-escapes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriviaResultEntry(
        @JsonProperty("type") String type,
        @JsonProperty("difficulty") String difficulty,
        @JsonProperty("category") String category,
        @JsonProperty("question") String question,
        @JsonProperty("correct_answer") String correctAnswer,
        @JsonProperty("incorrect_answers") List<String> incorrectAnswers) {}
