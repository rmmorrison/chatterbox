package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import ca.ryanmorrison.chatterbox.persistence.repository.TriggerRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class TriggerService {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final TriggerRepository triggerRepository;

    public TriggerService(@Autowired TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Cacheable("triggers")
    @Transactional
    public Map<Pattern, String> getExpressions(long channelId) {
        LOGGER.debug("Populating trigger cache");
        return triggerRepository.findAllByChannelId(channelId).stream()
                .collect(Collectors.toMap(trigger -> Pattern.compile(trigger.getChallenge()), Trigger::getResponse));
    }
}
