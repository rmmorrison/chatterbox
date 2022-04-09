package net.ryanmorrison.chatterbox.persistence.repository;

import net.ryanmorrison.chatterbox.persistence.model.ShoutDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShoutRepository extends CrudRepository<ShoutDTO, Long> {

    long countByChannelId(long channelId);

    ShoutDTO getShoutDTOByChannelIdAndContent(long channelId, String content);

    Page<ShoutDTO> findAllByChannelId(long channelId, Pageable pageable);
}
