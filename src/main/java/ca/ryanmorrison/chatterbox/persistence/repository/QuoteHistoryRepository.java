package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface QuoteHistoryRepository extends CrudRepository<QuoteHistory, Long> {

    int countByChannelId(long channelId);

    Optional<QuoteHistory> findFirstByChannelIdOrderByEmittedDesc(long channelId);

    Page<QuoteHistory> findByChannelIdOrderByEmittedDesc(long channelId, Pageable pageable);
}
