package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import org.springframework.data.repository.CrudRepository;

public interface QuoteHistoryRepository extends CrudRepository<QuoteHistory, Long> {
}
