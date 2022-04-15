package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.dto.ShoutMemberCountDTO;

import java.util.List;
import java.util.Optional;

public interface ShoutService {

    long count(MessageChannel messageChannel);

    List<ShoutMemberCountDTO> getTop10Users(MessageChannel channel, Guild guild);

    Optional<Message> getHistory(MessageChannel channel);

    Optional<Message> save(Message message);

    boolean update(Message message);

    boolean delete(Message message);

    boolean delete(long messageId);
}
