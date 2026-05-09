package ca.ryanmorrison.chatterbox.features.trivia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level Open Trivia DB response shape:
 * {@code {"response_code": 0, "results": [...]}}.
 *
 * <p>Response codes:
 * <ul>
 *   <li>0 — Success</li>
 *   <li>1 — No Results (no questions matched the filters)</li>
 *   <li>2 — Invalid Parameter</li>
 *   <li>3 — Token Not Found</li>
 *   <li>4 — Token Empty (we'd need to reset; we don't use tokens)</li>
 *   <li>5 — Rate Limit (1 request per 5s per IP)</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TriviaResponse(
        @JsonProperty("response_code") int responseCode,
        @JsonProperty("results") List<TriviaResultEntry> results) {}
