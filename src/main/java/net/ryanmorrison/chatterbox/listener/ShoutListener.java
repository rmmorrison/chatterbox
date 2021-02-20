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

package net.ryanmorrison.chatterbox.listener;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.ryanmorrison.chatterbox.service.ShoutService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Non-command listener that analyzes all messages to check for ones qualifying as a "shout". If so, persists
 * to an underlying persistence store and returns a previously stored random "shout".
 *
 * For more details on what constitutes a "shout", see the {@link net.ryanmorrison.chatterbox.persistence.model.Shout} class.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Singleton
public class ShoutListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShoutListener.class);

    private final ShoutService shoutService;

    @Inject
    public ShoutListener(ShoutService shoutService) {
        this.shoutService = shoutService;
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (!contentMatchesShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            LOGGER.debug("Received message \"{}\" in channel {} which does not match shout, ignoring.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
            return;
        }

        LOGGER.debug("Received new message \"{}\" in channel {} which does not match shout, calling save.",
                event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
        Optional<Message> randomMessage = this.shoutService.save(event.getChannel(), event.getMessage());
        randomMessage.ifPresent(message -> event.getChannel().sendMessage(
                new MessageBuilder()
                        .append(message.getContentDisplay(), MessageBuilder.Formatting.BOLD)
                        .build()).queue());
    }

    @Override
    public void onGuildMessageUpdate(@NotNull GuildMessageUpdateEvent event) {
        if (!contentMatchesShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            LOGGER.debug("Received updated message \"{}\" in channel {} which does not match shout, deleting original shout.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());

            this.shoutService.delete(event.getChannel().getIdLong(), event.getMessage().getIdLong());

            return;
        }

        LOGGER.debug("Received updated message \"{}\" in channel {} which matches shout, calling update.",
                event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
        this.shoutService.update(event.getMessage());
    }

    @Override
    public void onGuildMessageDelete(@NotNull GuildMessageDeleteEvent event) {
        LOGGER.debug("Received deleted message with ID {} in channel {}, deleting corresponding shout.",
                event.getMessageIdLong(), event.getChannel().getIdLong());
        this.shoutService.delete(event.getChannel().getIdLong(), event.getMessageIdLong());
    }

    private boolean contentMatchesShout(User author, String content) {
        if (author.isBot()) return false;
        if (content.equals(content.toLowerCase())) return false;
        if (content.startsWith("$")) return false; // special case for currency
        if (content.startsWith("http")) return false; // special case for links
        if (content.length() <= 5) return false;

        int nonCharCount = 0;
        for (char current : content.toCharArray()) {
            if (!Character.isLetter(current)) {
                nonCharCount++;
            }
        }

        return (int) (((float) nonCharCount / content.length()) * 100) <= 50;
    }
}
