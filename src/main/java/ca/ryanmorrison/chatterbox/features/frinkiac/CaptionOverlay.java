package ca.ryanmorrison.chatterbox.features.frinkiac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Builds the JSON payload that {@code /comic/img?b64=...} expects.
 *
 * <p>Frinkiac's redesign retired the old single-frame {@code /meme/...?b64lines=...}
 * route in favour of a multi-panel comic renderer. A "comic" with one panel and
 * one overlay is functionally equivalent to the old captioned-frame image, which
 * is all this feature needs.
 *
 * <p>The SPA transforms each verbose overlay into a compact, single-letter-keyed
 * shape before serialising; sending the verbose form to the renderer produces
 * an uncaptioned image (the server silently ignores unknown fields). The
 * mapping below mirrors that transformation: hex-stringified RGBA, 1-decimal
 * coordinates, and the {@code start}/{@code end}/{@code all_caps} fields only
 * emitted when set.
 */
final class CaptionOverlay {

    static final String DEFAULT_FONT = "akbar";

    private CaptionOverlay() {}

    /** A single comic panel: one episode/timestamp plus its overlays. */
    record Panel(
            @JsonProperty("e") String episode,
            @JsonProperty("ts") long timestamp,
            @JsonProperty("o") List<Overlay> overlays) {}

    /**
     * Compact overlay shape the renderer actually consumes. {@code s = 0}
     * means auto-size; {@code a = "c"} centres horizontally;
     * {@code (x, y) = (50, 97)} pins text near the bottom of the frame
     * (percent coordinates); {@code c} is an 8-char {@code rrggbbaa} hex string.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Overlay(
            @JsonProperty("t") String t,
            @JsonProperty("f") String f,
            @JsonProperty("s") double s,
            @JsonProperty("c") String c,
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("a") String a,
            @JsonProperty("b") Integer b,
            @JsonProperty("d") Integer d,
            @JsonProperty("u") Integer u) {

        /** Default-positioned, default-styled overlay carrying just the caption text. */
        static Overlay forText(String text) {
            return new Overlay(
                    text,
                    DEFAULT_FONT,
                    0,
                    "ffffffff",
                    50, 97,
                    "c",
                    null, null, null);
        }
    }

    /** Single-panel payload with one overlay carrying {@code text}. */
    static List<Panel> singlePanel(String episode, long timestamp, String text) {
        return List.of(new Panel(episode, timestamp, List.of(Overlay.forText(text))));
    }
}
