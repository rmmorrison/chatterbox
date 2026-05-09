package ca.ryanmorrison.chatterbox.features.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * wttr.in wraps every scalar inside {@code [{"value": "X"}]}. This record
 * is the {@code .value} unwrapper, used wherever a label like
 * {@code areaName}, {@code country}, {@code weatherDesc} appears.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ValueWrapper(String value) {
}
