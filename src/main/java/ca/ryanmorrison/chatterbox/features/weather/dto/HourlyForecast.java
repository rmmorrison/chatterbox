package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One hour-bucket inside a forecast day. wttr.in returns 8 of these per
 * day at 3-hour intervals: {@code time} is the hour-of-day as
 * {@code "0"}, {@code "300"}, {@code "600"}, …, {@code "2100"}.
 *
 * <p>We pull the noon-ish bucket out and use its weather code + description
 * as the day's representative condition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HourlyForecast(
        @JsonProperty("time") String time,
        @JsonProperty("tempC") String tempC,
        @JsonProperty("tempF") String tempF,
        @JsonProperty("weatherCode") String weatherCode,
        @JsonProperty("weatherDesc") List<ValueWrapper> weatherDesc,
        @JsonProperty("chanceofrain") String chanceOfRain,
        @JsonProperty("chanceofsnow") String chanceOfSnow) {

    public String description() {
        return weatherDesc == null || weatherDesc.isEmpty() || weatherDesc.get(0) == null
                ? ""
                : weatherDesc.get(0).value();
    }
}
