package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Snapshot of the current observation at the queried location.
 *
 * <p>Numbers come back as strings (wttr.in's quirk); we keep them as
 * strings here and parse on render to avoid losing fidelity for the
 * occasional missing field. Only the subset we render is exposed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentCondition(
        @JsonProperty("temp_C") String tempC,
        @JsonProperty("temp_F") String tempF,
        @JsonProperty("FeelsLikeC") String feelsLikeC,
        @JsonProperty("FeelsLikeF") String feelsLikeF,
        @JsonProperty("humidity") String humidity,
        @JsonProperty("weatherCode") String weatherCode,
        @JsonProperty("weatherDesc") List<ValueWrapper> weatherDesc,
        @JsonProperty("windspeedKmph") String windSpeedKmph,
        @JsonProperty("windspeedMiles") String windSpeedMiles,
        @JsonProperty("winddir16Point") String windDir,
        @JsonProperty("uvIndex") String uvIndex,
        @JsonProperty("visibility") String visibilityKm,
        @JsonProperty("visibilityMiles") String visibilityMiles,
        @JsonProperty("pressure") String pressureMb,
        @JsonProperty("cloudcover") String cloudCover) {

    public String description() {
        return weatherDesc == null || weatherDesc.isEmpty() || weatherDesc.get(0) == null
                ? ""
                : weatherDesc.get(0).value();
    }
}
