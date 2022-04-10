package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.model.CopypastaEntry;

import java.util.List;

public interface CopypastaService {

    List<String> getTriggersInChannel(MessageChannel channel);

    List<CopypastaEntry> getEntriesInChannel(MessageChannel channel);

    void save(CopypastaEntry entry);

    boolean delete(MessageChannel channel, String trigger);
}
