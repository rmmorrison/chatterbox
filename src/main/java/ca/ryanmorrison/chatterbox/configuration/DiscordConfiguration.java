package ca.ryanmorrison.chatterbox.configuration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.List;

@Configuration
public class DiscordConfiguration {

    private final DiscordProperties discordProperties;
    private final List<ListenerAdapter> listeners;

    public DiscordConfiguration(@Autowired DiscordProperties discordProperties, @Autowired List<ListenerAdapter> listeners) {
        this.discordProperties = discordProperties;
        this.listeners = listeners;
    }

    @Bean
    public JDA jda() {
        return JDABuilder.createDefault(discordProperties.getToken(), EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(listeners.toArray())
                .setActivity(Activity.listening(discordProperties.getActivity()))
                .build();
    }
}
