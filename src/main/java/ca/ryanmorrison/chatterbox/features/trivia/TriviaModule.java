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
import java.util.Map;

/**
 * {@code /trivia [difficulty] [category] [rounds]} — runs a multi-round
 * trivia game in the channel, sourcing questions from
 * <a href="https://opentdb.com">Open Trivia DB</a> (no API key).
 *
 * <p>Each round posts publicly with answer buttons; first user to click
 * the correct answer wins that round. After {@code rounds} rounds (default
 * {@value TriviaHandler#DEFAULT_ROUNDS}, max {@value TriviaHandler#MAX_ROUNDS}),
 * a leaderboard embed posts in the same channel. Rounds that nobody
 * answers within {@value TriviaHandler#ROUND_TIMEOUT_SECONDS} seconds
 * auto-resolve and the game continues to the next round.
 *
 * <p>State is in-memory only. Games in flight at the time of a bot
 * restart are forgotten — clicks on the orphaned buttons get a polite
 * "round has ended" reply.
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

        OptionData category = new OptionData(OptionType.INTEGER,
                TriviaHandler.OPT_CATEGORY,
                "Restrict to a single Open Trivia DB category.",
                false, false);
        for (Map.Entry<String, Integer> entry : TriviaCategories.all().entrySet()) {
            category.addChoice(entry.getKey(), entry.getValue());
        }

        OptionData numRounds = new OptionData(OptionType.INTEGER,
                TriviaHandler.OPT_ROUNDS,
                "How many rounds to play (1–" + TriviaHandler.MAX_ROUNDS
                        + ", default " + TriviaHandler.DEFAULT_ROUNDS + ").",
                false, false)
                .setRequiredRange(TriviaHandler.MIN_ROUNDS, TriviaHandler.MAX_ROUNDS);

        return List.of(Commands.slash(TriviaHandler.COMMAND,
                        "Play a trivia game. First correct click wins each round.")
                .addOptions(difficulty, category, numRounds));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        if (handler == null) {
            handler = new TriviaHandler(new TriviaClient(), new TriviaRounds());
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
