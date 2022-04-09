package net.ryanmorrison.chatterbox;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.security.auth.login.LoginException;

@Configuration
public class ChatterboxConfiguration {

    private final String token;

    public ChatterboxConfiguration(@Value("${discord.token}") String token) {
        this.token = token;
    }

    @Bean
    public JDA jda() throws LoginException {
        return JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                .setActivity(Activity.listening("everything you say"))
                .build();
    }
}
