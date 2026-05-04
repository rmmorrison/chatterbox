package ca.ryanmorrison.chatterbox.features.shout;

import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ReplayedShout;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShouterCount;
import ca.ryanmorrison.chatterbox.features.shout.ShoutStats.ShoutSummary;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

/**
 * Pure rendering for the {@code /shout stats} response. The handler resolves
 * raw shouts to their public-facing references (Discord mentions, jump-to-
 * message links, truncated content previews) and passes everything in via
 * value records, so this class makes no DB or REST calls and is unit-testable.
 */
final class ShoutStatsView {

    static final String SUBCOMMAND = "stats";

    /** Cap on content shown inline so a 2000-char shout doesn't blow the embed field. */
    static final int CONTENT_PREVIEW_LIMIT = 200;

    private ShoutStatsView() {}

    static MessageEmbed embedEmpty() {
        return new EmbedBuilder()
                .setTitle("Shout stats")
                .setDescription("No shouts have been recorded in this channel yet.")
                .build();
    }

    /**
     * @param stats              the loaded snapshot
     * @param guildId            for jump-to-message URLs
     * @param channelId          for jump-to-message URLs
     * @param topShouterRefs     pre-resolved mentions / fallbacks for each entry in
     *                           {@code stats.topShouters()}, in the same order
     * @param oldestRef          mention/fallback for the oldest shout's author
     * @param newestRef          mention/fallback for the newest shout's author
     * @param longestRef         mention/fallback for the longest shout's author
     * @param mostReplayedRef    mention/fallback for the hall-of-fame shout's author
     */
    static MessageEmbed embed(ShoutStats stats,
                              long guildId,
                              long channelId,
                              List<String> topShouterRefs,
                              String oldestRef,
                              String newestRef,
                              String longestRef,
                              String mostReplayedRef) {
        var b = new EmbedBuilder().setTitle("Shout stats");

        b.addField("Total shouts", Integer.toString(stats.totalShouts()), true);
        b.addField("Distinct shouters", Integer.toString(stats.distinctShouters()), true);
        b.addField("Last 7 days", Integer.toString(stats.shoutsLast7Days()), true);

        if (!stats.topShouters().isEmpty()) {
            b.addField("Top shouters", renderLeaderboard(stats.topShouters(), topShouterRefs), false);
        }

        stats.oldest().ifPresent(o ->
                b.addField("First shout",
                        renderShoutLine(o, guildId, channelId, oldestRef), false));
        stats.newest().ifPresent(n ->
                b.addField("Most recent",
                        renderShoutLine(n, guildId, channelId, newestRef), false));

        stats.longest().ifPresent(l ->
                b.addField("Longest (" + l.content().length() + " chars)",
                        renderShoutPreview(l, guildId, channelId, longestRef), false));

        stats.mostReplayed().ifPresent(r ->
                b.addField("Hall of fame (replayed " + r.replayCount() + " "
                                + (r.replayCount() == 1 ? "time" : "times") + ")",
                        renderShoutPreview(r.shout(), guildId, channelId, mostReplayedRef), false));

        return b.build();
    }

    static String renderLeaderboard(List<ShouterCount> rows, List<String> refs) {
        if (rows.size() != refs.size()) {
            throw new IllegalArgumentException("topShouterRefs size must match topShouters size");
        }
        var sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            int rank = i + 1;
            sb.append(medal(rank)).append(' ')
              .append(refs.get(i))
              .append(" — ").append(rows.get(i).count())
              .append(rows.get(i).count() == 1 ? " shout" : " shouts");
            if (i < rows.size() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private static String medal(int rank) {
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> rank + ".";
        };
    }

    static String renderShoutLine(ShoutSummary s, long guildId, long channelId, String authorRef) {
        long ts = s.authoredAt().toEpochSecond();
        return authorRef + " on <t:" + ts + ":F> · ["
                + "jump](" + jumpUrl(guildId, channelId, s.messageId()) + ")";
    }

    static String renderShoutPreview(ShoutSummary s, long guildId, long channelId, String authorRef) {
        return renderShoutLine(s, guildId, channelId, authorRef)
                + "\n> " + truncate(s.content(), CONTENT_PREVIEW_LIMIT).replace("\n", " ");
    }

    static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String jumpUrl(long guildId, long channelId, long messageId) {
        return "https://discord.com/channels/" + guildId + "/" + channelId + "/" + messageId;
    }
}
