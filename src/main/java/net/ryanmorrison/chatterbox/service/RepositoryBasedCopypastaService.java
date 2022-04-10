package net.ryanmorrison.chatterbox.service;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.ryanmorrison.chatterbox.persistence.model.CopypastaEntry;
import net.ryanmorrison.chatterbox.persistence.repository.CopypastaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RepositoryBasedCopypastaService implements CopypastaService {

    private final CopypastaRepository copypastaRepository;

    public RepositoryBasedCopypastaService(@Autowired CopypastaRepository copypastaRepository) {
        this.copypastaRepository = copypastaRepository;
    }

    @Override
    public List<String> getTriggersInChannel(MessageChannel channel) {
        return copypastaRepository.findAllByChannelId(channel.getIdLong()).stream()
                .map(CopypastaEntry::getTrigger)
                .collect(Collectors.toList());
    }

    @Override
    public List<CopypastaEntry> getEntriesInChannel(MessageChannel channel) {
        return copypastaRepository.findAllByChannelId(channel.getIdLong());
    }

    @Override
    public void save(CopypastaEntry entry) {
        CopypastaEntry existing = copypastaRepository.findByChannelIdAndTrigger(entry.getChannelId(), entry.getTrigger());
        if (existing != null) {
            existing.setCopypasta(entry.getCopypasta());
            copypastaRepository.save(existing);
            return;
        }

        copypastaRepository.save(entry);
    }

    @Override
    public boolean delete(MessageChannel channel, String trigger) {
        return copypastaRepository.deleteByChannelIdAndTrigger(channel.getIdLong(), trigger) == 1;
    }
}
