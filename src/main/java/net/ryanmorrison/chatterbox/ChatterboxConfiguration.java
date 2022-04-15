package net.ryanmorrison.chatterbox;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.internal.entities.EntityBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.security.auth.login.LoginException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Configuration
public class ChatterboxConfiguration {

    private final String token;
    private final String activity;
    private final List<ListenerAdapter> listeners;

    public ChatterboxConfiguration(@Value("${discord.token}") String token,
                                   @Value("${discord.activity:listening:everything you say}") String activity,
                                   @Autowired List<ListenerAdapter> listeners) {
        this.token = token;
        this.activity = activity;
        this.listeners = listeners;
    }

    @Bean
    public JDA jda() throws LoginException {
        String[] activityParsed = activity.split(":");
        if (activityParsed.length < 2) {
            throw new IllegalArgumentException("Activity must be provided in the format '<activity type>:<activity status>'.");
        }

        Optional<Activity.ActivityType> type = Arrays.stream(Activity.ActivityType.values())
                .filter(t -> t.name().equalsIgnoreCase(activityParsed[0].toLowerCase()))
                .findFirst();

        if (type.isEmpty()) {
            throw new IllegalArgumentException("Activity type must be a valid type supported by the Discord API.");
        }

        return JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners((Object[]) listeners.toArray(ListenerAdapter[]::new))
                .setActivity(EntityBuilder.createActivity(activityParsed[1], null, type.get()))
                .build();
    }
}
