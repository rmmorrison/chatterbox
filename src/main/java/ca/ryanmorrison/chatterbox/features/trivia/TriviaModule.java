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
 * {@code /trivia [difficulty:easy|medium|hard]} — pulls one random question
 * from <a href="https://opentdb.com">Open Trivia DB</a> (no API key) and
 * posts it publicly with answer buttons. First user to click the right
 * answer wins; the round auto-resolves after
 * {@value TriviaHandler#ROUND_TIMEOUT_SECONDS} seconds with no winner.
 *
 * <p>State is in-memory only. Rounds in flight at the time of a bot restart
 * are forgotten — clicks on the orphaned buttons get a polite "round has
 * ended" reply.
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
        return List.of(Commands.slash(TriviaHandler.COMMAND,
                        "Pull a random trivia question. First correct click wins.")
                .addOptions(difficulty));
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
