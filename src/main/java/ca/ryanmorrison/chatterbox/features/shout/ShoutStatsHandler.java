package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ReplayedShout;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShoutSummary;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShouterCount;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Slash handler for {@code /shout-stats}. Loads the channel's stat snapshot,
 * resolves all referenced user IDs to Discord mentions in parallel, and
 * renders a single embed via {@link ShoutStatsView}.
 *
 * <p>All counts and lookups exclude soft-deleted shouts so the figures
 * always match what's browseable via {@code /shout-history}.
 */
final class ShoutStatsHandler extends ListenerAdapter {

    static final int TOP_SHOUTER_LIMIT = 3;
    private static final String UNKNOWN_MEMBER = "Former member";

    private static final Logger log = LoggerFactory.getLogger(ShoutStatsHandler.class);

    private final ShoutStatsRepository stats;

    ShoutStatsHandler(ShoutStatsRepository stats) {
        this.stats = stats;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!ShoutStatsView.CMD_NAME.equals(event.getName())) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            event.reply("This command is only available in servers.").setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ShoutStats snapshot = stats.loadAll(channelId, now, TOP_SHOUTER_LIMIT);

        if (snapshot.isEmpty()) {
            event.replyEmbeds(ShoutStatsView.embedEmpty()).setEphemeral(true).queue();
            return;
        }

        // Defer because resolving user references makes REST calls.
        event.deferReply(true).queue();
        buildEmbed(guild, channelId, snapshot)
                .thenAccept(embed -> event.getHook().editOriginalEmbeds(embed).queue());
    }

    private CompletableFuture<MessageEmbed> buildEmbed(Guild guild, long channelId, ShoutStats snapshot) {
        List<CompletableFuture<String>> topRefs = new ArrayList<>(snapshot.topShouters().size());
        for (ShouterCount sc : snapshot.topShouters()) {
            topRefs.add(resolveMember(guild, sc.userId()));
        }
        CompletableFuture<String> oldest = optionalRef(guild, snapshot.oldest().map(ShoutSummary::authorId));
        CompletableFuture<String> newest = optionalRef(guild, snapshot.newest().map(ShoutSummary::authorId));
        CompletableFuture<String> longest = optionalRef(guild, snapshot.longest().map(ShoutSummary::authorId));
        CompletableFuture<String> hallOfFame = optionalRef(guild,
                snapshot.mostReplayed().map(ReplayedShout::shout).map(ShoutSummary::authorId));

        CompletableFuture<List<String>> resolvedTop = CompletableFuture.allOf(topRefs.toArray(CompletableFuture[]::new))
                .thenApply(v -> topRefs.stream().map(CompletableFuture::join).toList());

        return CompletableFuture.allOf(resolvedTop, oldest, newest, longest, hallOfFame)
                .thenApply(v -> ShoutStatsView.embed(
                        snapshot,
                        guild.getIdLong(),
                        channelId,
                        resolvedTop.join(),
                        oldest.join(),
                        newest.join(),
                        longest.join(),
                        hallOfFame.join()));
    }

    private static CompletableFuture<String> optionalRef(Guild guild, Optional<Long> userId) {
        return userId.map(id -> resolveMember(guild, id))
                .orElse(CompletableFuture.completedFuture(null));
    }

    /**
     * Returns a Discord user mention ({@code <@id>}) when the member still
     * exists in the guild, or the {@link #UNKNOWN_MEMBER} fallback otherwise.
     * Mentions in embed fields render as the user's name without producing a
     * notification, which is the desired UX here.
     */
    private static CompletableFuture<String> resolveMember(Guild guild, long userId) {
        return guild.retrieveMemberById(userId).submit()
                .thenApply(Member::getAsMention)
                .exceptionally(throwable -> {
                    Throwable cause = (throwable instanceof CompletionException ce) ? ce.getCause() : throwable;
                    if (cause instanceof ErrorResponseException ere
                            && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                        return UNKNOWN_MEMBER;
                    }
                    log.warn("Failed to resolve member {} in guild {}: {}",
                            userId, guild.getId(), cause.toString());
                    return UNKNOWN_MEMBER;
                });
    }
}
