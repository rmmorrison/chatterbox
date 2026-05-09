package ca.ryanmorrison.chatterbox.features.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartResult(@JsonProperty("meta") ChartMeta meta) {
}
