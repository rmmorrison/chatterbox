package ca.ryanmorrison.chatterbox.configuration;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "discord.commands", value = "register", havingValue = "true")
public class CommandsRegistrar {

    private final JDA jda;

    public CommandsRegistrar(@Autowired JDA jda) {
        this.jda = jda;
    }

    @PostConstruct
    private void register() {
        jda.updateCommands().addCommands(
                Commands.slash("history", "Interactively displays quote history for the current channel.")
        ).queue();
    }
}
