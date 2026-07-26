package ca.ryanmorrison.chatterbox.common.permissions;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization logic, so it gets direct coverage.
 *
 * <p>The DM cases matter most: JDA's {@code getGuildChannel()} throws
 * {@link IllegalStateException} outside a guild rather than returning null, so
 * reading it before checking {@code isFromGuild()} let the exception escape
 * the listener — the user saw "The application did not respond" instead of a
 * rejection. These tests stub that throwing behaviour explicitly.
 */
class PermissionsTest {

    /** An interaction that is not in a guild, mirroring JDA's throwing accessor. */
    private static IReplyCallback dmEvent() {
        IReplyCallback event = mock(IReplyCallback.class);
        when(event.isFromGuild()).thenReturn(false);
        lenient().when(event.getMember()).thenReturn(null);
        lenient().when(event.getGuildChannel())
                .thenThrow(new IllegalStateException("This interaction did not happen in a guild"));
        ReplyCallbackAction action = mock(ReplyCallbackAction.class, org.mockito.Answers.RETURNS_SELF);
        lenient().when(event.reply(anyString())).thenReturn(action);
        return event;
    }

    private static IReplyCallback guildEvent(boolean hasPermission) {
        IReplyCallback event = mock(IReplyCallback.class);
        Member member = mock(Member.class);
        GuildChannel channel = mock(GuildChannel.class);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getMember()).thenReturn(member);
        when(event.getGuildChannel()).thenReturn(channel);
        lenient().when(member.hasPermission(channel, Permission.MESSAGE_MANAGE))
                .thenReturn(hasPermission);
        ReplyCallbackAction action = mock(ReplyCallbackAction.class, org.mockito.Answers.RETURNS_SELF);
        lenient().when(event.reply(anyString())).thenReturn(action);
        return event;
    }

    // ---- canManageMessages ----

    @Test
    void canManageMessagesIsFalseInDmRatherThanThrowing() {
        assertFalse(Permissions.canManageMessages(dmEvent()));
    }

    @Test
    void canManageMessagesReflectsThePermissionInAGuild() {
        assertTrue(Permissions.canManageMessages(guildEvent(true)));
        assertFalse(Permissions.canManageMessages(guildEvent(false)));
    }

    @Test
    void canManageMessagesToleratesNullInputs() {
        assertFalse(Permissions.canManageMessages(null, mock(GuildChannel.class)));
        assertFalse(Permissions.canManageMessages(mock(Member.class), null));
    }

    // ---- requireManageMessages ----

    @Test
    void requireManageMessagesRejectsInDmWithAMessage() {
        IReplyCallback event = dmEvent();

        assertFalse(Permissions.requireManageMessages(event));

        // The point of the fix: the user gets told why, instead of the
        // interaction dying with an unhandled exception.
        verify(event).reply("This command is only available in servers.");
    }

    @Test
    void requireManageMessagesRejectsWhenPermissionIsMissing() {
        IReplyCallback event = guildEvent(false);

        assertFalse(Permissions.requireManageMessages(event));

        verify(event).reply(
                "You need the **Manage Messages** permission in this channel to do that.");
    }

    @Test
    void requireManageMessagesPassesForAModerator() {
        IReplyCallback event = guildEvent(true);

        assertTrue(Permissions.requireManageMessages(event));

        verify(event, never()).reply(anyString());
    }

    // ---- requireAdministrator ----

    @Test
    void requireAdministratorRejectsInDm() {
        IReplyCallback event = dmEvent();

        assertFalse(Permissions.requireAdministrator(event));

        verify(event).reply("This command is only available in servers.");
    }

    @Test
    void requireAdministratorPassesForAnAdmin() {
        IReplyCallback event = mock(IReplyCallback.class);
        Member member = mock(Member.class);
        when(event.getMember()).thenReturn(member);
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

        assertTrue(Permissions.requireAdministrator(event));
    }
}
