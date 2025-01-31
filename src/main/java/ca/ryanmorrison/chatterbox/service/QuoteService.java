package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.extension.Page;
import ca.ryanmorrison.chatterbox.persistence.entity.Quote;
import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteHistoryRepository;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Service
public class QuoteService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final QuoteRepository quoteRepository;
    private final QuoteHistoryRepository quoteHistoryRepository;
    private final Random random = new Random();

    public QuoteService(@Autowired QuoteRepository quoteRepository,
                        @Autowired QuoteHistoryRepository quoteHistoryRepository) {
        this.quoteRepository = quoteRepository;
        this.quoteHistoryRepository = quoteHistoryRepository;
    }

    @Transactional
    public Optional<Quote> findRandomQuote(long channelId) {
        final int count = quoteRepository.countByChannelId(channelId);
        if (count == 0) {
            return Optional.empty();
        }

        final int randomIndex = random.nextInt(count);
        PageRequest pageRequest = PageRequest.of(randomIndex, 1);

        return quoteRepository.findByChannelId(channelId, pageRequest).stream().findFirst();
    }

    @Transactional
    public Optional<Quote> processQuoteAndHistory(Quote newQuote) {
        Optional<Quote> randomQuote = findRandomQuote(newQuote.getChannelId());
        saveQuote(newQuote);
        randomQuote.ifPresent(quote -> quoteHistoryRepository.save(new QuoteHistory.Builder()
                .setChannelId(quote.getChannelId())
                .setQuote(quote)
                .build()
        ));
        return randomQuote;
    }

    @Transactional
    public void saveQuote(Quote quote) {
        quoteRepository.findByChannelIdAndContent(quote.getChannelId(), quote.getContent())
                .ifPresentOrElse(
                        existingQuote -> log.debug("Quote already exists in channel, skipping save: {}", quote),
                        () -> quoteRepository.save(quote)
                );
    }

    @Transactional
    public void deleteQuoteByMessageId(long messageId) {
        quoteRepository.deleteByMessageId(messageId);
    }

    @Transactional
    public Optional<Page<QuoteHistory>> findQuoteHistory(long channelId, int index) {
        int count = quoteHistoryRepository.countByChannelId(channelId);
        if (count == 0) {
            // no history for this channel yet
            return Optional.empty();
        }

        PageRequest pageRequest = PageRequest.of(index, 1);
        Optional<QuoteHistory> historyOptional = quoteHistoryRepository.findByChannelIdOrderByEmittedDesc(channelId, pageRequest)
                .stream().findFirst();
        if (historyOptional.isEmpty()) {
            log.error("Expected page based on index {} for channel ID {} but none was found.", index, channelId);
            return Optional.empty();
        }

        QuoteHistory quoteHistory = historyOptional.get();
        return Optional.of(new Page.Builder<QuoteHistory>()
                .setObject(quoteHistory)
                .setIndex(index)
                .setCount(count)
                .build());
    }
}
