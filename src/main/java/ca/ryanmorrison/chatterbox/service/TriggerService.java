package ca.ryanmorrison.chatterbox.service;

import ca.ryanmorrison.chatterbox.constants.TriggerConstants;
import ca.ryanmorrison.chatterbox.persistence.entity.Trigger;
import ca.ryanmorrison.chatterbox.persistence.repository.TriggerRepository;
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

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final TriggerRepository triggerRepository;

    public TriggerService(@Autowired TriggerRepository triggerRepository) {
        this.triggerRepository = triggerRepository;
    }

    @Cacheable(TriggerConstants.TRIGGER_CACHE_NAME)
    public Map<Pattern, String> getExpressions(long channelId) {
        log.debug("Populating trigger cache for channel ID {}", channelId);
        return triggerRepository.findAllByChannelId(channelId).stream()
                .collect(Collectors.toMap(trigger -> Pattern.compile(trigger.getChallenge()), Trigger::getResponse));
    }
}
