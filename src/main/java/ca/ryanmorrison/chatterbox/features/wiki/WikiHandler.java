package ca.ryanmorrison.chatterbox.features.wiki;

import ca.ryanmorrison.chatterbox.features.wiki.dto.PageSummary;
import ca.ryanmorrison.chatterbox.features.wiki.dto.SearchHit;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Handles {@code /wiki query:<text> [private:<bool>]}.
 *
 * <p>Resolution: tries a direct {@code page/summary/{title}} lookup first.
 * On 404, falls back to {@code search/page?q=...} and re-fetches the
 * summary for the top hit. Any successful path renders the result as an
 * embed via {@link WikiEmbedBuilder}; failures map to ephemeral text
 * replies via {@link WikiClient.WikiException}.
 *
 * <p>Mentions in the reply are suppressed: a Wikipedia extract that
 * happens to contain {@code @everyone} or a real user-id-shaped string
 * shouldn't ping the channel.
 */
final class WikiHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(WikiHandler.class);

    private final WikiClient client;

    WikiHandler(WikiClient client) {
        this.client = client;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!WikiModule.COMMAND.equals(event.getName())) return;

        String query = stringOption(event, WikiModule.OPT_QUERY);
        if (query == null || query.isBlank()) {
            event.reply("Tell me what to look up.").setEphemeral(true).queue();
            return;
        }
        boolean ephemeral = booleanOption(event, WikiModule.OPT_PRIVATE);

        event.deferReply(ephemeral).queue();

        Optional<PageSummary> summary;
        try {
            summary = client.summary(query);
            if (summary.isEmpty()) {
                List<SearchHit> hits = client.search(query, 1);
                if (hits.isEmpty()) {
                    event.getHook().sendMessage("Couldn't find anything for `" + query.trim() + "`. "
                                    + "Try different wording or a more specific name.")
                            .queue();
                    return;
                }
                summary = client.summary(hits.get(0).title());
            }
        } catch (WikiClient.WikiException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error for /wiki query={}", query, e);
            event.getHook().sendMessage("Something went wrong looking that up.").queue();
            return;
        }

        if (summary.isEmpty()) {
            // Search returned a hit but the follow-up summary 404'd — rare race.
            event.getHook().sendMessage("Couldn't find anything for `" + query.trim() + "`.").queue();
            return;
        }

        MessageEmbed embed = WikiEmbedBuilder.build(summary.get());
        event.getHook().sendMessageEmbeds(embed)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static boolean booleanOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null && opt.getAsBoolean();
    }
}
