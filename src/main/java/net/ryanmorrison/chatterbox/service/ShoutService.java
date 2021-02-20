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

package net.ryanmorrison.chatterbox.service;

import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.transaction.annotation.ReadOnly;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.model.Shout;
import net.ryanmorrison.chatterbox.persistence.model.ShoutHistory;
import net.ryanmorrison.chatterbox.persistence.repository.ShoutHistoryRepository;
import net.ryanmorrison.chatterbox.persistence.repository.ShoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.transaction.Transactional;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service layer for interacting with a persistence layer for {@link net.ryanmorrison.chatterbox.persistence.model.Shout}s.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Singleton
public class ShoutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShoutService.class);

    private final ShoutRepository shoutRepository;
    private final ShoutHistoryRepository shoutHistoryRepository;

    @Inject
    public ShoutService(ShoutRepository shoutRepository, ShoutHistoryRepository shoutHistoryRepository) {
        this.shoutRepository = shoutRepository;
        this.shoutHistoryRepository = shoutHistoryRepository;
    }

    @Transactional
    @ReadOnly
    public Optional<Message> getHistory(MessageChannel channel) {
        try {
            ShoutHistory channelHistory = shoutHistoryRepository.findFirstByChannelId(channel.getIdLong());

            return Optional.of(
                    channel.retrieveMessageById(channelHistory.getMessageId()).complete()
            );
        } catch (EmptyResultException e) {
            LOGGER.debug("A query for shout history was made in channel {} but there is no history to display.",
                    channel.getIdLong(), e);
        }

        return Optional.empty();
    }

    @Transactional
    public Optional<Message> save(MessageChannel channel, Message message) {
        Shout toSave = new Shout(
                message.getIdLong(), message.getAuthor().getIdLong(), channel.getIdLong(), message.getContentDisplay()
        );

        try {
            int count = this.shoutRepository.countByChannelId(channel.getIdLong());
            if (count == 0) {
                LOGGER.debug("Channel {} has no shouts, nothing to return. Skipping.", channel.getIdLong());
                return Optional.empty();
            }

            int selected = ThreadLocalRandom.current().nextInt((count - 1) + 1);
            Slice<Shout> result = this.shoutRepository.findByChannelId(channel.getIdLong(), Pageable.from(selected, 1));

            if (result.isEmpty()) {
                LOGGER.warn("Channel {} reported shouts in count query, but no objects were returned. Skipping.", channel.getIdLong());
                return Optional.empty();
            }

            Shout first = result.getContent().get(0);
            Message random = channel.retrieveMessageById(first.getMessageId()).complete();
            if (random == null) {
                LOGGER.info("Random message query returned ID {} for channel {}, but Discord did not return message object. Was the message deleted?",
                        first.getMessageId(), channel.getIdLong());
                return Optional.empty();
            }

            ShoutHistory channelHistory;
            try {
                channelHistory = shoutHistoryRepository.findFirstByChannelId(channel.getIdLong());
                channelHistory.setMessageId(random.getIdLong());
                LOGGER.debug("Shout history exists for channel {}, updating with new random ID {}.",
                        message.getChannel().getIdLong(), random.getIdLong());
                this.shoutHistoryRepository.update(channelHistory);
            } catch (EmptyResultException e) {
                LOGGER.debug("No shout history exists for channel {}, saving new history of random ID {}.",
                        message.getChannel().getIdLong(), random.getIdLong(), e);

                this.shoutHistoryRepository.save(new ShoutHistory(random.getIdLong(), channel.getIdLong()));
            }

            return Optional.of(random);
        }
        finally {
            boolean duplicateContent =
                    this.shoutRepository.countByChannelIdAndContent(channel.getIdLong(), message.getContentDisplay()) > 0;

            if (duplicateContent) {
                LOGGER.debug("Duplicate content detected for message ID {} and channel ID {}, will not save in persistence store.",
                        toSave.getChannelId(), toSave.getMessageId());
            }
            else {
                LOGGER.debug("Saving shout with message ID {} and channel ID {} to persistence store.",
                        toSave.getChannelId(), toSave.getMessageId());
                this.shoutRepository.save(toSave);
            }
        }
    }

    @Transactional
    public void update(Message message) {
        Shout current;
        try {
            current = this.shoutRepository.findByChannelIdAndMessageId(message.getChannel().getIdLong(), message.getIdLong());
        } catch (EmptyResultException e) {
            LOGGER.debug("Received update for message ID {} in channel {} but no matching shout exists in the database, ignoring.",
                    message.getChannel().getIdLong(), message.getIdLong(), e);
            return;
        }

        current.setContent(message.getContentDisplay());
        this.shoutRepository.update(current);
    }

    @Transactional
    public void delete(long channelId, long messageId) {
        this.shoutRepository.deleteByChannelIdAndMessageId(channelId, messageId);
        if (this.shoutHistoryRepository.countByChannelIdAndMessageId(channelId, messageId) > 0) {
            this.shoutHistoryRepository.deleteByChannelId(channelId);
        }
    }
}
