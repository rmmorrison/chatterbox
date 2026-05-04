package ca.ryanmorrison.chatterbox.features.eightball;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The 20 canonical Magic 8 Ball answers, in the same 10 / 5 / 5 split as
 * the physical toy (affirmative / non-committal / negative). Picks are
 * uniform — every answer has equal probability — which (slightly) skews
 * outcomes toward "yes" because the bowl itself does.
 */
final class EightBallAnswers {

    static final List<String> AFFIRMATIVE = List.of(
            "It is certain.",
            "It is decidedly so.",
            "Without a doubt.",
            "Yes definitely.",
            "You may rely on it.",
            "As I see it, yes.",
            "Most likely.",
            "Outlook good.",
            "Yes.",
            "Signs point to yes.");

    static final List<String> NON_COMMITTAL = List.of(
            "Reply hazy, try again.",
            "Ask again later.",
            "Better not tell you now.",
            "Cannot predict now.",
            "Concentrate and ask again.");

    static final List<String> NEGATIVE = List.of(
            "Don't count on it.",
            "My reply is no.",
            "My sources say no.",
            "Outlook not so good.",
            "Very doubtful.");

    static final List<String> ALL;
    static {
        var combined = new java.util.ArrayList<String>(20);
        combined.addAll(AFFIRMATIVE);
        combined.addAll(NON_COMMITTAL);
        combined.addAll(NEGATIVE);
        ALL = List.copyOf(combined);
    }

    private EightBallAnswers() {}

    static String pick() {
        return ALL.get(ThreadLocalRandom.current().nextInt(ALL.size()));
    }
}
