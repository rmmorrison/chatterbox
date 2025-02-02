package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeedRepository extends CrudRepository<Feed, Long> {

    @NotNull List<Feed> findAll();

    List<Feed> findAllByChannelId(long channelId);

    Optional<Feed> findByChannelIdAndUrl(long channelId, String url);

    @Modifying
    @Query("update Feed f set f.lastPublished = :lastPublished where f.id = :id")
    void updateLastPublished(long id, Instant lastPublished);
}
