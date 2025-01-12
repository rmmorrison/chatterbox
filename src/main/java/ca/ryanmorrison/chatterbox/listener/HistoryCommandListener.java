package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.QuoteConstants;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.persistence.repository.QuoteHistoryRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HistoryCommandListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String buttonPrefix = String.format("%s-", QuoteConstants.HISTORY_COMMAND_NAME);
    private final QuoteHistoryRepository quoteHistoryRepository;

    public HistoryCommandListener(@Autowired QuoteHistoryRepository quoteHistoryRepository) {
        this.quoteHistoryRepository = quoteHistoryRepository;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(QuoteConstants.HISTORY_COMMAND_NAME)) return;

        event.deferReply().setEphemeral(true).queue();

        int count = quoteHistoryRepository.countByChannelId(event.getChannel().getIdLong());
        if (count == 0) {
            event.getHook().sendMessageEmbeds(buildErrorResponse("There's no history for this channel yet.")).queue();
            return;
        }

        quoteHistoryRepository.findFirstByChannelIdOrderByEmittedDesc(event.getChannel().getIdLong())
                .ifPresentOrElse(quoteHistory -> {
                    event.getChannel().retrieveMessageById(quoteHistory.getQuote().getMessageId()).queue(message -> {
                        if (message == null) {
                            log.error("Found history with message ID {} but could not retrieve the message.", quoteHistory.getQuote().getMessageId());
                            event.getHook().sendMessageEmbeds(buildErrorResponse("The original message could not be found.")).queue();
                            return;
                        }

                        event.getHook().sendMessageEmbeds(createEmbed(message))
                                .addActionRow(createButtons(0, count))
                                .queue();
                    });
                }, () -> event.getHook().sendMessageEmbeds(buildErrorResponse("There's no history for this channel yet.")).queue());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(buttonPrefix)) return;

        event.deferEdit().queue();

        int index = Integer.parseInt(event.getComponentId().substring(buttonPrefix.length()));
        PageRequest pageRequest = PageRequest.of(index, 1);

        quoteHistoryRepository.findByChannelIdOrderByEmittedDesc(event.getChannelIdLong(), pageRequest)
                .stream().findFirst().ifPresentOrElse(quoteHistory -> {
                    int count = quoteHistoryRepository.countByChannelId(event.getChannel().getIdLong());
                    event.getChannel().retrieveMessageById(quoteHistory.getQuote().getMessageId()).queue(message -> {
                        if (message == null) {
                            log.error("Found history with message ID {} but could not retrieve the message.", quoteHistory.getQuote().getMessageId());
                            event.getHook().sendMessageEmbeds(buildErrorResponse("The original message could not be found.")).queue();
                            return;
                        }

                        event.getHook().editOriginalEmbeds(createEmbed(message))
                                .and(event.getHook().editOriginalComponents(ActionRow.of(createButtons(index, count))))
                                .queue();
                    });
                },
                () -> {
                    log.error("Expected page based on index {} for channel ID {} but none was found.", index, event.getChannelId());
                    event.getHook().sendMessageEmbeds(buildErrorResponse("An error occurred while loading the next page - possibly a message was deleted."))
                            .queue();
                });
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

    private List<Button> createButtons(int index, int max) {
        return List.of(
                Button.primary(String.format("%s%d", buttonPrefix, index + 1), "Previous")
                        .withDisabled(index == (max - 1)),
                Button.secondary(String.format("%s%d", buttonPrefix, index - 1), "Next")
                        .withDisabled(index == 0)
        );
    }
}
