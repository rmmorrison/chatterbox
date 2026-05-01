package ca.ryanmorrison.chatterbox.features.autoreply;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Slash, modal, select, and button glue for {@code /autoreply}. All replies
 * are ephemeral. Permissions ({@link Permission#MESSAGE_MANAGE} in the
 * channel) are re-checked on every interaction so a user who loses the
 * role mid-flow is denied as soon as they click again.
 */
final class AutoReplyHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyHandler.class);

    static final String CMD_NAME = "autoreply";

    // Component / modal id namespace.
    private static final String PREFIX             = CMD_NAME + ":";
    private static final String MODAL_ADD          = PREFIX + "add";
    private static final String MODAL_EDIT_PREFIX  = PREFIX + "edit:";
    private static final String SELECT_EDIT        = PREFIX + "edit:select";
    private static final String SELECT_DELETE      = PREFIX + "delete:select";
    private static final String OVERRIDE_CONFIRM   = PREFIX + "override:confirm:";
    private static final String OVERRIDE_CANCEL    = PREFIX + "override:cancel:";
    private static final String DELETE_CONFIRM     = PREFIX + "delete:confirm:";
    private static final String DELETE_CANCEL      = PREFIX + "delete:cancel:";

    // Modal text input ids.
    private static final String INPUT_PATTERN     = "pattern";
    private static final String INPUT_DESCRIPTION = "description";
    private static final String INPUT_RESPONSE    = "response";

    // Discord limits / our caps.
    static final int MAX_PATTERN_LENGTH     = 200;
    static final int MAX_DESCRIPTION_LENGTH = 100;
    static final int MAX_RESPONSE_LENGTH    = 2000;
    static final int SELECT_OPTION_LIMIT    = 25;

    private final AutoReplyRepository repo;
    private final AutoReplyMatcher matcher;
    private final PendingOverrideStore pending;

    AutoReplyHandler(AutoReplyRepository repo, AutoReplyMatcher matcher, PendingOverrideStore pending) {
        this.repo = repo;
        this.matcher = matcher;
        this.pending = pending;
    }

    // ---- slash entry ----

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!CMD_NAME.equals(event.getName())) return;
        if (!requireModerator(event)) return;
        String sub = event.getSubcommandName();
        if (sub == null) return;
        switch (sub) {
            case "add"    -> openAddModal(event);
            case "edit"   -> openEditPicker(event);
            case "delete" -> openDeletePicker(event);
            default       -> {}
        }
    }

    private void openAddModal(SlashCommandInteractionEvent event) {
        event.replyModal(buildAddModal()).queue();
    }

    private void openEditPicker(SlashCommandInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        List<AutoReplyRule> rules = repo.listRecentByChannel(channelId, SELECT_OPTION_LIMIT);
        if (rules.isEmpty()) {
            event.reply("No automated replies configured for this channel yet. Use `/autoreply add` first.")
                    .setEphemeral(true).queue();
            return;
        }
        StringSelectMenu select = buildPicker(SELECT_EDIT, rules, "Pick a rule to edit");
        event.reply(headerForPicker(channelId, "edit"))
                .setComponents(ActionRow.of(select))
                .setEphemeral(true)
                .queue();
    }

    private void openDeletePicker(SlashCommandInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        List<AutoReplyRule> rules = repo.listRecentByChannel(channelId, SELECT_OPTION_LIMIT);
        if (rules.isEmpty()) {
            event.reply("No automated replies configured for this channel yet.")
                    .setEphemeral(true).queue();
            return;
        }
        StringSelectMenu select = buildPicker(SELECT_DELETE, rules, "Pick a rule to delete");
        event.reply(headerForPicker(channelId, "delete"))
                .setComponents(ActionRow.of(select))
                .setEphemeral(true)
                .queue();
    }

    private String headerForPicker(long channelId, String verb) {
        int total = repo.countByChannel(channelId);
        if (total > SELECT_OPTION_LIMIT) {
            return "Pick a rule to " + verb + ". This channel has " + total + " rules; the "
                    + SELECT_OPTION_LIMIT + " most recently created or edited are listed below.";
        }
        return "Pick a rule to " + verb + ".";
    }

    // ---- modal submit ----

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith(PREFIX)) return;
        if (!requireModerator(event)) return;

        if (id.equals(MODAL_ADD)) {
            handleAddSubmit(event);
        } else if (id.startsWith(MODAL_EDIT_PREFIX)) {
            long ruleId;
            try {
                ruleId = Long.parseLong(id.substring(MODAL_EDIT_PREFIX.length()));
            } catch (NumberFormatException e) {
                log.warn("Malformed edit modal id: {}", id);
                return;
            }
            handleEditSubmit(event, ruleId);
        }
    }

    private void handleAddSubmit(ModalInteractionEvent event) {
        Submission s = readSubmission(event);
        Optional<String> validationError = validate(s);
        if (validationError.isPresent()) {
            event.reply(validationError.get()).setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        Optional<AutoReplyRule> existing = repo.findByChannelAndPattern(channelId, s.pattern());
        if (existing.isPresent()) {
            UUID token = pending.stash(existing.get().id(), channelId, s.pattern(), s.response(), s.description());
            event.reply("A rule with this exact pattern already exists in this channel:\n"
                            + "**" + truncate(existing.get().description(), 200) + "**\n"
                            + "Override it with the new response and description?")
                    .setComponents(ActionRow.of(
                            Button.danger(OVERRIDE_CONFIRM + token, "Override"),
                            Button.secondary(OVERRIDE_CANCEL + token, "Cancel")))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<Long> newId = repo.insert(channelId, s.pattern(), s.response(), s.description(),
                event.getUser().getIdLong());
        if (newId.isEmpty()) {
            // Race: another moderator inserted the same pattern between our find and insert.
            event.reply("That pattern was just added by someone else. Run `/autoreply edit` to modify it.")
                    .setEphemeral(true).queue();
            return;
        }
        matcher.invalidate(channelId);
        event.reply("Automated reply created.").setEphemeral(true).queue();
    }

    private void handleEditSubmit(ModalInteractionEvent event, long ruleId) {
        Submission s = readSubmission(event);
        Optional<String> validationError = validate(s);
        if (validationError.isPresent()) {
            event.reply(validationError.get()).setEphemeral(true).queue();
            return;
        }

        long channelId = event.getChannel().getIdLong();
        Optional<AutoReplyRule> existingPattern = repo.findByChannelAndPattern(channelId, s.pattern());
        if (existingPattern.isPresent() && existingPattern.get().id() != ruleId) {
            event.reply("Another rule in this channel already uses that exact pattern. "
                            + "Delete the existing rule first or pick a different pattern.")
                    .setEphemeral(true).queue();
            return;
        }

        int rows = repo.update(ruleId, s.pattern(), s.response(), s.description(), event.getUser().getIdLong());
        if (rows == 0) {
            event.reply("That rule no longer exists — it may have been deleted while you were editing.")
                    .setEphemeral(true).queue();
            return;
        }
        matcher.invalidate(channelId);
        event.reply("Automated reply updated.").setEphemeral(true).queue();
    }

    // ---- string-select handling ----

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) return;
        if (!requireModerator(event)) return;

        long ruleId;
        try {
            ruleId = Long.parseLong(event.getValues().get(0));
        } catch (RuntimeException e) {
            log.warn("Malformed select value: {}", event.getValues());
            return;
        }

        Optional<AutoReplyRule> rule = repo.findById(ruleId);
        if (rule.isEmpty()) {
            event.editMessage("That rule no longer exists.").setComponents(List.of()).queue();
            return;
        }

        if (id.equals(SELECT_EDIT)) {
            event.replyModal(buildEditModal(rule.get())).queue();
        } else if (id.equals(SELECT_DELETE)) {
            event.editMessage("Confirm deletion of:\n"
                            + "**" + truncate(rule.get().description(), 200) + "**\n"
                            + "Pattern: `" + truncate(rule.get().pattern(), 200) + "`")
                    .setComponents(ActionRow.of(
                            Button.danger(DELETE_CONFIRM + ruleId, "Delete"),
                            Button.secondary(DELETE_CANCEL + ruleId, "Cancel")))
                    .queue();
        }
    }

    // ---- button handling ----

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) return;
        if (!requireModerator(event)) return;

        if (id.startsWith(OVERRIDE_CONFIRM)) {
            handleOverrideConfirm(event, id.substring(OVERRIDE_CONFIRM.length()));
        } else if (id.startsWith(OVERRIDE_CANCEL)) {
            UUID token = parseUuidOrNull(id.substring(OVERRIDE_CANCEL.length()));
            if (token != null) pending.discard(token);
            event.editMessage("Cancelled.").setComponents(List.of()).queue();
        } else if (id.startsWith(DELETE_CONFIRM)) {
            handleDeleteConfirm(event, id.substring(DELETE_CONFIRM.length()));
        } else if (id.startsWith(DELETE_CANCEL)) {
            event.editMessage("Cancelled.").setComponents(List.of()).queue();
        }
    }

    private void handleOverrideConfirm(ButtonInteractionEvent event, String tokenStr) {
        UUID token = parseUuidOrNull(tokenStr);
        if (token == null) {
            event.editMessage("Cancelled.").setComponents(List.of()).queue();
            return;
        }
        Optional<PendingOverrideStore.Pending> pendingOpt = pending.consume(token);
        if (pendingOpt.isEmpty()) {
            event.editMessage("This override prompt expired. Run `/autoreply add` again.")
                    .setComponents(List.of()).queue();
            return;
        }
        var p = pendingOpt.get();
        int rows = repo.update(p.ruleId(), p.pattern(), p.response(), p.description(),
                event.getUser().getIdLong());
        if (rows == 0) {
            event.editMessage("The original rule no longer exists. Run `/autoreply add` to create a fresh rule.")
                    .setComponents(List.of()).queue();
            return;
        }
        matcher.invalidate(p.channelId());
        event.editMessage("Automated reply overridden.").setComponents(List.of()).queue();
    }

    private void handleDeleteConfirm(ButtonInteractionEvent event, String ruleIdStr) {
        long ruleId;
        try {
            ruleId = Long.parseLong(ruleIdStr);
        } catch (NumberFormatException e) {
            log.warn("Malformed delete-confirm id: {}", event.getComponentId());
            return;
        }
        long channelId = event.getChannel().getIdLong();
        int rows = repo.deleteById(ruleId);
        if (rows == 0) {
            event.editMessage("That rule no longer exists.").setComponents(List.of()).queue();
            return;
        }
        matcher.invalidate(channelId);
        event.editMessage("Automated reply deleted.").setComponents(List.of()).queue();
    }

    // ---- modal builders ----

    private static Modal buildAddModal() {
        return Modal.create(MODAL_ADD, "New automated reply")
                .addComponents(
                        Label.of("Regular expression",
                                TextInput.create(INPUT_PATTERN, TextInputStyle.SHORT)
                                        .setRequiredRange(1, MAX_PATTERN_LENGTH)
                                        .setPlaceholder("e.g. (?i)\\bhello\\b")
                                        .build()),
                        Label.of("Description",
                                TextInput.create(INPUT_DESCRIPTION, TextInputStyle.SHORT)
                                        .setRequiredRange(1, MAX_DESCRIPTION_LENGTH)
                                        .setPlaceholder("Greets users who say hello")
                                        .build()),
                        Label.of("Response",
                                TextInput.create(INPUT_RESPONSE, TextInputStyle.PARAGRAPH)
                                        .setRequiredRange(1, MAX_RESPONSE_LENGTH)
                                        .setPlaceholder("Hi there!")
                                        .build()))
                .build();
    }

    private static Modal buildEditModal(AutoReplyRule rule) {
        return Modal.create(MODAL_EDIT_PREFIX + rule.id(), "Edit automated reply")
                .addComponents(
                        Label.of("Regular expression",
                                TextInput.create(INPUT_PATTERN, TextInputStyle.SHORT)
                                        .setRequiredRange(1, MAX_PATTERN_LENGTH)
                                        .setValue(truncate(rule.pattern(), MAX_PATTERN_LENGTH))
                                        .build()),
                        Label.of("Description",
                                TextInput.create(INPUT_DESCRIPTION, TextInputStyle.SHORT)
                                        .setRequiredRange(1, MAX_DESCRIPTION_LENGTH)
                                        .setValue(truncate(rule.description(), MAX_DESCRIPTION_LENGTH))
                                        .build()),
                        Label.of("Response",
                                TextInput.create(INPUT_RESPONSE, TextInputStyle.PARAGRAPH)
                                        .setRequiredRange(1, MAX_RESPONSE_LENGTH)
                                        .setValue(truncate(rule.response(), MAX_RESPONSE_LENGTH))
                                        .build()))
                .build();
    }

    private static StringSelectMenu buildPicker(String customId, List<AutoReplyRule> rules, String placeholder) {
        var builder = StringSelectMenu.create(customId).setPlaceholder(placeholder);
        for (AutoReplyRule r : rules) {
            builder.addOptions(SelectOption.of(
                            truncate(r.description(), 100),
                            String.valueOf(r.id()))
                    .withDescription(truncate(r.pattern(), 100)));
        }
        return builder.build();
    }

    // ---- helpers ----

    private record Submission(String pattern, String description, String response) {}

    private static Submission readSubmission(ModalInteractionEvent event) {
        return new Submission(
                trim(event.getValue(INPUT_PATTERN)),
                trim(event.getValue(INPUT_DESCRIPTION)),
                trim(event.getValue(INPUT_RESPONSE)));
    }

    private static String trim(net.dv8tion.jda.api.interactions.modals.ModalMapping mapping) {
        return mapping == null ? "" : mapping.getAsString().trim();
    }

    private static Optional<String> validate(Submission s) {
        if (s.pattern().isEmpty())     return Optional.of("Pattern can't be blank.");
        if (s.description().isEmpty()) return Optional.of("Description can't be blank.");
        if (s.response().isEmpty())    return Optional.of("Response can't be blank.");
        if (s.pattern().length()     > MAX_PATTERN_LENGTH)     return Optional.of("Pattern is too long.");
        if (s.description().length() > MAX_DESCRIPTION_LENGTH) return Optional.of("Description is too long.");
        if (s.response().length()    > MAX_RESPONSE_LENGTH)    return Optional.of("Response is too long.");
        try {
            Pattern.compile(s.pattern());
        } catch (PatternSyntaxException e) {
            return Optional.of("Invalid regular expression: " + truncate(e.getDescription(), 200));
        }
        return Optional.empty();
    }

    private static UUID parseUuidOrNull(String token) {
        try { return UUID.fromString(token); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static boolean requireModerator(IReplyCallback event) {
        Member member = event.getMember();
        GuildChannel channel = event.getGuildChannel();
        if (member == null || channel == null) {
            event.reply("This command is only available in servers.").setEphemeral(true).queue();
            return false;
        }
        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            event.reply("You need the **Manage Messages** permission in this channel to use `/autoreply`.")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }
}
