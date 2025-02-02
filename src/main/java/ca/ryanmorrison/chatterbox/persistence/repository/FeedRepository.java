package ca.ryanmorrison.chatterbox.persistence.repository;

import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface FeedRepository extends CrudRepository<Feed, Long> {

    @NotNull List<Feed> findAll();

    List<Feed> findAllByChannelId(long channelId);

    Optional<Feed> findByChannelIdAndUrl(long channelId, String url);
}
