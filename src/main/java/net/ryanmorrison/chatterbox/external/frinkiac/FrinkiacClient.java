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

package net.ryanmorrison.chatterbox.external.frinkiac;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import net.ryanmorrison.chatterbox.external.frinkiac.model.Frame;
import net.ryanmorrison.chatterbox.external.frinkiac.model.FramePreview;

import java.util.List;

/**
 * Client interface for the Frinkiac API. The concrete implementation will be generated at runtime by Micronaut's
 * HTTP client implementation.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Client(FrinkiacClient.BASE_URL + "/api")
public interface FrinkiacClient {

    String BASE_URL = "https://frinkiac.com";

    @Get("/random")
    FramePreview fetchRandom();

    @Get("/search?q={query}")
    List<Frame> search(String query);

    @Get("/caption?e={episode}&t={timestamp}")
    FramePreview fetchCaption(String episode, int timestamp);
}
