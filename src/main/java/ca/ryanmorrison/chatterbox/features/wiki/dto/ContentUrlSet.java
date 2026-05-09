package ca.ryanmorrison.chatterbox.features.wiki.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One of the entries in {@code content_urls} (keyed by {@code desktop} or
 * {@code mobile}). Only {@code page} is used.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentUrlSet(String page) {
}
