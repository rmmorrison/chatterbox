package ca.ryanmorrison.chatterbox.listener;

import ca.ryanmorrison.chatterbox.constants.RSSConstants;
import ca.ryanmorrison.chatterbox.exception.DuplicateResourceException;
import ca.ryanmorrison.chatterbox.exception.ResourceNotFoundException;
import ca.ryanmorrison.chatterbox.extension.FormattedListenerAdapter;
import ca.ryanmorrison.chatterbox.integration.rss.service.FeedService;
import ca.ryanmorrison.chatterbox.persistence.entity.Feed;
import com.rometools.rome.feed.synd.SyndFeed;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class RSSFeedCommandListener extends FormattedListenerAdapter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final String stringSelectId = String.format("%s-delete", RSSConstants.RSS_COMMAND_NAME);
    private final FeedService feedService;

    private UrlValidator urlValidator = new UrlValidator(new String[] {"http", "https"});

    public RSSFeedCommandListener(@Autowired FeedService feedService) {
        this.feedService = feedService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;
        if (!event.getName().equals(RSSConstants.RSS_COMMAND_NAME)) return;

        if (event.getSubcommandName() == null) {
            log.error("Received RSS command without subcommand somehow. Discord shouldn't be allowing this.");
            event.replyEmbeds(buildErrorResponse("An error occurred while processing your request."))
                    .setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.replyEmbeds(buildErrorResponse("You do not have permission to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        switch(event.getSubcommandName()) {
            case RSSConstants.ADD_SUBCOMMAND_NAME:
                String feedUrl = event.getOption(RSSConstants.FEED_URL_OPTION_NAME).getAsString();

                Optional<SyndFeed> feed = validate(feedUrl);

                if (feed.isEmpty()) {
                    event.replyEmbeds(buildErrorResponse("Invalid RSS feed. Either the URL is incorrect, or it didn't return a compatible feed.")).setEphemeral(true).queue();
                    return;
                }

                try {
                    feedService.add(event.getChannel().getIdLong(), event.getUser().getIdLong(), feed.get());
                    event.replyEmbeds(buildSuccessResponse("RSS feed added successfully.")).setEphemeral(true).queue();
                } catch (DuplicateResourceException e) {
                    event.replyEmbeds(buildErrorResponse(e.getMessage())).setEphemeral(true).queue();
                }
                break;
            case RSSConstants.DELETE_SUBCOMMAND_NAME:
                feedSelect(event);
                break;
            default:
                event.replyEmbeds(buildErrorResponse("An error occurred while processing your request."))
                        .setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals(stringSelectId)) return;

        event.deferReply().setEphemeral(true).queue();

        try {
            feedService.delete(event.getChannel().getIdLong(), event.getSelectedOptions().get(0).getValue());
        } catch (ResourceNotFoundException e) {
            event.getHook().sendMessage(MessageCreateData.fromEmbeds(buildErrorResponse(e.getMessage()))).queue();
        }

        event.getHook().sendMessage(MessageCreateData.fromEmbeds(buildSuccessResponse("RSS feed deleted successfully."))).queue();
    }

    private void feedSelect(SlashCommandInteractionEvent event) {
        List<Feed> feeds = feedService.get(event.getChannel().getIdLong());
        if (feeds.isEmpty()) {
            event.replyEmbeds(buildWarningResponse("There are no RSS feeds for this channel."))
                    .setEphemeral(true).queue();
            return;
        }

        event.reply("⚠️ Please select the RSS feed you wish to delete.")
                .addActionRow(
                        StringSelectMenu.create(stringSelectId)
                                .addOptions(feeds.stream()
                                        .map(feed -> SelectOption.of(feed.getTitle(), feed.getUrl()))
                                        .toList())
                                .build()
                ).setEphemeral(true).queue();
    }

    private Optional<SyndFeed> validate(String url) {
        if (!urlValidator.isValid(url)) {
            return Optional.empty();
        }

        try {
            return Optional.of(feedService.get(url));
        } catch (Exception e) {
            log.error("An error occurred while fetching the RSS feed from {}", url, e);
            return Optional.empty();
        }
    }
}
