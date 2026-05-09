package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.features.stock.dto.ChartMeta;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Renders a {@link ChartMeta} as a Discord embed.
 *
 * <p>Layout:
 * <ul>
 *   <li>Title: "{display name} ({symbol})", hyperlinked to the Yahoo Finance quote page.</li>
 *   <li>Description: current price + change (absolute and %, with ▲/▼/▬),
 *       day range, 52-week range, volume.</li>
 *   <li>Embed colour reflects the day's change: green up, red down,
 *       grey flat.</li>
 *   <li>Footer: exchange + currency + freshness timestamp + a "may be delayed"
 *       note (Yahoo's free feed is typically 15-minute-delayed by exchange policy).</li>
 * </ul>
 */
final class StockEmbedBuilder {

    static final Color UP_COLOR   = new Color(0x4CAF50);
    static final Color DOWN_COLOR = new Color(0xE53935);
    static final Color FLAT_COLOR = new Color(0x90A4AE);

    private static final NumberFormat VOLUME_FORMAT = NumberFormat.getInstance(Locale.US);

    private StockEmbedBuilder() {}

    static MessageEmbed build(ChartMeta meta) {
        Double price = meta.regularMarketPrice();
        Double prev  = meta.chartPreviousClose();
        Double change = (price != null && prev != null) ? price - prev : null;
        Double changePct = (change != null && prev != null && prev != 0.0) ? (change / prev) * 100.0 : null;

        EmbedBuilder eb = new EmbedBuilder().setColor(colorFor(change));
        eb.setTitle(titleFor(meta), quoteUrl(meta.symbol()));
        eb.setDescription(buildDescription(meta, price, change, changePct));
        eb.setFooter(footerFor(meta));
        if (meta.regularMarketTime() != null && meta.regularMarketTime() > 0) {
            eb.setTimestamp(java.time.Instant.ofEpochSecond(meta.regularMarketTime()));
        }
        return eb.build();
    }

    private static String titleFor(ChartMeta meta) {
        String name = meta.displayName();
        String symbol = meta.symbol() == null ? "" : meta.symbol();
        if (name.isBlank()) return symbol;
        if (name.equals(symbol)) return symbol;
        return name + " (" + symbol + ")";
    }

    private static String quoteUrl(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        return "https://finance.yahoo.com/quote/"
                + java.net.URLEncoder.encode(symbol, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String buildDescription(ChartMeta meta, Double price, Double change, Double changePct) {
        String currency = meta.currency() == null ? "" : meta.currency();
        StringBuilder sb = new StringBuilder();

        // Headline: arrow + price + change.
        sb.append(arrowFor(change)).append(" ").append("**");
        sb.append(formatPrice(price));
        if (!currency.isBlank()) sb.append(" ").append(currency);
        sb.append("**");
        if (change != null && changePct != null) {
            sb.append("  ").append(formatChange(change, changePct));
        }
        sb.append('\n');

        if (meta.regularMarketDayLow() != null || meta.regularMarketDayHigh() != null) {
            sb.append("Day: ")
                    .append(formatPrice(meta.regularMarketDayLow()))
                    .append(" – ")
                    .append(formatPrice(meta.regularMarketDayHigh()))
                    .append('\n');
        }
        if (meta.fiftyTwoWeekLow() != null || meta.fiftyTwoWeekHigh() != null) {
            sb.append("52-week: ")
                    .append(formatPrice(meta.fiftyTwoWeekLow()))
                    .append(" – ")
                    .append(formatPrice(meta.fiftyTwoWeekHigh()))
                    .append('\n');
        }
        if (meta.regularMarketVolume() != null && meta.regularMarketVolume() > 0) {
            sb.append("Volume: ")
                    .append(VOLUME_FORMAT.format(meta.regularMarketVolume()));
        }
        return sb.toString().stripTrailing();
    }

    private static String footerFor(ChartMeta meta) {
        StringBuilder f = new StringBuilder();
        if (meta.fullExchangeName() != null && !meta.fullExchangeName().isBlank()) {
            f.append(meta.fullExchangeName());
        }
        if (meta.currency() != null && !meta.currency().isBlank()) {
            if (f.length() > 0) f.append(" · ");
            f.append(meta.currency());
        }
        if (f.length() > 0) f.append(" · ");
        f.append("via Yahoo Finance · prices may be delayed");
        return f.toString();
    }

    static Color colorFor(Double change) {
        if (change == null) return FLAT_COLOR;
        if (change > 0)     return UP_COLOR;
        if (change < 0)     return DOWN_COLOR;
        return FLAT_COLOR;
    }

    static String arrowFor(Double change) {
        if (change == null) return "▬";
        if (change > 0)     return "▲";
        if (change < 0)     return "▼";
        return "▬";
    }

    /** "$293.32" / "—" for missing. */
    static String formatPrice(Double v) {
        if (v == null) return "—";
        // Two decimals for prices; use `f` formatting (no thousands separator on prices —
        // matches how most financial UIs render).
        return String.format(Locale.US, "%.2f", v);
    }

    /** "+$5.89 (+2.05%)" / "−$5.89 (−2.05%)" / "±0.00 (0.00%)". */
    static String formatChange(double change, double pct) {
        char sign = change > 0 ? '+' : (change < 0 ? '−' : '±');
        // For negative numbers, drop the built-in "-" sign and replace with the
        // typographic minus we picked above.
        return sign + String.format(Locale.US, "%.2f (%+.2f%%)", Math.abs(change), pct);
    }
}
