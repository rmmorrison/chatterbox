package net.ryanmorrison.chatterbox.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import net.ryanmorrison.chatterbox.service.ShoutService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.awt.*;
import java.util.*;

@Component
@Slf4j
public class ShoutListener extends SlashCommandsListenerAdapter {

    private static final String COMMAND_NAME = "shout";
    private static final String LAST_SUBCOMMAND_NAME = "last";

    private final Random random = new Random();
    private final ShoutService shoutService;

    public ShoutListener(@Autowired ShoutService shoutService) {
        this.shoutService = shoutService;
    }

    @Override
    public Collection<SlashCommandData> getSupportedCommands() {
        Collection<SubcommandData> subcommands = new ArrayList<>();
        subcommands.add(new SubcommandData(LAST_SUBCOMMAND_NAME, "Displays the last returned quote in this channel, if possible"));

        return Collections.singleton(Commands.slash(COMMAND_NAME, "Interact with the quote database")
                .addSubcommands(subcommands));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        if (!event.getName().equals(COMMAND_NAME)) {
            // this command is not for us, return
            return;
        }

        // let Discord know we're working on it...
        event.deferReply().queue();

        shoutService.getHistory(event.getChannel()).ifPresentOrElse(message -> {
            Member member = message.getMember();
            String authorName = member != null ? member.getEffectiveName() : message.getAuthor().getName();
            String iconUrl = message.getAuthor().getEffectiveAvatarUrl();

            MessageEmbed embed = new EmbedBuilder()
                    .setColor(new Color(
                            random.nextInt(255),
                            random.nextInt(255),
                            random.nextInt(255)
                    ))
                    .setTimestamp(message.getTimeCreated())
                    .setAuthor(authorName, null, iconUrl)
                    .setDescription(message.getContentDisplay())
                    .addField(
                            "Link to Context",
                            "https://discordapp.com/channels/" + message.getChannel().getId() + "/" + message.getChannel().getId() + "/" + message.getId(),
                            false
                    )
                    .build();

            event.getHook().sendMessage(new MessageBuilder()
                    .setEmbeds(embed).build()).queue();
        }, () -> event.getHook().sendMessage("It doesn't look like anyone has shouted in this channel yet.")
                .setEphemeral(true).queue());
    }

    @Override
    @Transactional
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (contentIsNotShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            log.debug("Received message \"{}\" in channel {} which does not match shout, ignoring.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
            return;
        }

        Optional<Message> randomMessage = shoutService.save(event.getMessage());
        randomMessage.ifPresent(message -> event.getChannel().sendMessage(
                new MessageBuilder()
                        .append(message.getContentDisplay(), MessageBuilder.Formatting.BOLD)
                        .build()).queue());
    }

    @Override
    @Transactional
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        if (contentIsNotShout(event.getAuthor(), event.getMessage().getContentDisplay())) {
            log.debug("Received updated message \"{}\" in channel {} which does not match shout, " +
                            "deleting original shout if it exists.",
                    event.getMessage().getContentDisplay(), event.getChannel().getIdLong());

            shoutService.delete(event.getMessage());
            return;
        }

        log.debug("Received updated message \"{}\" in channel {} which matches shout, calling update if it exists.",
                event.getMessage().getContentDisplay(), event.getChannel().getIdLong());
        this.shoutService.update(event.getMessage());
    }

    @Override
    @Transactional
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        log.debug("Received deleted message with ID {} in channel {}, deleting corresponding shout if it exists.",
                event.getMessageIdLong(), event.getChannel().getIdLong());
        shoutService.delete(event.getMessageIdLong());
    }

    private boolean contentIsNotShout(User author, String content) {
        if (author.isBot()) return true;
        if (!content.equals(content.toUpperCase())) return true;
        if (content.startsWith("$")) return true; // special case for currency
        if (content.startsWith("http")) return true; // special case for links
        if (content.length() <= 5) return true;

        int nonCharCount = 0;
        for (char current : content.toCharArray()) {
            if (!Character.isLetter(current)) {
                nonCharCount++;
            }
        }

        return (int) (((float) nonCharCount / content.length()) * 100) > 50;
    }
}
