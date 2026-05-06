package ca.ryanmorrison.chatterbox.features.frinkiac;

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
 * <p>Defaults mirror the values the SPA initialises new overlays with
 * ({@code window.defaultFont == "akbar"}, white text centred near the bottom
 * of the frame, auto-sized).
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
     * Overlay shape lifted from the SPA bundle. {@code size = 0} means
     * auto-size, {@code text_align = "c"} centres horizontally,
     * {@code (x, y) = (50, 97)} pins the text near the bottom of the frame
     * (percent coordinates).
     */
    record Overlay(
            @JsonProperty("text") String text,
            @JsonProperty("font") String font,
            @JsonProperty("size") int size,
            @JsonProperty("color") int[] color,
            @JsonProperty("x") int x,
            @JsonProperty("y") int y,
            @JsonProperty("text_align") String textAlign,
            @JsonProperty("all_caps") boolean allCaps,
            @JsonProperty("start") int start,
            @JsonProperty("end") int end) {

        /** Default-positioned, default-styled overlay carrying just the caption text. */
        static Overlay forText(String text) {
            return new Overlay(
                    text,
                    DEFAULT_FONT,
                    0,
                    new int[]{255, 255, 255, 255},
                    50, 97,
                    "c",
                    false,
                    0, 0);
        }
    }

    /** Single-panel payload with one overlay carrying {@code text}. */
    static List<Panel> singlePanel(String episode, long timestamp, String text) {
        return List.of(new Panel(episode, timestamp, List.of(Overlay.forText(text))));
    }
}
