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
    private static final String COUNT_SUBCOMMAND_NAME = "count";
    private static final String TOP10_SUBCOMMAND_NAME = "top10";

    private final Random random = new Random();
    private final ShoutService shoutService;

    public ShoutListener(@Autowired ShoutService shoutService) {
        this.shoutService = shoutService;
    }

    @Override
    public Collection<SlashCommandData> getSupportedCommands() {
        Collection<SubcommandData> subcommands = new ArrayList<>();
        subcommands.add(new SubcommandData(LAST_SUBCOMMAND_NAME, "Displays the last returned quote in this channel, if possible"));
        subcommands.add(new SubcommandData(COUNT_SUBCOMMAND_NAME, "Displays the number of quotes in the database for this channel"));
        subcommands.add(new SubcommandData(TOP10_SUBCOMMAND_NAME, "Displays the top 10 most active shouters in this channel"));

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

        if (event.getSubcommandName() == null) {
            event.reply("You need to provide the full command for me to execute it! :(")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // let Discord know we're working on it...
        event.deferReply().queue();

        switch (event.getSubcommandName()) {
            case LAST_SUBCOMMAND_NAME -> handleLast(event);
            case COUNT_SUBCOMMAND_NAME -> handleCount(event);
            case TOP10_SUBCOMMAND_NAME -> handleTop10(event);
            default -> event.reply("I don't support that action! :(")
                    .setEphemeral(true)
                    .queue();
        }
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

    private void handleLast(SlashCommandInteractionEvent event) {
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

    private void handleCount(SlashCommandInteractionEvent event) {
        long count = shoutService.count(event.getChannel());
        String quotesPluralized = count == 1 ? "quote" : "quotes";

        event.getHook().sendMessage(new MessageBuilder()
                .append(event.getChannel()).append(" has ").append(count).append(" ")
                .append(quotesPluralized).append(" ").append("in the database.")
                .build()).queue();
    }

    private void handleTop10(SlashCommandInteractionEvent event) {
        Map<Member, Long> top10 = shoutService.getTop10Users(event.getChannel(), event.getGuild());
        if (top10.isEmpty()) {
            event.getHook().sendMessage("It doesn't look like anyone has shouted in this channel yet.")
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("Top 10 Loudmouths in #" + event.getChannel().getName())
                .setDescription("Who's got the biggest chatterbox around these parts?");

        int position = 1;
        for (Map.Entry<Member, Long> entry : top10.entrySet()) {
            String quotesPluralized = entry.getValue() == 1 ? "quote" : "quotes";
            builder.addField("#" + position + ": " + entry.getKey().getEffectiveName(),
                    "with " + entry.getValue() + " " + quotesPluralized,
                    true);
            position++;
        }

        event.getHook().sendMessage(new MessageBuilder()
                .setEmbeds(builder.build())
                .build()).queue();
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
