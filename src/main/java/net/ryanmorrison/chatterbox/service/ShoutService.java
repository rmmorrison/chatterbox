package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.Message;

import java.util.Optional;

public interface ShoutService {

    Optional<Message> save(Message message);
}
