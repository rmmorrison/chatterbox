package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.features.stock.dto.ChartMeta;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles {@code /stock symbol:<text> [private:<bool>]}.
 *
 * <p>Defers the reply (network call), fetches the chart meta via
 * {@link StockClient}, and renders an embed via {@link StockEmbedBuilder}.
 * Failures map to ephemeral text replies via
 * {@link StockClient.StockException}; raw exceptions are never surfaced.
 */
final class StockHandler extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(StockHandler.class);

    private final StockClient client;

    StockHandler(StockClient client) {
        this.client = client;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!StockModule.COMMAND.equals(event.getName())) return;

        String symbol = stringOption(event, StockModule.OPT_SYMBOL);
        if (symbol == null || symbol.isBlank()) {
            event.reply("Tell me a stock symbol.").setEphemeral(true).queue();
            return;
        }
        boolean ephemeral = booleanOption(event, StockModule.OPT_PRIVATE);

        event.deferReply(ephemeral).queue();

        ChartMeta meta;
        try {
            meta = client.fetch(symbol);
        } catch (StockClient.StockException e) {
            event.getHook().sendMessage(e.getMessage()).queue();
            return;
        } catch (RuntimeException e) {
            log.warn("Unexpected error fetching stock for {}", symbol, e);
            event.getHook().sendMessage("Something went wrong fetching that quote.").queue();
            return;
        }

        MessageEmbed embed = StockEmbedBuilder.build(meta);
        event.getHook().sendMessageEmbeds(embed).queue();
    }

    private static String stringOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt == null ? null : opt.getAsString();
    }

    private static boolean booleanOption(SlashCommandInteractionEvent event, String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null && opt.getAsBoolean();
    }
}
