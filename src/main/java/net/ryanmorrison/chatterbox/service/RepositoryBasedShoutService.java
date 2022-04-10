package net.ryanmorrison.chatterbox.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.model.ShoutDTO;
import net.ryanmorrison.chatterbox.persistence.model.ShoutHistoryDTO;
import net.ryanmorrison.chatterbox.persistence.repository.ShoutHistoryRepository;
import net.ryanmorrison.chatterbox.persistence.repository.ShoutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class RepositoryBasedShoutService implements ShoutService {

    private final ShoutRepository shoutRepository;
    private final ShoutHistoryRepository shoutHistoryRepository;

    public RepositoryBasedShoutService(@Autowired ShoutRepository shoutRepository, @Autowired ShoutHistoryRepository shoutHistoryRepository) {
        this.shoutRepository = shoutRepository;
        this.shoutHistoryRepository = shoutHistoryRepository;
    }

    @Override
    public long count(MessageChannel messageChannel) {
        return shoutRepository.countByChannelId(messageChannel.getIdLong());
    }

    @Override
    public Optional<Message> getHistory(MessageChannel channel) {
        ShoutHistoryDTO historyDTO = shoutHistoryRepository.findShoutHistoryDTOByChannelId(channel.getIdLong());
        if (historyDTO == null) return Optional.empty();

        return Optional.ofNullable(channel.retrieveMessageById(historyDTO.getMessageId()).complete());
    }

    @Override
    public Optional<Message> save(Message message) {
        Optional<Message> random = getRandomMessage(message.getChannel());

        ShoutDTO existing = shoutRepository.getShoutDTOByChannelIdAndContent(message.getChannel().getIdLong(),
                message.getContentDisplay());
        if (existing == null) {
            log.debug("Existing shout matching content \"{}\" in channel with ID {} doesn't exist, saving new.",
                    message.getContentDisplay(), message.getChannel().getIdLong());
            shoutRepository.save(ShoutDTO.builder()
                    .messageId(message.getIdLong())
                    .channelId(message.getChannel().getIdLong())
                    .authorId(message.getAuthor().getIdLong())
                    .content(message.getContentDisplay())
                    .build());
        }

        random.ifPresent(rand -> {
            // do we have history already?
            ShoutHistoryDTO existingHistory = shoutHistoryRepository.findShoutHistoryDTOByChannelId(message.getChannel().getIdLong());
            if (existingHistory != null) {
                // we do - save updated value
                existingHistory.setMessageId(rand.getIdLong());
                shoutHistoryRepository.save(existingHistory);
            }
            else {
                // we do not - create it
                shoutHistoryRepository.save(ShoutHistoryDTO.builder()
                        .channelId(rand.getChannel().getIdLong())
                        .messageId(rand.getIdLong())
                        .build());
            }
        });

        return random;
    }

    @Override
    public boolean update(Message message) {
        ShoutDTO existing = shoutRepository.getShoutDTOByMessageId(message.getIdLong());
        if (existing != null) {
            log.debug("Shout matching message ID {} identified in the database, updating contents.", message.getIdLong());
            existing.setContent(message.getContentDisplay());
            shoutRepository.save(existing);
            return true;
        }

        log.debug("Shout matching message ID {} does not exist in the database, nothing to do for update operation.",
                message.getIdLong());
        return false;
    }

    @Override
    public boolean delete(Message message) {
        return delete(message.getIdLong());
    }

    @Override
    public boolean delete(long messageId) {
        shoutHistoryRepository.deleteByMessageId(messageId);
        return shoutRepository.deleteByMessageId(messageId) == 1;
    }

    private Optional<Message> getRandomMessage(MessageChannel channel) {
        // first count total number of messages in the channel
        long total = shoutRepository.countByChannelId(channel.getIdLong());
        // choose a random value as an index
        int index = (int)(Math.random() * total);

        // use Spring Data's Pageable to get a single "page" at the random index
        Page<ShoutDTO> randomPage = shoutRepository.findAllByChannelId(channel.getIdLong(), PageRequest.of(index, 1));

        // if it exists, query Discord API to load the entire message and return it
        // TODO: if Discord says it doesn't exist anymore, we should delete from the database too
        if (randomPage.hasContent()) {
            long messageId = randomPage.getContent().get(0).getMessageId();
            Message lookup = channel.retrieveMessageById(messageId).complete();
            // if Discord returns no message, it's been deleted but we never caught it for some reason - we should
            // delete it too, since it has no reference in Discord anymore
            if (lookup == null) {
                delete(messageId);
                return Optional.empty();
            }

            return Optional.of(lookup);
        }

        return Optional.empty();
    }
}
