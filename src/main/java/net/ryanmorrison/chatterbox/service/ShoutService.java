package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.Message;

import java.util.Optional;

public interface ShoutService {

    Optional<Message> save(Message message);

    boolean update(Message message);

    boolean delete(Message message);

    boolean delete(long messageId);
}
