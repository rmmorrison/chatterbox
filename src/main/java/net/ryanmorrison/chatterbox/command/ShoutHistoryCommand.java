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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.ryanmorrison.chatterbox.service.ShoutService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Random;

@Singleton
public class ShoutHistoryCommand extends Command {

    private final ShoutService shoutService;
    private final Random random = new Random();

    @Inject
    public ShoutHistoryCommand(ShoutService shoutService) {
        this.shoutService = shoutService;
        this.name = "shout";
        this.help = "returns information about the shout type provided";
        this.arguments = "last";
    }

    @Override
    protected void execute(CommandEvent event) {
        shoutService.getHistory(event.getChannel()).ifPresentOrElse(message -> {
            Member member = message.getMember();
            String authorName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
            String iconUrl = message.getAuthor().getEffectiveAvatarUrl();

            MessageEmbed embed = new EmbedBuilder()
                    .setColor(new Color(
                            random.nextInt(255),
                            random.nextInt(255),
                            random.nextInt(255)
                    ))
                    .setTimestamp(message.getTimeCreated())
                    .setAuthor(authorName, null, iconUrl)
                    .setDescription(message.getContentDisplay())
                    .addField(
                            "Link to Context",
                            "https://discordapp.com/channels/" + message.getChannel().getId() + "/" + message.getChannel().getId() + "/" + message.getId(),
                            false
                    )
                    .build();

            event.reply(embed);
        }, () -> event.replyWarning("It doesn't look like anyone has shouted in this channel yet."));
    }
}
