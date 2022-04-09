package net.ryanmorrison.chatterbox.persistence.repository;

import net.ryanmorrison.chatterbox.persistence.model.ShoutHistoryDTO;
import org.springframework.data.repository.CrudRepository;

public interface ShoutHistoryRepository extends CrudRepository<ShoutHistoryDTO, Long> {

    ShoutHistoryDTO findShoutHistoryDTOByChannelId(long channelId);

    int deleteByMessageId(long messageId);
}
