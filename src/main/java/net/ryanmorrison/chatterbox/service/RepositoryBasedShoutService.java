package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.model.ShoutDTO;
import net.ryanmorrison.chatterbox.persistence.repository.ShoutRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RepositoryBasedShoutService implements ShoutService {

    private final ShoutRepository shoutRepository;

    public RepositoryBasedShoutService(@Autowired ShoutRepository shoutRepository) {
        this.shoutRepository = shoutRepository;
    }

    @Override
    public Optional<Message> save(Message message) {
        Optional<Message> random = getRandomMessage(message.getChannel());

        ShoutDTO existing = shoutRepository.getShoutDTOByChannelIdAndContent(message.getChannel().getIdLong(),
                message.getContentDisplay());
        if (existing == null) {
            shoutRepository.save(ShoutDTO.builder()
                    .messageId(message.getIdLong())
                    .channelId(message.getChannel().getIdLong())
                    .authorId(message.getAuthor().getIdLong())
                    .content(message.getContentDisplay())
                    .build());
        }

        return random;
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
            return Optional.ofNullable(channel.retrieveMessageById(messageId).complete());
        }

        return Optional.empty();
    }
}
