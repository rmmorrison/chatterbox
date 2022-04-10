package net.ryanmorrison.chatterbox.listener;

import lombok.Builder;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import net.ryanmorrison.chatterbox.persistence.model.CopypastaEntry;
import net.ryanmorrison.chatterbox.service.CopypastaService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Component
public class CopypastaListener extends SlashCommandsListenerAdapter {

    private static final String COMMAND_NAME = "copypasta";
    private static final String TRIGGERS_SUBCOMMAND_NAME = "triggers";
    private static final String ADD_SUBCOMMAND_NAME = "add";
    private static final String UPDATE_SUBCOMMAND_NAME = "update";
    private static final String DELETE_SUBCOMMAND_NAME = "delete";

    private static final String TRIGGER_OPTION_NAME = "trigger";
    private static final String COPYPASTA_OPTION_NAME = "copypasta";

    private final ConcurrentMap<Long, List<ComputedCopypastaEntry>> copypastaCache = new ConcurrentHashMap<>();

    private final CopypastaService copypastaService;

    public CopypastaListener(@Autowired CopypastaService copypastaService) {
        this.copypastaService = copypastaService;
    }

    @Override
    @Transactional
    public Collection<SlashCommandData> getSupportedCommands() {
        final String TRIGGER_DESCRIPTION = "A regular expression defining the trigger in which copypasta should be returned for";
        final String COPYPASTA_DESCRIPTION = "The copypasta to return";

        SubcommandData triggersSubcommand = new SubcommandData(TRIGGERS_SUBCOMMAND_NAME, "Displays already configured triggers in the channel this command is run in");

        SubcommandData addSubcommand = new SubcommandData(ADD_SUBCOMMAND_NAME, "Adds copypasta to be automatically returned based on a provided trigger")
                .addOption(OptionType.STRING, TRIGGER_OPTION_NAME, TRIGGER_DESCRIPTION, true)
                .addOption(OptionType.STRING, COPYPASTA_OPTION_NAME, COPYPASTA_DESCRIPTION, true);

        SubcommandData updateSubcommand = new SubcommandData(UPDATE_SUBCOMMAND_NAME, "Edits copypasta returned by an already configured trigger")
                .addOption(OptionType.STRING, TRIGGER_OPTION_NAME, "The trigger regular expression to edit", true)
                .addOption(OptionType.STRING, COPYPASTA_OPTION_NAME, COPYPASTA_DESCRIPTION, true);

        SubcommandData deleteSubcommand = new SubcommandData(DELETE_SUBCOMMAND_NAME, "Deletes a pre-configured copypasta trigger")
                .addOption(OptionType.STRING, TRIGGER_OPTION_NAME, "The trigger regular expression to delete", true);

        return Collections.singleton(Commands.slash(COMMAND_NAME, "Manages copypasta displayed by the bot under predefined triggers")
                .addSubcommands(triggersSubcommand, addSubcommand, updateSubcommand, deleteSubcommand));
    }

    @Override
    @Transactional
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // don't accidentally reply to ourselves :)
        if (event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) return;

        List<ComputedCopypastaEntry> entries = copypastaCache.computeIfAbsent(event.getChannel().getIdLong(), key ->
                copypastaService.getEntriesInChannel(event.getChannel()).stream()
                        .map(entry -> ComputedCopypastaEntry.builder()
                                .trigger(Pattern.compile(entry.getTrigger()))
                                .copypasta(entry.getCopypasta())
                                .build())
                        .collect(Collectors.toList()));

        String content = event.getMessage().getContentDisplay();
        entries.forEach(entry -> {
            if (entry.getTrigger().matcher(content).find()) {
                event.getMessage().reply(entry.getCopypasta()).queue();
            }
        });
    }

    @Override
    @Transactional
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        if (!event.getName().equalsIgnoreCase(COMMAND_NAME)) return;

        if (event.getSubcommandName() == null) {
            event.reply("You need to provide the full command for me to execute it! :(")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member callingMember = event.getMember();
        if (callingMember == null || !callingMember.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("You don't have permission to access this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // let Discord know we're working on it...
        event.deferReply().queue();

        switch (event.getSubcommandName()) {
            case TRIGGERS_SUBCOMMAND_NAME -> handleTriggers(event);
            case ADD_SUBCOMMAND_NAME -> handleAdd(event);
            case UPDATE_SUBCOMMAND_NAME -> handleUpdate(event);
            case DELETE_SUBCOMMAND_NAME -> handleDelete(event);
            default -> event.reply("I don't support that action! :(")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleTriggers(SlashCommandInteractionEvent event) {
        List<CopypastaEntry> entries = copypastaService.getEntriesInChannel(event.getChannel());
        if (entries == null || entries.isEmpty()) {
            event.getHook().sendMessage("No triggers have been configured for this channel yet!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Configured Triggers for #" + event.getChannel().getName());
        entries.forEach(entry -> builder.addField(entry.getTrigger(),
                entry.getCopypasta().length() <= 200 ? "Displays: \"" + entry.getCopypasta() + "\"" :
                        "Displays a message with " + entry.getCopypasta().length() + " characters",
                false));

        event.getHook().sendMessage(new MessageBuilder()
                .setEmbeds(builder.build())
                .build()).queue();
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        OptionMapping triggerOption = event.getOption(TRIGGER_OPTION_NAME);
        OptionMapping copypastaOption = event.getOption(COPYPASTA_OPTION_NAME);

        if (triggerOption == null || copypastaOption == null) {
            event.getHook().sendMessage("You must provide both trigger and copypasta options to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        // make sure regex compiles
        try {
            Pattern.compile(triggerOption.getAsString());
        } catch (PatternSyntaxException e) {
            event.getHook().sendMessage("You have an error in your trigger regular expression syntax - please review and try again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        copypastaService.save(CopypastaEntry.builder()
                .channelId(event.getChannel().getIdLong())
                .trigger(triggerOption.getAsString())
                .copypasta(copypastaOption.getAsString())
                .build());

        // evict the cache for this channel
        copypastaCache.remove(event.getChannel().getIdLong());

        event.getHook().sendMessage(new MessageBuilder()
                .append("Done. Trigger `").append(triggerOption.getAsString()).append("` added to channel ")
                .append(event.getChannel())
                .append(".")
                .build()).queue();
    }

    private void handleUpdate(SlashCommandInteractionEvent event) {
        // shhh...editing is the same as saving :)
        handleAdd(event);
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        OptionMapping triggerOption = event.getOption(TRIGGER_OPTION_NAME);

        if (triggerOption == null) {
            event.getHook().sendMessage("You must provide a trigger option to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        copypastaService.delete(event.getChannel(), triggerOption.getAsString());

        // evict the cache for this channel
        copypastaCache.remove(event.getChannel().getIdLong());

        event.getHook().sendMessage(new MessageBuilder()
                .append("Done. Trigger `").append(triggerOption.getAsString()).append("` removed from channel ")
                .append(event.getChannel())
                .append(".")
                .build()).queue();
    }

    @Builder
    private static class ComputedCopypastaEntry {

        @Getter
        private Pattern trigger;

        @Getter
        private String copypasta;
    }
}
