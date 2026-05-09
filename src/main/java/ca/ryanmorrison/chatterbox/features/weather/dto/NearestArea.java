package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/** Reverse-geocoded location wttr.in resolved the query to. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NearestArea(
        @JsonProperty("areaName") List<ValueWrapper> areaName,
        @JsonProperty("region") List<ValueWrapper> region,
        @JsonProperty("country") List<ValueWrapper> country,
        @JsonProperty("latitude") String latitude,
        @JsonProperty("longitude") String longitude) {

    public Optional<String> city()    { return firstValue(areaName); }
    public Optional<String> regionName() { return firstValue(region); }
    public Optional<String> countryName() { return firstValue(country); }

    private static Optional<String> firstValue(List<ValueWrapper> list) {
        if (list == null || list.isEmpty() || list.get(0) == null) return Optional.empty();
        String v = list.get(0).value();
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }
}
