package net.ryanmorrison.chatterbox;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public class ReadyHandler extends ListenerAdapter {

    private final List<ListenerAdapter> listeners;

    public ReadyHandler(@Autowired List<ListenerAdapter> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        Collection<SlashCommandData> combinedCommands = listeners.stream()
                .filter(listener -> listener instanceof SlashCommandsListenerAdapter)
                .map(listener -> (SlashCommandsListenerAdapter) listener)
                .flatMap(listener -> listener.getSupportedCommands().stream())
                .toList();

        if (isDebugModeEnabled()) {
            CommandListUpdateAction combinedActions = null;
            for (Guild guild : event.getJDA().getGuilds()) {
                log.info("Registering {} total commands in server \"{}\" (debug mode).", combinedCommands.size(),
                        guild.getName());
                if (combinedActions == null) {
                    combinedActions = guild.updateCommands().addCommands(combinedCommands.toArray(SlashCommandData[]::new));
                } else {
                    combinedActions.zip(guild.updateCommands().addCommands(combinedCommands.toArray(SlashCommandData[]::new)));
                }
            }

            if (combinedActions != null) {
                combinedActions.queue();
            }
            else
            {
                log.warn("No guilds were discovered to register commands to (debug mode). Nothing to do.");
            }
        }
        else {
            log.info("Registering {} commands globally.", combinedCommands.size());
            event.getJDA().updateCommands().addCommands(combinedCommands.toArray(SlashCommandData[]::new)).queue();
        }
    }

    private boolean isDebugModeEnabled() {
        // big hacks here: check if the JDWP (Java Wire Debug Protocol) agent is being used
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(s -> s.contains("jdwp"));
    }
}
