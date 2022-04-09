package net.ryanmorrison.chatterbox;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
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
        listeners.stream()
                .filter(listener -> listener instanceof SlashCommandsListenerAdapter)
                .map(listener -> (SlashCommandsListenerAdapter) listener)
                .forEach(listener -> {
                    if (listener.getSupportedCommands().isEmpty()) {
                        log.warn("Listener {} has no commands to register even though it extends the " +
                                "SlashCommandsListenerAdapter abstract class. This probably isn't desired.",
                                listener.getClass().getName());
                        return; // continue as there's nothing we can do with this listener
                    }

                    if (isDebugModeEnabled()) {
                        event.getJDA().getGuilds().forEach(guild -> {
                            log.info("Listener {} registering {} commands in server \"{}\" (debug mode).",
                                    listener.getClass().getName(), listener.getSupportedCommands().size(),
                                    guild.getName());
                            guild.updateCommands()
                                    .addCommands(listener.getSupportedCommands().toArray(SlashCommandData[]::new)).queue();
                        });
                    }
                    else {
                        log.info("Listener {} registering {} commands globally.",
                                listener.getClass().getName(), listener.getSupportedCommands().size());
                        listener.getSupportedCommands().forEach(command -> event.getJDA().upsertCommand(command).queue());
                    }
                });
    }

    private boolean isDebugModeEnabled() {
        // big hacks here: check if the JDWP (Java Wire Debug Protocol) agent is being used
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(s -> s.contains("jdwp"));
    }
}
