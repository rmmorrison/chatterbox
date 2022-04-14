package net.ryanmorrison.chatterbox.persistence.repository;

import net.ryanmorrison.chatterbox.persistence.model.Shout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoutRepository extends CrudRepository<Shout, Long> {

    long countByChannelId(long channelId);

    Shout getShoutByChannelIdAndContent(long channelId, String content);

    Shout getShoutByMessageId(long messageId);

    Page<Shout> findAllByChannelId(long channelId, Pageable pageable);

    int deleteByMessageId(long messageId);
}
