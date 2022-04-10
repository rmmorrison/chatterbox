package net.ryanmorrison.chatterbox.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.ryanmorrison.chatterbox.framework.SlashCommandsListenerAdapter;
import net.ryanmorrison.chatterbox.model.frinkiac.Frame;
import net.ryanmorrison.chatterbox.model.frinkiac.FramePreview;
import net.ryanmorrison.chatterbox.model.frinkiac.Subtitle;
import net.ryanmorrison.chatterbox.service.FrinkiacService;
import net.ryanmorrison.chatterbox.service.generator.FrinkiacServiceGenerator;
import org.apache.commons.text.WordUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.awt.*;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class FrinkiacListener extends SlashCommandsListenerAdapter {

    private static final String COMMAND_NAME = "frinkiac";
    private static final String RANDOM_SUBCOMMAND_NAME = "random";
    private static final String SEARCH_SUBCOMMAND_NAME = "search";

    private static final String QUERY_OPTION_NAME = "query";
    private static final String TEXT_OPTION_NAME = "text";

    private static final FrinkiacService service = FrinkiacServiceGenerator.createService(FrinkiacService.class);

    @Override
    public Collection<SlashCommandData> getSupportedCommands() {
        SubcommandData randomSubcommand = new SubcommandData(RANDOM_SUBCOMMAND_NAME, "Returns a screencap at complete random");
        SubcommandData searchSubcommand = new SubcommandData(SEARCH_SUBCOMMAND_NAME, "Search the repository for a screencap")
                .addOption(OptionType.STRING, QUERY_OPTION_NAME, "A phrase to search for in the database", true)
                .addOption(OptionType.STRING, TEXT_OPTION_NAME, "Create a meme using the resulting screencap and text");

        return Collections.singleton(Commands.slash(COMMAND_NAME, "Search Frinkiac, a repository of screencaps from \"The Simpsons\"")
                .addSubcommands(randomSubcommand, searchSubcommand));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        if (!event.getName().equalsIgnoreCase(COMMAND_NAME)) return;

        if (event.getSubcommandName() == null) {
            event.reply("You need to provide the full command for me to execute it! :(")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // let Discord know we're working on it...
        event.deferReply().queue();

        switch (event.getSubcommandName()) {
            case RANDOM_SUBCOMMAND_NAME -> handleRandom(event);
            case SEARCH_SUBCOMMAND_NAME -> handleSearch(event);
            default -> event.reply("I don't support that action! :(")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void handleRandom(SlashCommandInteractionEvent event) {
        service.fetchRandom().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NotNull Call<FramePreview> call, @NotNull Response<FramePreview> response) {
                if (!response.isSuccessful()) {
                    log.warn("Frinkiac random returned unsuccessful response ({} status code)", response.code());
                    event.getHook().sendMessage("Uh oh! I encountered an error while trying to load a random " +
                                    "screencap. Try waiting a few minutes and requesting a random screencap again.")
                            .setEphemeral(true).queue();
                    return;
                }

                FramePreview preview = response.body();
                event.getHook().sendMessage(embedFramePreview(preview, null)).queue();
            }

            @Override
            public void onFailure(@NotNull Call<FramePreview> call, @NotNull Throwable throwable) {
                log.error("An exception occurred upon trying to load a random frame from Frinkiac", throwable);
                event.getHook().sendMessage("Uh oh! I encountered an error while trying to load a random " +
                                "screencap. Try waiting a few minutes and requesting a random screencap again.")
                        .setEphemeral(true).queue();
            }
        });
    }

    private void handleSearch(SlashCommandInteractionEvent event) {
        OptionMapping queryMapping = event.getOption(QUERY_OPTION_NAME);
        if (queryMapping == null || queryMapping.getAsString().isEmpty()) {
            event.getHook().sendMessage("You must supply at least one argument to this command.")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping textMapping = event.getOption(TEXT_OPTION_NAME);

        service.search(queryMapping.getAsString()).enqueue(new Callback<List<Frame>>() {
            @Override
            public void onResponse(Call<List<Frame>> call, Response<List<Frame>> response) {
                if (!response.isSuccessful()) {
                    log.warn("Frinkiac search returned unsuccessful response ({} status code, query '{}')",
                            response.code(), queryMapping.getAsString());
                    event.getHook().sendMessage("Uh oh! I encountered an error while trying to search for your " +
                                    "screencap. Wait a few minutes and try again.")
                            .setEphemeral(true).queue();
                }

                assert response.body() != null; // we shouldn't ever get null here

                if (response.body().size() == 0) {
                    // our query returned no results
                    event.getHook().sendMessage("Nothing found. Try again, for glayvin out loud!")
                            .setEphemeral(true).queue();
                    return;
                }

                Frame first = response.body().get(0);

                service.fetchCaption(first.episode(), first.timestamp()).enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<FramePreview> call, Response<FramePreview> response) {
                        if (!response.isSuccessful()) {
                            log.warn("Frinkiac caption lookup returned unsuccessful response ({} status code, query '{}')",
                                    response.code(), queryMapping.getAsString());
                            event.getHook().sendMessage("Uh oh! I encountered an error while trying to search for your " +
                                            "screencap. Wait a few minutes and try again.")
                                    .setEphemeral(true).queue();
                        }

                        FramePreview preview = response.body();
                        event.getHook().sendMessage(
                                embedFramePreview(preview, textMapping != null ? textMapping.getAsString() : null)
                        ).queue();
                    }

                    @Override
                    public void onFailure(Call<FramePreview> call, Throwable throwable) {
                        log.error("An exception occurred upon trying to load a caption for a Frinkiac frame (query '{}')",
                                queryMapping.getAsString(), throwable);
                        event.getHook().sendMessage("Uh oh! I encountered an error while trying to search for your " +
                                        "screencap. Wait a few minutes and try again.")
                                .setEphemeral(true).queue();
                    }
                });
            }

            @Override
            public void onFailure(Call<List<Frame>> call, Throwable throwable) {
                log.error("An exception occurred upon trying to search for a query from Frinkiac (query '{}')",
                        queryMapping.getAsString(), throwable);
                event.getHook().sendMessage("Uh oh! I encountered an error while trying to search for your " +
                                "screencap. Wait a few minutes and try again.")
                        .setEphemeral(true).queue();
            }
        });
    }

    private Message embedFramePreview(FramePreview framePreview, String memeText) {
        String b64Text = null;
        if (memeText != null) {
            memeText = WordUtils.wrap(memeText, 25, "\n", false); // 25 seems to be the max character length in a Frinkiac frame
            b64Text = Base64.getEncoder().encodeToString(memeText.getBytes());
        }

        String imageUri = b64Text != null ? buildMemeImageURI(b64Text, framePreview) :
                framePreview.frame().getImageURI();

        return new MessageBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setColor(Color.YELLOW)
                        .setTitle(framePreview.subtitles()
                                        .stream()
                                        .map(Subtitle::content)
                                        .collect(Collectors.joining(" ")),
                                framePreview.frame().getCaptureURI())
                        .setImage(imageUri)
                        .addField("Episode", framePreview.frame().episode(), true)
                        .addField("Title", framePreview.episode().title(), true)
                        .addField("Air Date", framePreview.episode().originalAirDate(), true)
                        .build())
                .build();
    }

    private String buildMemeImageURI(String b64Text, FramePreview framePreview) {
        return "https://frinkiac.com/meme/" + framePreview.frame().episode() + "/"
                + framePreview.frame().timestamp() + ".jpg?b64lines=" + b64Text;
    }
}
