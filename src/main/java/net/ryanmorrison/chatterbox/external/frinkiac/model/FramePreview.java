/*
 * chatterbox - a (not so helpful) Discord bot custom written for a private server
 * Copyright (C) 2021 Ryan Morrison
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.ryanmorrison.chatterbox.external.frinkiac.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the object returned by the Frinkiac API when accessing the "preview" of an episode's frame.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
public class FramePreview {

    @JsonProperty("Episode")
    @Getter
    private Episode episode;

    @JsonProperty("Frame")
    @Getter
    private Frame frame;

    @JsonProperty("Subtitles")
    @Getter
    private List<Subtitle> subtitles;

    @JsonProperty("Nearby")
    @Getter
    private List<Nearby> nearby;

    public String getSubtitlesAsString() {
        return subtitles.stream()
                .map(Subtitle::getContent)
                .collect(Collectors.joining(" "));
    }
}
