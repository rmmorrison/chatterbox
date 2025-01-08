package ca.ryanmorrison.chatterbox.configuration;

import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
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
        jda.retrieveCommands().complete().forEach(command -> jda.deleteCommandById(command.getId()).queue());

        jda.updateCommands().addCommands(
                Commands.slash("history", "Interactively displays quote history for the current channel."),
                Commands.slash("trigger", "Manages triggers for the current channel.")
                        .addSubcommands(
                                new SubcommandData("add", "Create a new trigger."),
                                new SubcommandData("edit", "Edit an existing trigger.")
                                        .addOption(OptionType.STRING, "challenge", "The trigger challenge to edit.", true, true),
                                new SubcommandData("delete", "Delete an existing trigger.")
                                        .addOption(OptionType.STRING, "challenge", "The trigger challenge to delete.", true, true)
                        )
        ).queue();
    }
}
