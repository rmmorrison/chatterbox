package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface TriggerRepository extends CrudRepository<Trigger, Integer> {

    List<Trigger> findAllByChannelId(long channelId);

    Optional<Trigger> findByChannelIdAndChallenge(long channelId, String challenge);
}
