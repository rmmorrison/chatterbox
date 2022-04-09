package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.Optional;

public interface ShoutService {

    Optional<Message> getHistory(MessageChannel channel);

    Optional<Message> save(Message message);

    boolean update(Message message);

    boolean delete(Message message);

    boolean delete(long messageId);
}
