package ca.ryanmorrison.chatterbox.features.trivia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response shape for {@code /api_token.php?command=request}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriviaTokenResponse(
        @JsonProperty("response_code") int responseCode,
        @JsonProperty("response_message") String responseMessage,
        @JsonProperty("token") String token) {}
