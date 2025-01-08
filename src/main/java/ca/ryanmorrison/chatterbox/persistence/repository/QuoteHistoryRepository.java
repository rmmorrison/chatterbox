package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface QuoteHistoryRepository extends CrudRepository<QuoteHistory, Long> {

    Optional<QuoteHistory> findFirstByChannelIdOrderByEmittedDesc(long channelId);
}
