package net.ryanmorrison.chatterbox.persistence.repository;

import net.ryanmorrison.chatterbox.persistence.model.ShoutHistory;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoutHistoryRepository extends CrudRepository<ShoutHistory, Long> {

    ShoutHistory findShoutHistoryByChannelId(long channelId);

    int deleteByMessageId(long messageId);
}
