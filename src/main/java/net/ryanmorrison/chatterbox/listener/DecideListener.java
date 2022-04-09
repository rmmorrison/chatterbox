package net.ryanmorrison.chatterbox.listener;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Random;

@Component
public class DecideListener extends SlashCommandsListenerAdapter {

    private static final String COMMAND_NAME = "decide";
    private static final String CHOICES_OPTION_NAME = "choices";
    private final String[] responses = new String[] {
            "I choose...",
            "The crystal ball in my head says: ",
            "I think I'm going to have to go with ",
            "It seems to me the best option is ",
            "You should consider ",
            "Oh, this one's easy: ",
            "Why are you asking me? You know the correct choice is ",
            "The smart people here would go with ",
            "I mean, I'm just a random number generator, but here you go: ",
            "EZ PZ, "
    };
    private final String[] stalls = new String[] {
            "Hmm, my crystal ball isn't working right now. Try again later?",
            "Oops, I dropped and smashed my 8-Ball. You'll have to wait for Amazon to deliver a new one.",
            "These choices are just too hard to choose between, sorry. Maybe try asking again in a little bit?",
            "Sorry, I have to restock the shelves in the Walmart Metaverse. I'll be back in a bit.",
            "What? It's too early for decisions. Ask again when I've finished my nap.",
            "Oh dear, I can't get to this decision right now. I'm late for my vaccine appointment.",
            "I'd love to answer this but unfortunately I have an awful headache at the moment. Ask again in awhile?",
            "I'm not in the mood for this.",
            "Sorry, I'm quite busy at the moment with Euro Truck Simulator 2 deliveries. Ask again when I'm off the clock.",
            "I can't talk right now, there's a Demon chasing me in Phasmophobia right now and my cores are a little preoccupied with that."
    };
    private final Random random = new Random();

    @Override
    public Collection<SlashCommandData> getSupportedCommands() {
        return Collections.singleton(Commands.slash(COMMAND_NAME, "Chooses a random selection from options " +
                "provided or a yes/no result if only one option is given")
                .addOption(OptionType.STRING, CHOICES_OPTION_NAME, "One or more options to choose from, " +
                        "separated by \" or \"", true));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        if (!event.getName().equalsIgnoreCase(COMMAND_NAME)) return;

        // let Discord know we're working on it...
        event.deferReply().queue();

        OptionMapping choiceMapping = event.getOption(CHOICES_OPTION_NAME);
        if (choiceMapping == null || choiceMapping.getAsString().isEmpty()) {
            event.getHook().sendMessage("One or more choices (separated by \" or \") are required.")
                    .setEphemeral(true).queue();
            return;
        }

        MessageBuilder builder = new MessageBuilder();

        // are we stalling?
        if (random.nextDouble() > 0.80) {
            builder.append(stalls[random.nextInt(stalls.length - 1)]);
        } else {
            // get our answering prefix...
            builder.append(responses[random.nextInt(responses.length - 1)]);

            String[] choices = choiceMapping.getAsString().split(" or ");
            if (choices.length == 1) {
                builder.append(random.nextBoolean() ? "yes" : "no", MessageBuilder.Formatting.BOLD);
            } else {
                builder.append(choices[random.nextInt(choices.length - 1)], MessageBuilder.Formatting.BOLD);
            }
        }

        builder.append(".");
        event.getHook().sendMessage(builder.build()).queue();
    }
}
