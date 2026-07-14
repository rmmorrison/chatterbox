package ca.ryanmorrison.chatterbox.features.trivia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shape of {@code /api_category.php}: {@code {"trivia_categories":[{"id":N,"name":"..."}]}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryListResponse(
        @JsonProperty("trivia_categories") List<Category> triviaCategories) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name) {}
}
