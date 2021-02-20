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

package net.ryanmorrison.chatterbox.command;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.ryanmorrison.chatterbox.external.frinkiac.FrinkiacClient;
import net.ryanmorrison.chatterbox.external.frinkiac.model.Frame;
import net.ryanmorrison.chatterbox.external.frinkiac.model.FramePreview;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * Command implementation for interacting with Frinkiac (the Simpsons screenshots service).
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Singleton
public class FrinkiacCommand extends Command {

    private final FrinkiacClient frinkiacClient;

    @Inject
    public FrinkiacCommand(FrinkiacClient frinkiacClient) {
        this.frinkiacClient = frinkiacClient;
        this.name = "frinkiac";
        this.help = "interact with Frinkiac, the Simpsons screencap repository";
        this.arguments = "<random | search query>";
    }

    @Override
    protected void execute(CommandEvent event) {
        if (event.getArgs().trim().isEmpty()) {
            event.replyWarning("You must supply at least one argument to this command.");
            return;
        }

        FramePreview framePreview;
        if (event.getArgs().equals("random")) {
            framePreview = frinkiacClient.fetchRandom();
        }
        else {
            Optional<FramePreview> previewResult = query(event.getArgs());
            if (previewResult.isEmpty()) {
                event.replyWarning("Oops! Looks like no results matched the query you provided.");
                return;
            }

            framePreview = previewResult.get();
        }

        event.reply(new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle(framePreview.getSubtitlesAsString(), framePreview.getFrame().getCaptureURI())
                .setImage(framePreview.getFrame().getImageURI())
                .addField("Episode", framePreview.getFrame().getEpisode(), true)
                .addField("Title", framePreview.getEpisode().getTitle(), true)
                .addField("Air Date", framePreview.getEpisode().getOriginalAirDate(), true)
                .build());
    }

    private Optional<FramePreview> query(String query) {
        List<Frame> frames = frinkiacClient.search(query);
        if (frames == null || frames.isEmpty()) {
            return Optional.empty();
        }

        Frame first = frames.get(0);
        FramePreview framePreview = frinkiacClient.fetchCaption(first.getEpisode(), first.getTimestamp());

        return Optional.ofNullable(framePreview);
    }
}
