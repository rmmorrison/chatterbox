package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.module.InitContext;
import ca.ryanmorrison.chatterbox.module.Module;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

/**
 * {@code /stock symbol:<text> [private:<bool>]} — current quote for a
 * NYSE / NASDAQ / TSX symbol via Yahoo Finance's
 * {@code v8/finance/chart} endpoint (no API key).
 *
 * <p>Symbol convention follows Yahoo: NYSE/NASDAQ tickers as-is
 * ({@code AAPL}, {@code KO}), TSX with {@code .TO} suffix
 * ({@code SHOP.TO}, {@code RY.TO}). Bad symbols return a clear "couldn't
 * find" with a format hint. See {@link StockClient} for caveats around
 * the unofficial endpoint and the typical 15-minute delay on free quotes.
 */
public final class StockModule implements Module {

    static final String COMMAND     = "stock";
    static final String OPT_SYMBOL  = "symbol";
    static final String OPT_PRIVATE = "private";

    @Override public String name() { return "stock"; }

    @Override
    public List<SlashCommandData> slashCommands(InitContext ctx) {
        OptionData symbol = new OptionData(OptionType.STRING, OPT_SYMBOL,
                "Ticker — e.g. AAPL (NASDAQ), KO (NYSE), SHOP.TO (TSX).",
                true, false);
        OptionData priv = new OptionData(OptionType.BOOLEAN, OPT_PRIVATE,
                "Show the result only to you instead of the channel.",
                false, false);
        return List.of(Commands.slash(COMMAND,
                        "Look up a stock quote (NYSE / NASDAQ / TSX) via Yahoo Finance.")
                .addOptions(symbol, priv));
    }

    @Override
    public List<EventListener> listeners(InitContext ctx) {
        return List.of(new StockHandler(new StockClient()));
    }
}
