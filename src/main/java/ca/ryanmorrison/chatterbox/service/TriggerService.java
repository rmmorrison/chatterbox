package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.constants.TriggerConstants;
import ca.ryanmorrison.chatterbox.exception.DuplicateResourceException;
import ca.ryanmorrison.chatterbox.exception.ResourceNotFoundException;
import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import ca.ryanmorrison.chatterbox.persistence.repository.TriggerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TriggerService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TriggerRepository triggerRepository;

    public TriggerService(@Autowired TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Transactional
    @Cacheable(TriggerConstants.TRIGGER_CACHE_NAME)
    public Map<Pattern, String> getExpressions(long channelId) {
        log.debug("Populating trigger cache for channel ID {}", channelId);
        return triggerRepository.findAllByChannelId(channelId).stream()
                .collect(Collectors.toMap(trigger -> Pattern.compile(trigger.getChallenge()), Trigger::getResponse));
    }

    @Transactional
    public Optional<Trigger> find(long channelId, String challenge) {
        return triggerRepository.findByChannelIdAndChallenge(channelId, challenge);
    }

    @Transactional
    public void save(Trigger trigger) throws DuplicateResourceException {
        Optional<Trigger> existing = triggerRepository.findByChannelIdAndChallenge(trigger.getChannelId(), trigger.getChallenge());
        if (existing.isPresent()) {
            throw new DuplicateResourceException("A trigger with that challenge already exists.");
        }

        triggerRepository.save(trigger);
    }

    @Transactional
    public void edit(int id, String newResponse) throws ResourceNotFoundException {
        Optional<Trigger> existing = triggerRepository.findById(id);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("No trigger found with that ID.");
        }

        Trigger trigger = existing.get();
        trigger.setResponse(newResponse);
        triggerRepository.save(trigger);
    }

    @Transactional
    public void delete(long channelId, String challenge) throws ResourceNotFoundException {
        Optional<Trigger> existing = triggerRepository.findByChannelIdAndChallenge(channelId, challenge);
        if (existing.isEmpty()) {
            throw new ResourceNotFoundException("No trigger found with that challenge.");
        }

        triggerRepository.delete(existing.get());
    }
}
