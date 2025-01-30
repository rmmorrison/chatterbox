package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.LastSeen;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface LastSeenRepository extends CrudRepository<LastSeen, Long> {

    Optional<LastSeen> findFirstByUserId(long userId);
}
