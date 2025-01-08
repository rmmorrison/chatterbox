package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.persistence.entity.Quote;
import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteHistoryRepository;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteRepository;
import jakarta.transaction.Transactional;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Random;

@Component
public class QuoteListener extends ListenerAdapter {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final QuoteRepository quoteRepository;
    private final QuoteHistoryRepository quoteHistoryRepository;
    private final Random random = new Random();

    public QuoteListener(@Autowired QuoteRepository quoteRepository, @Autowired QuoteHistoryRepository quoteHistoryRepository) {
        this.quoteRepository = quoteRepository;
        this.quoteHistoryRepository = quoteHistoryRepository;
    }

    @Override
    @Transactional
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        if (!matches(event.getMessage().getContentDisplay())) return;
        save(event.getMessage());
        findRandom(event.getChannel().getIdLong()).ifPresent(quote -> {
            quoteHistoryRepository.save(new QuoteHistory.Builder()
                    .setQuote(quote)
                    .setChannelId(event.getChannel().getIdLong())
                    .build());
            event.getChannel().sendMessage(String.format("**%s**", quote.getContent())).queue();
        });
    }

    @Override
    @Transactional
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        quoteRepository.findByMessageId(event.getMessageIdLong())
                .ifPresent(quote -> {
                    if (!matches(event.getMessage().getContentDisplay())) {
                        quoteRepository.delete(quote);
                        LOGGER.debug("Quote deleted due to edit: {}", quote);
                    }
                });
    }

    @Override
    @Transactional
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;

        quoteRepository.deleteByMessageId(event.getMessageIdLong());
    }

    public boolean matches(String content) {
        LOGGER.debug("Evaluating message: {}", content);

        // Check if the message is greater than 5 characters in length
        if (content.length() <= 5) {
            LOGGER.debug("Message failed: less than or equal to 5 characters");
            return false;
        }

        // Remove URLs
        String noUrls = content.replaceAll("https?://\\S+\\s?", "");
        LOGGER.debug("Message after removing URLs: {}", noUrls);

        // Remove emojis
        String noEmojis = noUrls.replaceAll("[\\p{So}\\p{Cn}]", "");
        LOGGER.debug("Message after removing emojis: {}", noEmojis);

        // Check if more than 75% of the message is alphabetical characters
        long alphaCount = noEmojis.chars().filter(Character::isLetter).count();
        if (alphaCount <= 0.75 * noEmojis.length()) {
            LOGGER.debug("Message failed: less than 75% alphabetical characters");
            return false;
        }

        // Check if the message is in uppercase
        if (!noEmojis.equals(noEmojis.toUpperCase())) {
            LOGGER.debug("Message failed: not in uppercase");
            return false;
        }

        LOGGER.debug("Message passed all checks");
        return true;
    }

    private void save(Message message) {
        quoteRepository.findByChannelIdAndContent(message.getChannel().getIdLong(), message.getContentDisplay())
                .ifPresentOrElse(
                        quote -> LOGGER.debug("Quote already exists in channel, skipping save: {}", quote),
                        () -> {
                            quoteRepository.save(new Quote.Builder()
                                    .setMessageId(message.getIdLong())
                                    .setAuthorId(message.getAuthor().getIdLong())
                                    .setChannelId(message.getChannel().getIdLong())
                                    .setContent(message.getContentDisplay())
                                    .build());
                            LOGGER.debug("New quote saved: {}", message.getContentDisplay());
                        }
                );
    }

    private Optional<Quote> findRandom(long channelId) {
        final int count = quoteRepository.countByChannelId(channelId);
        if (count == 0) {
            LOGGER.debug("No quotes found in channel: {}, nothing to return.", channelId);
            return Optional.empty();
        }

        final int randomIndex = random.nextInt(count);
        PageRequest pageRequest = PageRequest.of(randomIndex, 1);

        return quoteRepository.findByChannelId(channelId, pageRequest).stream().findFirst();
    }
}
