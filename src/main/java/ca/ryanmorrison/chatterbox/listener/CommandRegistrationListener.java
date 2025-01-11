package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.QuoteConstants;
import ca.ryanmorrison.chatterbox.constants.TriggerConstants;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(prefix = "discord.commands", value = "register", havingValue = "true")
public class CommandRegistrationListener extends ListenerAdapter {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final boolean global;

    public CommandRegistrationListener(@Value("${discord.commands.global:false}") boolean global) {
        this.global = global;
    }

    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        List<CommandData> knownCommands = getCommands();

        LOGGER.info("Starting command registration flow");
        jda.retrieveCommands().queue(commands -> {
            LOGGER.debug("Discovered {} global commands to remove", commands.size());
            List<CompletableFuture<Void>> futures = scheduleGlobalCommandDeletions(jda, commands);

            LOGGER.debug("Waiting for all global command deletions to complete (total {})", futures.size());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            LOGGER.debug("All global command deletions have completed");
            if (global) {
                LOGGER.debug("Configured to register commands globally, not on a guild-level");
                jda.updateCommands().addCommands(knownCommands).queue(success ->
                                LOGGER.info("Successfully registered {} global commands", knownCommands.size()),
                        throwable -> LOGGER.error("An error occurred while registering global commands", throwable));
            }
        });

        jda.getGuilds().forEach(guild -> guild.retrieveCommands().queue(guildCommands -> {
            LOGGER.debug("Discovered {} commands to remove in guild {}", guildCommands.size(), guild.getId());
            List<CompletableFuture<Void>> futures = scheduleGuildCommandDeletions(guild, guildCommands);

            LOGGER.debug("Waiting for all guild command deletions to complete (total {})", futures.size());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            LOGGER.debug("All guild command deletions have completed");
            if (!global) {
                LOGGER.debug("Configured to register commands on a guild-level");
                guild.updateCommands().addCommands(knownCommands).queue(success ->
                                LOGGER.info("Successfully registered {} commands in guild {}", knownCommands.size(), guild.getId()),
                        throwable -> LOGGER.error("An error occurred while registering commands in guild {}", guild.getId(), throwable));
            }
        }));
    }

    private List<CommandData> getCommands() {
        return List.of(
                Commands.slash(QuoteConstants.HISTORY_COMMAND_NAME, "Interactively displays quote history for the current channel."),
                Commands.slash(TriggerConstants.TRIGGER_COMMAND_NAME, "Manages triggers for the current channel.")
                        .addSubcommands(
                                new SubcommandData(TriggerConstants.ADD_SUBCOMMAND_NAME, "Create a new trigger."),
                                new SubcommandData(TriggerConstants.EDIT_SUBCOMMAND_NAME, "Edit an existing trigger.")
                                        .addOption(OptionType.STRING, TriggerConstants.CHALLENGE_OPTION_NAME, "The trigger challenge to edit.", true, true),
                                new SubcommandData(TriggerConstants.DELETE_SUBCOMMAND_NAME, "Delete an existing trigger.")
                                        .addOption(OptionType.STRING, TriggerConstants.CHALLENGE_OPTION_NAME, "The trigger challenge to delete.", true, true)
                        )
        );
    }

    private List<CompletableFuture<Void>> scheduleGlobalCommandDeletions(JDA jda, List<Command> commands) {
        LOGGER.debug("Scheduling {} global commands for deletion", commands.size());
        return commands.stream()
                .map(command -> {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    jda.deleteCommandById(command.getId()).queue(future::complete, future::completeExceptionally);
                    return future;
                }).toList();
    }

    private List<CompletableFuture<Void>> scheduleGuildCommandDeletions(Guild guild, List<Command> commands) {
        LOGGER.debug("Scheduling {} commands for deletion in guild {}", commands.size(), guild.getId());
        return commands.stream()
                .map(command -> {
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    guild.deleteCommandById(command.getId()).queue(future::complete, future::completeExceptionally);
                    return future;
                }).toList();
    }
}
