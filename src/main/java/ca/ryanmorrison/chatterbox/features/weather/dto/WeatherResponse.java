package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/** Top-level wttr.in {@code ?format=j1} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherResponse(
        @JsonProperty("current_condition") List<CurrentCondition> currentCondition,
        @JsonProperty("nearest_area") List<NearestArea> nearestArea,
        @JsonProperty("weather") List<DailyForecast> weather) {

    public Optional<CurrentCondition> current() {
        return currentCondition == null || currentCondition.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(currentCondition.get(0));
    }

    public Optional<NearestArea> nearest() {
        return nearestArea == null || nearestArea.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(nearestArea.get(0));
    }

    public List<DailyForecast> forecast() {
        return weather == null ? List.of() : weather;
    }
}
