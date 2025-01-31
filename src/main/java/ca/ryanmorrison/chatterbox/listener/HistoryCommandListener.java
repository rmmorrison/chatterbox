package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.QuoteConstants;
import ca.ryanmorrison.chatterbox.exception.ResourceNotFoundException;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.extension.Page;
import ca.ryanmorrison.chatterbox.persistence.entity.QuoteHistory;
import ca.ryanmorrison.chatterbox.service.QuoteService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HistoryCommandListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String buttonPrefix = String.format("%s-", QuoteConstants.HISTORY_COMMAND_NAME);
    private final QuoteService quoteService;

    public HistoryCommandListener(@Autowired QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(QuoteConstants.HISTORY_COMMAND_NAME)) return;

        event.deferReply().setEphemeral(true).queue();

        Optional<Page<Message>> historyOptional;
        try {
            historyOptional = queryHistory(event.getChannel(), 0);
        } catch (ResourceNotFoundException e) {
            event.getHook().sendMessageEmbeds(buildErrorResponse("The original message could not be found.")).queue();
            return;
        }

        if (historyOptional.isEmpty()) {
            event.getHook().sendMessageEmbeds(buildErrorResponse("There's no history for this channel yet.")).queue();
            return;
        }

        Page<Message> historyPage = historyOptional.get();
        event.getHook().sendMessageEmbeds(createEmbed(historyPage.getObject()))
                .addActionRow(createButtons(0, historyPage.getCount()))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith(buttonPrefix)) return;

        event.deferEdit().queue();

        int index = Integer.parseInt(event.getComponentId().substring(buttonPrefix.length()));
        Optional<Page<Message>> historyOptional;
        try {
            historyOptional = queryHistory(event.getChannel(), index);
        } catch (ResourceNotFoundException e) {
            log.error("Found history with message ID {} but could not retrieve the message.", e.getIdentifier());
            event.getHook().sendMessageEmbeds(buildErrorResponse(e.getMessage())).queue();
            return;
        }

        if (historyOptional.isEmpty()) {
            log.error("Expected page based on index {} for channel ID {} but none was found.", index, event.getChannelId());
            event.getHook().sendMessageEmbeds(buildErrorResponse("An error occurred while loading the next page - possibly a message was deleted."))
                    .queue();
            return;
        }

        Page<Message> historyPage = historyOptional.get();
        event.getHook().editOriginalEmbeds(createEmbed(historyPage.getObject()))
                .and(event.getHook().editOriginalComponents(ActionRow.of(createButtons(historyPage.getIndex(), historyPage.getCount()))))
                .queue();
    }

    private Optional<Page<Message>> queryHistory(MessageChannelUnion channel, int index) throws ResourceNotFoundException {
        Optional<Page<QuoteHistory>> pageOptional = quoteService.findQuoteHistory(channel.getIdLong(), index);
        if (pageOptional.isEmpty()) {
            return Optional.empty();
        }

        Page<QuoteHistory> historyPage = pageOptional.get();
        QuoteHistory history = historyPage.getObject();

        Message message = channel.retrieveMessageById(history.getQuote().getMessageId()).complete();
        if (message == null) {
            throw new ResourceNotFoundException("The original message could not be found.", "quote history", String.valueOf(history.getQuote().getMessageId()));
        }

        return Optional.of(new Page.Builder<Message>()
                .setObject(message)
                .setCount(historyPage.getCount())
                .setIndex(historyPage.getIndex())
                .build()
        );
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
