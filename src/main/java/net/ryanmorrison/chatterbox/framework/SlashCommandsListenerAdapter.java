package net.ryanmorrison.chatterbox.framework;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Collection;

public abstract class SlashCommandsListenerAdapter extends ListenerAdapter {

    public abstract Collection<SlashCommandData> getSupportedCommands();
}
