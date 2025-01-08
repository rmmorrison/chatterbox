package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.extension.Messages;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteHistoryRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HistoryCommandListener extends ListenerAdapter {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final QuoteHistoryRepository quoteHistoryRepository;

    public HistoryCommandListener(@Autowired QuoteHistoryRepository quoteHistoryRepository) {
        this.quoteHistoryRepository = quoteHistoryRepository;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals("history")) return;

        event.deferReply().queue();

        quoteHistoryRepository.findFirstByChannelIdOrderByEmittedDesc(event.getChannel().getIdLong())
                .ifPresentOrElse(quoteHistory -> {
                            Message message = event.getChannel().retrieveMessageById(quoteHistory.getQuote().getMessageId()).complete();
                            if (message == null) {
                                LOGGER.error("Found history with message ID {} but could not retrieve the message.", quoteHistory.getQuote().getMessageId());
                                Messages.sendError(event.getHook(), "The original message could not be found.")
                                        .setEphemeral(true).queue();
                                return;
                            }

                            event.getHook().sendMessageEmbeds(createEmbed(message)).queue();
                        },
                        () -> Messages.sendError(event.getHook(), "There's no history for this channel yet.")
                                .setEphemeral(true).queue());
    }

    private MessageEmbed createEmbed(Message message) {
        Member author = message.getGuild().getMemberById(message.getAuthor().getId());

        return new EmbedBuilder()
                .setTitle(message.getContentDisplay(), message.getJumpUrl())
                .setAuthor(author != null ? author.getEffectiveName() : "[deleted]",
                        null,
                        author != null ? author.getUser().getAvatarUrl() : null)
                .build();
    }
}
