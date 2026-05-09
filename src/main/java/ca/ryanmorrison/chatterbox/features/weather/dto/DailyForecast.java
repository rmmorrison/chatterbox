package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * One day in wttr.in's forecast array. The first entry is "today" (its
 * {@code maxtempC} / {@code mintempC} are based on the full day, including
 * already-passed hours), the next two are the upcoming days.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyForecast(
        @JsonProperty("date") String date,
        @JsonProperty("maxtempC") String maxTempC,
        @JsonProperty("maxtempF") String maxTempF,
        @JsonProperty("mintempC") String minTempC,
        @JsonProperty("mintempF") String minTempF,
        @JsonProperty("avgtempC") String avgTempC,
        @JsonProperty("avgtempF") String avgTempF,
        @JsonProperty("uvIndex") String uvIndex,
        @JsonProperty("hourly") List<HourlyForecast> hourly) {

    /**
     * Pick the hour-bucket closest to noon as the day's "representative"
     * condition. wttr.in returns 8 buckets per day at 3-hour spacing; the
     * 12:00 entry sits at index 4 of a well-formed list, but we look up
     * by {@code time == "1200"} so a sparse list still works.
     */
    public Optional<HourlyForecast> noonish() {
        if (hourly == null) return Optional.empty();
        return hourly.stream().filter(h -> "1200".equals(h.time())).findFirst()
                .or(() -> hourly.size() > 4 ? Optional.ofNullable(hourly.get(4)) : Optional.empty());
    }
}
