package ca.ryanmorrison.chatterbox.features.frinkiac;

import ca.ryanmorrison.chatterbox.features.frinkiac.dto.CaptionResponse;
import ca.ryanmorrison.chatterbox.features.frinkiac.dto.SearchResult;
import ca.ryanmorrison.chatterbox.features.frinkiac.dto.Subtitle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Glue between the {@code /frinkiac} slash command, its prev/next/edit/post
 * buttons, and the caption-edit modal.
 *
 * <p>Flow:
 * <ol>
 *   <li>Slash command runs a search, stashes results in {@link FrinkiacSessions},
 *       replies <em>ephemerally</em> with a preview of the first hit.</li>
 *   <li>Prev/Next re-render the same ephemeral message at a different index.
 *       The on-screen subtitle and an uncaptioned thumbnail are loaded from
 *       Frinkiac's caption + image endpoints.</li>
 *   <li>Edit Caption opens a modal pre-filled with the joined subtitle text
 *       (or the user's prior edit, if any). Submitting it saves the override
 *       and re-renders the preview using Frinkiac's {@code /comic/img}
 *       endpoint so the user sees the captioned frame they're about to post.</li>
 *   <li>Post to Channel uploads the (captioned-or-plain) image as a file in
 *       the originating channel and dismisses the ephemeral preview.</li>
 * </ol>
 *
 * <p>All button/modal IDs encode a {@link UUID} that points at the session.
 * The session is checked on every interaction; if it has expired the user
 * gets a clear "this prompt expired" message rather than a stack trace.
 */
final class FrinkiacHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(FrinkiacHandler.class);

    private static final String PREFIX        = FrinkiacModule.COMMAND + ":";
    private static final String BTN_PREV      = PREFIX + "prev:";
    private static final String BTN_NEXT      = PREFIX + "next:";
    private static final String BTN_EDIT      = PREFIX + "edit:";
    private static final String BTN_POST      = PREFIX + "post:";
    private static final String BTN_CANCEL    = PREFIX + "cancel:";
    private static final String MODAL_EDIT    = PREFIX + "editmodal:";
    private static final String INPUT_CAPTION = "caption";

    /** Frinkiac caps free-form caption length here in its own UI. */
    private static final int MAX_CAPTION_LENGTH = 500;
    /** Hard cap on how many search hits we paginate through. */
    private static final int MAX_RESULTS = 25;
    /** Filename used for both preview and posted image attachments. */
    private static final String IMAGE_FILENAME = "frinkiac.jpg";
    /** Frinkiac brand-ish yellow for embeds. */
    private static final Color EMBED_COLOR = new Color(0xFED41B);

    private final FrinkiacClient client;
    private final FrinkiacSessions sessions;

    FrinkiacHandler(FrinkiacClient client, FrinkiacSessions sessions) {
        this.client = client;
        this.sessions = sessions;
    }

    // ---- slash entry ----

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!FrinkiacModule.COMMAND.equals(event.getName())) return;
        var queryOpt = event.getOption(FrinkiacModule.OPTION_QUERY);
        if (queryOpt == null) return;
        String query = queryOpt.getAsString().trim();
        if (query.isEmpty()) {
            event.reply("Please provide a search query.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        List<SearchResult> hits;
        try {
            hits = client.search(query);
        } catch (FrinkiacClient.FrinkiacException e) {
            event.getHook().sendMessage("Frinkiac search failed: " + e.getMessage()).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error searching Frinkiac for {}", query, e);
            event.getHook().sendMessage("Something went wrong searching Frinkiac.").queue();
            return;
        }

        if (hits.isEmpty()) {
            event.getHook().sendMessage("No Frinkiac results for `" + truncate(query, 100) + "`.").queue();
            return;
        }
        if (hits.size() > MAX_RESULTS) {
            hits = hits.subList(0, MAX_RESULTS);
        }

        UUID token = sessions.create(event.getUser().getIdLong(),
                event.getChannel().getIdLong(), query, hits);
        renderPreview(event.getHook(), token);
    }

    // ---- button handling ----

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(PREFIX)) return;

        if (id.startsWith(BTN_PREV)) {
            handleNav(event, id.substring(BTN_PREV.length()), -1);
        } else if (id.startsWith(BTN_NEXT)) {
            handleNav(event, id.substring(BTN_NEXT.length()), +1);
        } else if (id.startsWith(BTN_EDIT)) {
            handleEditOpen(event, id.substring(BTN_EDIT.length()));
        } else if (id.startsWith(BTN_POST)) {
            handlePost(event, id.substring(BTN_POST.length()));
        } else if (id.startsWith(BTN_CANCEL)) {
            handleCancel(event, id.substring(BTN_CANCEL.length()));
        }
    }

    private void handleNav(ButtonInteractionEvent event, String tokenStr, int delta) {
        UUID token = parseUuid(tokenStr);
        var sessionOpt = token == null ? Optional.<FrinkiacSessions.Session>empty() : sessions.get(token);
        if (sessionOpt.isEmpty()) {
            expired(event);
            return;
        }
        if (!sessionOwned(event, sessionOpt.get())) return;

        sessions.setIndex(token, sessionOpt.get().index() + delta);
        event.deferEdit().queue();
        renderPreview(event.getHook(), token);
    }

    private void handleEditOpen(ButtonInteractionEvent event, String tokenStr) {
        UUID token = parseUuid(tokenStr);
        var sessionOpt = token == null ? Optional.<FrinkiacSessions.Session>empty() : sessions.get(token);
        if (sessionOpt.isEmpty()) {
            expired(event);
            return;
        }
        if (!sessionOwned(event, sessionOpt.get())) return;

        FrinkiacSessions.Session s = sessionOpt.get();
        SearchResult current = s.current();
        // The first render of this frame cached its on-screen subtitle here, so the
        // modal can prefill without a network call. Fall back to the search hit's
        // content if the user somehow opens this before the preview has rendered.
        String prefill = s.captionFor(current.id())
                .orElseGet(() -> current.content() == null ? "" : current.content());

        event.replyModal(buildEditModal(token, prefill)).queue();
    }

    private void handlePost(ButtonInteractionEvent event, String tokenStr) {
        UUID token = parseUuid(tokenStr);
        var sessionOpt = token == null ? Optional.<FrinkiacSessions.Session>empty() : sessions.get(token);
        if (sessionOpt.isEmpty()) {
            expired(event);
            return;
        }
        FrinkiacSessions.Session s = sessionOpt.get();
        if (!sessionOwned(event, s)) return;

        event.deferEdit().queue();
        SearchResult current = s.current();
        String caption = s.captionFor(current.id())
                .orElseGet(() -> current.content() == null ? "" : current.content());

        byte[] image;
        try {
            image = client.fetchCaptionedFrame(current.episode(), current.timestamp(), caption);
        } catch (FrinkiacClient.FrinkiacException e) {
            event.getHook().editOriginal("Couldn't fetch the image to post: " + e.getMessage())
                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching image for post", e);
            event.getHook().editOriginal("Something went wrong fetching the image.")
                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
            return;
        }

        MessageEmbed publicEmbed = buildPublicEmbed(current);
        FileUpload upload = FileUpload.fromData(image, IMAGE_FILENAME);
        event.getMessageChannel().sendMessageEmbeds(publicEmbed)
                .addFiles(upload)
                .queue(
                        m -> event.getHook().editOriginal("Posted to channel.")
                                .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue(),
                        err -> {
                            log.warn("Failed to post Frinkiac frame to channel", err);
                            event.getHook().editOriginal("Couldn't post to the channel.")
                                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
                        });
        sessions.discard(token);
    }

    private void handleCancel(ButtonInteractionEvent event, String tokenStr) {
        UUID token = parseUuid(tokenStr);
        if (token != null) sessions.discard(token);
        event.editMessage("Cancelled.")
                .setEmbeds(List.of())
                .setComponents(List.of())
                .setAttachments()
                .queue();
    }

    // ---- modal handling ----

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith(MODAL_EDIT)) return;

        UUID token = parseUuid(id.substring(MODAL_EDIT.length()));
        var sessionOpt = token == null ? Optional.<FrinkiacSessions.Session>empty() : sessions.get(token);
        if (sessionOpt.isEmpty()) {
            event.reply("This caption editor expired. Run `/frinkiac` again.").setEphemeral(true).queue();
            return;
        }
        FrinkiacSessions.Session s = sessionOpt.get();
        if (s.requestedBy != event.getUser().getIdLong()) {
            event.reply("Only the user who started this search can edit it.").setEphemeral(true).queue();
            return;
        }

        var mapping = event.getValue(INPUT_CAPTION);
        String text = mapping == null ? "" : mapping.getAsString();
        if (text.length() > MAX_CAPTION_LENGTH) {
            text = text.substring(0, MAX_CAPTION_LENGTH);
        }
        sessions.putCaption(token, s.current().id(), text);

        event.deferEdit().queue();
        renderPreview(event.getHook(), token);
    }

    // ---- shared rendering ----

    /**
     * Updates the ephemeral preview message to reflect the session's current
     * frame. Always renders the caption text into the image via Frinkiac's
     * {@code /comic/img} endpoint so the user sees the same Simpsons-fonted
     * frame they'll be posting. The text comes from a per-frame cache that's
     * seeded with the on-screen subtitle on first render and replaced when
     * the user edits it.
     */
    private void renderPreview(InteractionHook hook, UUID token) {
        var sessionOpt = sessions.get(token);
        if (sessionOpt.isEmpty()) {
            hook.editOriginal("This search expired.")
                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
            return;
        }
        FrinkiacSessions.Session s = sessionOpt.get();
        SearchResult current = s.current();
        String caption = resolveAndCacheCaption(token, s, current);

        byte[] image;
        try {
            image = client.fetchCaptionedFrame(current.episode(), current.timestamp(), caption);
        } catch (FrinkiacClient.FrinkiacException e) {
            hook.editOriginal("Couldn't fetch the frame image: " + e.getMessage())
                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected image fetch error", e);
            hook.editOriginal("Something went wrong fetching the frame image.")
                    .setEmbeds(List.of()).setComponents(List.of()).setAttachments().queue();
            return;
        }

        MessageEmbed embed = buildPreviewEmbed(s);
        FileUpload upload = FileUpload.fromData(image, IMAGE_FILENAME);
        hook.editOriginal("")
                .setEmbeds(embed)
                .setComponents(buildButtonRow(token, s))
                .setAttachments(upload)
                .queue();
    }

    /**
     * Returns the caption text that should be rendered onto {@code current},
     * caching it in the session on first lookup. The cached value is whatever
     * the user has typed (if they've edited this frame) or the joined nearby
     * subtitles from {@code /api/caption} (mirroring the website's display).
     * Falls back to the search hit's own {@code Content} if the caption
     * endpoint is unreachable.
     */
    private String resolveAndCacheCaption(UUID token, FrinkiacSessions.Session s, SearchResult current) {
        var cached = s.captionFor(current.id());
        if (cached.isPresent()) return cached.get();

        String fallback = current.content() == null ? "" : current.content();
        String resolved = fallback;
        try {
            CaptionResponse cap = client.caption(current.episode(), current.timestamp());
            String joined = joinSubtitles(cap);
            if (!joined.isEmpty()) resolved = joined;
        } catch (FrinkiacClient.FrinkiacException e) {
            log.debug("Caption fetch failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected caption fetch error", e);
        }
        sessions.putCaption(token, current.id(), resolved);
        return resolved;
    }

    private MessageEmbed buildPreviewEmbed(FrinkiacSessions.Session s) {
        EmbedBuilder eb = new EmbedBuilder().setColor(EMBED_COLOR).setTitle(headerFor(s.current()));
        eb.setImage("attachment://" + IMAGE_FILENAME);
        eb.setFooter("Result " + (s.index() + 1) + " of " + s.hits.size()
                + " · query: " + truncate(s.query, 80)
                + " · via frinkiac.com");
        return eb.build();
    }

    private MessageEmbed buildPublicEmbed(SearchResult current) {
        return new EmbedBuilder()
                .setColor(EMBED_COLOR)
                .setTitle(headerFor(current))
                .setImage("attachment://" + IMAGE_FILENAME)
                .setFooter("via frinkiac.com")
                .build();
    }

    private static String headerFor(SearchResult current) {
        String title = (current.title() == null ? "" : current.title()).trim();
        return current.episode() + (title.isEmpty() ? "" : " — " + title);
    }

    private List<ActionRow> buildButtonRow(UUID token, FrinkiacSessions.Session s) {
        boolean atStart = s.index() == 0;
        boolean atEnd = s.index() >= s.hits.size() - 1;
        Button prev = Button.secondary(BTN_PREV + token, "◀ Prev").withDisabled(atStart);
        Button next = Button.secondary(BTN_NEXT + token, "Next ▶").withDisabled(atEnd);
        Button edit = Button.primary(BTN_EDIT + token, "✏ Edit caption");
        Button post = Button.success(BTN_POST + token, "📤 Post to channel");
        Button cancel = Button.danger(BTN_CANCEL + token, "Cancel");
        return List.of(ActionRow.of(prev, next, edit, post, cancel));
    }

    private static Modal buildEditModal(UUID token, String prefill) {
        TextInput caption = TextInput.create(INPUT_CAPTION, TextInputStyle.PARAGRAPH)
                .setRequiredRange(0, MAX_CAPTION_LENGTH)
                .setValue(truncate(prefill, MAX_CAPTION_LENGTH))
                .setPlaceholder("Caption text. Newlines are preserved as separate lines on the frame.")
                .build();
        return Modal.create(MODAL_EDIT + token, "Edit caption")
                .addComponents(Label.of("Caption", caption))
                .build();
    }

    /** Joins the {@code /api/caption} {@code Subtitles} list with newlines, mimicking the website. */
    private static String joinSubtitles(CaptionResponse cap) {
        if (cap == null || cap.subtitles() == null || cap.subtitles().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Subtitle sub : cap.subtitles()) {
            if (sub.content() == null || sub.content().isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(sub.content().trim());
        }
        return sb.toString();
    }

    // ---- helpers ----

    private void expired(ButtonInteractionEvent event) {
        event.editMessage("This Frinkiac prompt expired. Run `/frinkiac` again.")
                .setEmbeds(List.of())
                .setComponents(List.of())
                .setAttachments()
                .queue();
    }

    private boolean sessionOwned(ButtonInteractionEvent event, FrinkiacSessions.Session s) {
        if (s.requestedBy == event.getUser().getIdLong()) return true;
        // Ephemerals are private to the requester so we shouldn't reach this in practice;
        // belt-and-braces check in case Discord ever changes that.
        event.reply("Only the user who started this search can use these buttons.")
                .setEphemeral(true).queue();
        return false;
    }

    private static UUID parseUuid(String token) {
        try { return UUID.fromString(token); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
