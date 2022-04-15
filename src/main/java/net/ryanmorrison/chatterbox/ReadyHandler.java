package net.ryanmorrison.chatterbox;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public class ReadyHandler extends ListenerAdapter {

    private final boolean forceGuildRegistration;
    private final List<ListenerAdapter> listeners;

    public ReadyHandler(@Value("${discord.forceGuildRegistration:false}") boolean forceGuildRegistration,
                        @Autowired List<ListenerAdapter> listeners) {
        this.forceGuildRegistration = forceGuildRegistration;
        this.listeners = listeners;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        Collection<SlashCommandData> combinedCommands = listeners.stream()
                .filter(listener -> listener instanceof SlashCommandsListenerAdapter)
                .map(listener -> (SlashCommandsListenerAdapter) listener)
                .flatMap(listener -> listener.getSupportedCommands().stream())
                .toList();

        if (isDebugModeEnabled()) handleGuildLevelCommandRegistration(combinedCommands, event.getJDA());
        else handleGlobalCommandRegistration(combinedCommands, event.getJDA());
    }

    private void handleGuildLevelCommandRegistration(Collection<SlashCommandData> combinedCommands, JDA instance) {
        List<CommandListUpdateAction> updateActions = new ArrayList<>();
        List<RestAction<Void>> deleteActions = new ArrayList<>(instance.retrieveCommands().complete().stream()
                .map(command -> instance.deleteCommandById(command.getId())).toList());

        if (deleteActions.size() > 0) {
            log.info("Will remove {} pre-existing globally registered commands (debug mode).", deleteActions.size());
        }

        for (Guild guild : instance.getGuilds()) {
            log.info("Registering {} total commands in server \"{}\" (debug mode).", combinedCommands.size(),
                    guild.getName());

            List<Command> guildCommands = guild.retrieveCommands().complete();
            deleteActions.addAll(guildCommands.stream()
                    .map(command -> guild.deleteCommandById(command.getId())).toList());
            log.info("Will remove {} pre-existing commands in server \"{}\" (debug mode).", guildCommands.size(),
                    guild.getName());

            updateActions.add(guild.updateCommands().addCommands(combinedCommands.toArray(SlashCommandData[]::new)));
        }

        if (!deleteActions.isEmpty()) {
            log.info("Executing bulk command delete operation against the Discord API (debug mode).");
            executeBulkDeleteOperation(deleteActions);

            // wait a few seconds before registering new commands to decrease the likelihood Discord will ratelimit us
            log.info("Sleeping a short while to avoid ratelimits.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.error("Sleep between Discord REST interactions interrupted.", e);
            }
        }

        if (!updateActions.isEmpty()) {
            log.info("Executing bulk command update operation against the Discord API (debug mode).");
            CommandListUpdateAction firstElement = updateActions.get(0);
            updateActions.remove(firstElement);
            RestAction<List<List<Command>>> bulkUpdateAction = firstElement.zip(firstElement, updateActions.toArray(RestAction[]::new));
            bulkUpdateAction.complete();
        }
        else log.warn("No guilds were discovered to register commands to (debug mode). Nothing to do.");
    }

    private void handleGlobalCommandRegistration(Collection<SlashCommandData> combinedCommands, JDA instance) {
        List<RestAction<Void>> deleteActions = new ArrayList<>();
        List<Command> currentCommandList = instance.retrieveCommands().complete();

        for (Command command : currentCommandList) {
            deleteActions.add(instance.deleteCommandById(command.getId()));
            log.debug("Prepared to remove command with ID {} from global command registry.", command.getId());
        }

        // make sure we remove any guild-level commands left over from debug sessions
        for (Guild guild : instance.getGuilds()) {
            deleteActions.addAll(guild.retrieveCommands().complete().stream()
                    .map(command -> guild.deleteCommandById(command.getId())).toList());
        }

        if (!deleteActions.isEmpty()) {
            log.info("Executing bulk command delete operation against the Discord API.");
            executeBulkDeleteOperation(deleteActions);

            // wait a few seconds before registering new commands to decrease the likelihood Discord will ratelimit us
            log.info("Sleeping a short while to avoid ratelimits.");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.error("Sleep between Discord REST interactions interrupted.", e);
            }
        }

        log.info("Registering {} commands globally.", combinedCommands.size());
        instance.updateCommands().addCommands(combinedCommands.toArray(SlashCommandData[]::new)).queue();
    }

    private void executeBulkDeleteOperation(List<RestAction<Void>> deleteActions) {
        if (!deleteActions.isEmpty()) {
            RestAction<Void> firstElement = deleteActions.get(0);
            deleteActions.remove(firstElement);
            RestAction<List<Void>> bulkDeleteAction = firstElement.zip(firstElement, deleteActions.toArray(RestAction[]::new));
            bulkDeleteAction.complete(); // complete instead of queue so we know when it's done and can wait
        }
    }

    private boolean isDebugModeEnabled() {
        // big hacks here: check if the JDWP (Java Wire Debug Protocol) agent is being used
        return forceGuildRegistration ||
                ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(s -> s.contains("jdwp"));
    }
}
