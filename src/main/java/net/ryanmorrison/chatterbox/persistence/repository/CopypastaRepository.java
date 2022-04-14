package net.ryanmorrison.chatterbox.persistence.repository;

import net.ryanmorrison.chatterbox.persistence.model.CopypastaEntry;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CopypastaRepository extends CrudRepository<CopypastaEntry, Integer> {

    List<CopypastaEntry> findAllByChannelId(long channelId);

    CopypastaEntry findByChannelIdAndTrigger(long channelId, String trigger);

    int deleteByChannelIdAndTrigger(long channelId, String trigger);
}
