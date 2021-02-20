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
import net.ryanmorrison.chatterbox.external.frinkiac.FrinkiacClient;

/**
 * Represents the "Frame" object inside Frinkiac API responses.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
public class Frame {

    @JsonProperty("Id")
    @Getter
    private long id;

    @JsonProperty("Episode")
    @Getter
    private String episode;

    @JsonProperty("Timestamp")
    @Getter
    private int timestamp;

    /**
     * Constructs and returns a URI (in String form) to the screencap for this frame.
     * @return the URI to the screencap for this frame
     */
    public String getImageURI() {
        return String.format("%s/img/%s/%d.jpg", FrinkiacClient.BASE_URL, episode, timestamp);
    }

    /**
     * Constructs and returns a URI (in String form) to the frame preview page on Frinkiac for this frame.
     * @return the URI to the frame preview page for this frame
     */
    public String getCaptureURI() {
        return String.format("%s/caption/%s/%d", FrinkiacClient.BASE_URL, episode, timestamp);
    }
}
