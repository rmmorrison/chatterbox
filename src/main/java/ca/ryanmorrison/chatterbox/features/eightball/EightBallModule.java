package ca.ryanmorrison.chatterbox.features.eightball;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.EnumSet;
import java.util.List;

/**
 * {@code /8ball} — ask the bot a question, get one of the 20 classic Magic 8
 * Ball answers chosen at random.
 *
 * <p>Public reply by default since the joke wants an audience; pass
 * {@code private:true} to keep it ephemeral. Mentions in the question text
 * are suppressed so {@code /8ball question:should @everyone get pinged?}
 * can't actually ping anyone.
 */
public final class EightBallModule extends ListenerAdapter implements Module {

    static final String COMMAND     = "8ball";
    static final String OPT_QUESTION = "question";
    static final String OPT_PRIVATE  = "private";

    /**
     * Trim the echoed question if it'd push the rendered reply past Discord's
     * 2000-char message cap. Generous; real questions are short.
     */
    static final int MAX_QUESTION_LENGTH = 1500;

    @Override public String name() { return "eightball"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        return List.of(Commands.slash(COMMAND, "Ask the Magic 8 Ball a question.")
                .addOption(OptionType.STRING, OPT_QUESTION,
                        "What do you want to know?", true)
                .addOption(OptionType.BOOLEAN, OPT_PRIVATE,
                        "Show the answer only to you instead of the channel.", false));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND.equals(event.getName())) return;

        OptionMapping questionOption = event.getOption(OPT_QUESTION);
        String question = questionOption == null ? "" : questionOption.getAsString().trim();
        if (question.isEmpty()) {
            event.reply("Give the 8 ball a question to ponder.")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping privateOption = event.getOption(OPT_PRIVATE);
        boolean ephemeral = privateOption != null && privateOption.getAsBoolean();

        String body = renderResult(question, EightBallAnswers.pick());
        event.reply(body)
                .setEphemeral(ephemeral)
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class))
                .queue();
    }

    static String renderResult(String question, String answer) {
        String echoed = question.length() > MAX_QUESTION_LENGTH
                ? question.substring(0, MAX_QUESTION_LENGTH - 1) + "…"
                : question;
        return "🎱 *\"" + echoed + "\"*\n**" + answer + "**";
    }
}
