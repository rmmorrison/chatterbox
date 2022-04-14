package net.ryanmorrison.chatterbox;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.security.auth.login.LoginException;
import java.util.List;

@Configuration
public class ChatterboxConfiguration {

    private final String token;
    private final List<ListenerAdapter> listeners;

    public ChatterboxConfiguration(@Value("${discord.token}") String token, @Autowired List<ListenerAdapter> listeners) {
        this.token = token;
        this.listeners = listeners;
    }

    @Bean
    public JDA jda() throws LoginException {
        return JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners((Object[]) listeners.toArray(ListenerAdapter[]::new))
                .setActivity(Activity.listening("everything you say"))
                .build();
    }
}
