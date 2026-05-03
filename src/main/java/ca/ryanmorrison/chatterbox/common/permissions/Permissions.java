package ca.ryanmorrison.chatterbox.common.permissions;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/**
 * Shared permission checks. Centralises the {@link Permission#MESSAGE_MANAGE}
 * "channel moderator" gate that several modules used to roll inline; routes
 * a single user-facing rejection message so feedback is consistent.
 */
public final class Permissions {

    private static final String NOT_A_GUILD_MESSAGE =
            "This command is only available in servers.";
    private static final String MISSING_MANAGE_MESSAGES_MESSAGE =
            "You need the **Manage Messages** permission in this channel to do that.";

    private Permissions() {}

    /**
     * Pure check: does {@code member} have {@link Permission#MESSAGE_MANAGE}
     * in {@code channel}? Tolerant of {@code null} inputs (returns false) so
     * callers can pass through DM/system contexts without pre-validating.
     */
    public static boolean canManageMessages(Member member, GuildChannel channel) {
        return member != null
                && channel != null
                && member.hasPermission(channel, Permission.MESSAGE_MANAGE);
    }

    /**
     * Same as {@link #canManageMessages(Member, GuildChannel)} but pulls the
     * member and channel off the interaction event for convenience at slash /
     * button handler entry points.
     */
    public static boolean canManageMessages(IReplyCallback event) {
        return canManageMessages(event.getMember(), event.getGuildChannel());
    }

    /**
     * Gate for the start of an interaction handler: returns true if the caller
     * is a channel moderator, otherwise replies with an ephemeral rejection
     * message and returns false. Caller should early-return on false.
     *
     * <p>Distinguishes the "not in a guild" case (DMs, system messages) from
     * the "in a guild but lacks the permission" case so the error message
     * actually tells the user why.
     */
    public static boolean requireManageMessages(IReplyCallback event) {
        Member member = event.getMember();
        GuildChannel channel = event.getGuildChannel();
        if (member == null || channel == null) {
            event.reply(NOT_A_GUILD_MESSAGE).setEphemeral(true).queue();
            return false;
        }
        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            event.reply(MISSING_MANAGE_MESSAGES_MESSAGE).setEphemeral(true).queue();
            return false;
        }
        return true;
    }
}
