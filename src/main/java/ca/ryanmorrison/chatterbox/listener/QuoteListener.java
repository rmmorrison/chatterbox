package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.persistence.entity.Quote;
import ca.ryanmorrison.chatterbox.service.QuoteService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class QuoteListener extends ListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final QuoteService quoteService;

    public QuoteListener(@Autowired QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        if (!matches(event.getMessage().getContentDisplay())) return;
        Message message = event.getMessage();
        Optional<Quote> randomQuote = quoteService.processQuoteAndHistory(new Quote.Builder()
                .setMessageId(message.getIdLong())
                .setAuthorId(message.getAuthor().getIdLong())
                .setChannelId(message.getChannel().getIdLong())
                .setContent(message.getContentDisplay())
                .build());
        randomQuote.ifPresentOrElse(quote -> event.getChannel().sendMessage(String.format("**%s**", quote.getContent())).queue(),
                () -> log.debug("No quotes found in channel: {}, nothing to return.", event.getChannel().getIdLong()));
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (!event.isFromGuild()) return;

        if (!matches(event.getMessage().getContentDisplay())) {
            quoteService.deleteQuoteByMessageId(event.getMessageIdLong());
            log.debug("Deleting quote (if exists) due to original message edit: {}", event.getMessage().getContentDisplay());
        }
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;

        log.debug("Deleting quote (if exists) due to original message delete: {}", event.getMessageId());
        quoteService.deleteQuoteByMessageId(event.getMessageIdLong());
    }

    public boolean matches(String content) {
        log.debug("Evaluating message: {}", content);

        // Check if the message is greater than 5 characters in length
        if (content.length() <= 5) {
            log.debug("Message failed: less than or equal to 5 characters");
            return false;
        }

        // Remove URLs
        String noUrls = content.replaceAll("https?://\\S+\\s?", "");
        log.debug("Message after removing URLs: {}", noUrls);

        // Remove emojis
        String noEmojis = noUrls.replaceAll("[\\p{So}\\p{Cn}]", "");
        log.debug("Message after removing emojis: {}", noEmojis);

        // Check if more than 75% of the message is alphabetical characters
        long alphaCount = noEmojis.chars().filter(Character::isLetter).count();
        if (alphaCount <= 0.75 * noEmojis.length()) {
            log.debug("Message failed: less than 75% alphabetical characters");
            return false;
        }

        // Check if the message is in uppercase
        if (!noEmojis.equals(noEmojis.toUpperCase())) {
            log.debug("Message failed: not in uppercase");
            return false;
        }

        log.debug("Message passed all checks");
        return true;
    }
}
