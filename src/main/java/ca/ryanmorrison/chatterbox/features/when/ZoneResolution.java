package ca.ryanmorrison.chatterbox.features.when;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Resolves {@code /when}'s two distinct uses of a timezone:
 * <ol>
 *   <li><b>Interpretation zone</b> — the zone in which to read the
 *       {@code at:} string. "tomorrow" / "12pm" / "friday" attach to
 *       <em>this</em> zone's calendar and wall clock.</li>
 *   <li><b>Display zone</b> — the zone the bot's wall-clock literal is
 *       rendered in, alongside the Discord {@code <t:UNIX:F>} markdown.
 *       Empty means "don't render a wall-clock literal at all" — Discord
 *       will localize the timestamp for each viewer regardless, so the
 *       extra line would just be redundant for the caller.</li>
 * </ol>
 *
 * <h2>Resolution rules</h2>
 *
 * The caller's stored timezone (from {@code /timezone set}) always wins
 * for <b>interpretation</b> when present. {@code in:} only changes the
 * <b>display</b>. So a Toronto user typing
 * {@code /when at:tomorrow 12pm in:Asia/Kolkata} means
 * <em>"tomorrow at noon in <b>my</b> calendar — show me what time that is
 * in Kolkata"</em>, not <em>"tomorrow noon Kolkata wall clock"</em>.
 *
 * <p>If the caller hasn't set a personal timezone, {@code in:} falls back
 * to driving interpretation too. If neither is supplied, interpretation
 * is empty — the parser then accepts only zone-independent forms (now,
 * relative offsets) and rejects anything else with
 * {@link TimeParser.Result.RequiresZone}.
 *
 * <p>Display is purely opt-in: the wall-clock literal renders only when
 * the caller explicitly passes {@code in:}. With no {@code in:}, the
 * reply is just the Discord markdown, which auto-localizes per viewer.
 */
final class ZoneResolution {

    private ZoneResolution() {}

    record Resolved(Optional<ZoneId> interpretZone, Optional<ZoneId> displayZone) {}

    /**
     * @param storedZone caller's preference (from {@code /timezone set}),
     *                   or empty if they haven't set one
     * @param inOption   the {@code in:} option from the slash command,
     *                   or empty if not provided
     */
    static Resolved resolve(Optional<ZoneId> storedZone, Optional<ZoneId> inOption) {
        Optional<ZoneId> interpret = storedZone.or(() -> inOption);
        return new Resolved(interpret, inOption);
    }
}
