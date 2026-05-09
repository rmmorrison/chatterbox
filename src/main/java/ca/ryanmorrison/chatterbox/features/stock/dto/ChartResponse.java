package ca.ryanmorrison.chatterbox.features.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * Top-level shape: {@code {"chart": {"result": [...], "error": {...}}}}.
 * On a happy lookup, {@code result} has one entry and {@code error} is null;
 * on 4xx/5xx, {@code result} is null and {@code error} carries the reason.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartResponse(@JsonProperty("chart") Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(
            @JsonProperty("result") List<ChartResult> result,
            @JsonProperty("error") ChartError error) {}

    public Optional<ChartMeta> meta() {
        if (chart == null || chart.result() == null || chart.result().isEmpty()) {
            return Optional.empty();
        }
        ChartResult first = chart.result().get(0);
        return Optional.ofNullable(first == null ? null : first.meta());
    }

    public Optional<ChartError> error() {
        return Optional.ofNullable(chart == null ? null : chart.error());
    }
}
