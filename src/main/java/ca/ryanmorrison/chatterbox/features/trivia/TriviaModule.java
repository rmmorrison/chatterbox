package ca.ryanmorrison.chatterbox.features.trivia;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import ca.ryanmorrison.chatterbox.module.ModuleContext;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /trivia [difficulty] [category] [rounds] [lobby]} — runs a
 * multi-round trivia session in the channel, sourcing questions from
 * <a href="https://opentdb.com">Open Trivia DB</a> (no API key).
 *
 * <p>Lobby: configurable via {@code lobby:<seconds>} (default 30, range
 * 5–120). Players opt in by clicking Join; when the lobby closes the
 * roster is frozen and rounds run with all joined players answering
 * simultaneously. Each round resolves on the 20s timer or when every
 * joined player has answered.
 *
 * <p>Categories are autocomplete-driven and lazy-loaded from
 * {@code /api_category.php} on first use; nothing is hardcoded.
 *
 * <p>Per-channel single session: a second {@code /trivia} in the same
 * channel while a session is active is rejected with a brief ephemeral
 * reply.
 */
public final class TriviaModule implements Module {

    private TriviaHandler handler;

    @Override public String name() { return "trivia"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData difficulty = new OptionData(OptionType.STRING,
                TriviaHandler.OPT_DIFFICULTY,
                "Filter by difficulty.", false, false)
                .addChoice("Easy", "easy")
                .addChoice("Medium", "medium")
                .addChoice("Hard", "hard");

        // Autocomplete-driven so categories come from the live opentdb
        // list and nothing is baked into the slash registration.
        OptionData category = new OptionData(OptionType.INTEGER,
                TriviaHandler.OPT_CATEGORY,
                "Restrict to a single Open Trivia DB category.",
                false, true);

        OptionData numRounds = new OptionData(OptionType.INTEGER,
                TriviaHandler.OPT_ROUNDS,
                "How many rounds to play (1–" + TriviaHandler.MAX_ROUNDS
                        + ", default " + TriviaHandler.DEFAULT_ROUNDS + ").",
                false, false)
                .setRequiredRange(TriviaHandler.MIN_ROUNDS, TriviaHandler.MAX_ROUNDS);

        OptionData lobby = new OptionData(OptionType.INTEGER,
                TriviaHandler.OPT_LOBBY,
                "Seconds to wait for players to join before starting (default "
                        + TriviaHandler.DEFAULT_LOBBY_SECS + ").",
                false, false)
                .setRequiredRange(TriviaHandler.MIN_LOBBY_SECONDS, TriviaHandler.MAX_LOBBY_SECONDS);

        return List.of(Commands.slash(TriviaHandler.COMMAND,
                        "Start a trivia session. Players opt in during a lobby, then play together.")
                .addOptions(difficulty, category, numRounds, lobby));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        if (handler == null) {
            TriviaClient client = new TriviaClient();
            TriviaCategoryCache cache = new TriviaCategoryCache(client);
            handler = new TriviaHandler(client, new TriviaRounds(), cache);
        }
        return List.of(handler);
    }

    @Override
    public void onStart(ModuleContext ctx) {
        if (handler != null) handler.setJda(ctx.jda());
    }

    @Override
    public void onStop() {
        if (handler != null) handler.shutdown();
    }
}
