package ca.ryanmorrison.chatterbox.features.emote;

/**
 * The roster of canned text emotes. Each carries:
 * <ul>
 *   <li>{@code value} — the slash-command choice value the user picks,</li>
 *   <li>{@code label} — the human-readable picker label,</li>
 *   <li>{@code text}  — the literal characters posted to the channel.</li>
 * </ul>
 *
 * <p>Adding a new emote is one enum line; {@code EmoteModule} reflects
 * the values into Discord choices automatically.
 */
enum Emote {

    FLIP("flip",       "Flip table",        "(╯°□°)╯︵ ┻━┻"),
    UNFLIP("unflip",   "Put table back",    "┬─┬ノ( º _ ºノ)"),
    RAGEFLIP("rageflip", "Rage-flip table", "(ノಠ益ಠ)ノ彡┻━┻"),
    DOUBLEFLIP("doubleflip", "Double-flip", "┻━┻ ︵ ヽ(°□°ヽ)"),
    SHRUG("shrug",     "Shrug",             "¯\\_(ツ)_/¯"),
    LENNY("lenny",     "Lenny face",        "( ͡° ͜ʖ ͡°)"),
    DISAPPROVE("disapprove", "Look of disapproval", "ಠ_ಠ");

    private final String value;
    private final String label;
    private final String text;

    Emote(String value, String label, String text) {
        this.value = value;
        this.label = label;
        this.text = text;
    }

    String value() { return value; }
    String label() { return label; }
    String text()  { return text; }

    static Emote fromValue(String value) {
        for (Emote e : values()) {
            if (e.value.equals(value)) return e;
        }
        throw new IllegalArgumentException("Unknown emote: " + value);
    }
}
