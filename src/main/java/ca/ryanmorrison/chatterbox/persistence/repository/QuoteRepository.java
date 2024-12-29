package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface QuoteRepository extends CrudRepository<Quote, Long> {

    Optional<Quote> findByMessageId(long messageId);

    Optional<Quote> findByChannelIdAndContent(long channelId, String content);

    int countByChannelId(long channelId);

    Page<Quote> findByChannelId(long channelId, Pageable pageable);
}
