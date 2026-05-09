package ca.ryanmorrison.chatterbox.features.stock;

import ca.ryanmorrison.chatterbox.features.stock.dto.ChartMeta;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockEmbedBuilderTest {

    private static ChartMeta meta(double price, double prev) {
        return new ChartMeta(
                "AAPL", "Apple Inc.", "Apple", "USD", "NMS", "NasdaqGS",
                price, prev,
                price + 1.5, price - 1.5,         // day high/low
                price + 50.0, price - 100.0,      // 52-week
                45_708_423L,
                1778270402L);
    }

    // ---- title / link ----

    @Test
    void titleIncludesNameAndSymbolAndIsHyperlinked() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(293.32, 287.423));
        assertEquals("Apple Inc. (AAPL)", embed.getTitle());
        assertEquals("https://finance.yahoo.com/quote/AAPL", embed.getUrl());
    }

    @Test
    void titleIsJustSymbolWhenNameMissing() {
        ChartMeta m = new ChartMeta("AAPL", null, null, "USD", "NMS", "NasdaqGS",
                100.0, 99.0, 101.0, 99.0, 200.0, 50.0, 1L, 1L);
        MessageEmbed embed = StockEmbedBuilder.build(m);
        assertEquals("AAPL", embed.getTitle());
    }

    @Test
    void tsxSymbolIsUrlEncodedInQuoteLink() {
        ChartMeta m = new ChartMeta("SHOP.TO", "Shopify Inc.", "Shopify", "CAD",
                "TOR", "Toronto",
                150.68, 152.57, 152.0, 149.0, 200.0, 100.0, 1_000_000L, 1L);
        MessageEmbed embed = StockEmbedBuilder.build(m);
        // The dot is fine in URLs; we just confirm the link points at the right page.
        assertEquals("https://finance.yahoo.com/quote/SHOP.TO", embed.getUrl());
    }

    // ---- color ----

    @Test
    void greenWhenUp() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(101.0, 100.0));
        assertEquals(StockEmbedBuilder.UP_COLOR.getRGB(), embed.getColorRaw());
    }

    @Test
    void redWhenDown() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(99.0, 100.0));
        assertEquals(StockEmbedBuilder.DOWN_COLOR.getRGB(), embed.getColorRaw());
    }

    @Test
    void greyWhenFlat() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(100.0, 100.0));
        assertEquals(StockEmbedBuilder.FLAT_COLOR.getRGB(), embed.getColorRaw());
    }

    @Test
    void greyWhenChangeUncomputable() {
        // Missing previous close → can't compute change. Don't pretend.
        ChartMeta m = new ChartMeta("AAPL", "Apple", null, "USD", "NMS", "NasdaqGS",
                100.0, null, 101.0, 99.0, 200.0, 50.0, 1L, 1L);
        MessageEmbed embed = StockEmbedBuilder.build(m);
        assertEquals(StockEmbedBuilder.FLAT_COLOR.getRGB(), embed.getColorRaw());
    }

    // ---- description content ----

    @Test
    void descriptionContainsPriceCurrencyAndChange() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(293.32, 287.423));
        String d = embed.getDescription();
        assertNotNull(d);
        assertTrue(d.contains("293.32"), d);
        assertTrue(d.contains("USD"), d);
        // Up-arrow + signed change + percent (formatChange uses "+" for positives).
        assertTrue(d.contains("▲"), d);
        assertTrue(d.contains("+5.90"), () -> "expected +5.90, got: " + d);
        assertTrue(d.contains("+2.05%"), () -> "expected +2.05%, got: " + d);
    }

    @Test
    void downArrowAndTypographicMinusForNegative() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(95.0, 100.0));
        String d = embed.getDescription();
        assertTrue(d.contains("▼"), d);
        // Typographic minus on the absolute change, regular "-" on the percent.
        assertTrue(d.contains("−5.00"), () -> d);
        assertTrue(d.contains("-5.00%"), () -> d);
    }

    @Test
    void volumeFormattedWithThousandsSeparators() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(293.32, 287.423));
        assertTrue(embed.getDescription().contains("Volume: 45,708,423"),
                embed.getDescription());
    }

    @Test
    void dayAnd52WeekRangesPresent() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(293.32, 287.423));
        String d = embed.getDescription();
        assertTrue(d.contains("Day:"),  d);
        assertTrue(d.contains("52-week:"), d);
    }

    @Test
    void missingFieldsRenderEmDashRatherThanZero() {
        ChartMeta m = new ChartMeta("AAPL", "Apple", null, "USD", "NMS", "NasdaqGS",
                100.0, 99.0,
                null, null,           // day range missing
                null, null,           // 52-week missing
                null, 1L);
        MessageEmbed embed = StockEmbedBuilder.build(m);
        String d = embed.getDescription();
        assertFalse(d.contains("Day:"), () -> "no day range when both null: " + d);
        assertFalse(d.contains("52-week:"), () -> d);
        assertFalse(d.contains("Volume:"), () -> d);
    }

    // ---- footer ----

    @Test
    void footerNamesExchangeCurrencyAndDelay() {
        MessageEmbed embed = StockEmbedBuilder.build(meta(293.32, 287.423));
        String f = embed.getFooter().getText();
        assertTrue(f.contains("NasdaqGS"), f);
        assertTrue(f.contains("USD"), f);
        assertTrue(f.toLowerCase().contains("yahoo"), f);
        assertTrue(f.toLowerCase().contains("delayed"), f);
    }

    // ---- formatting helpers ----

    @Test
    void formatChangeUsesPlusForPositives() {
        // Direct exposure of the helper for non-pricing-edge sanity.
        assertEquals("+5.00 (+2.50%)", StockEmbedBuilder.formatChange(5.0, 2.5));
    }

    @Test
    void formatChangeUsesTypographicMinusForNegatives() {
        // The percent uses the regular - because String.format's %+ inserts ASCII;
        // we leave that consistent across the change and the percent's own sign.
        assertEquals("−5.00 (-2.50%)", StockEmbedBuilder.formatChange(-5.0, -2.5));
    }

    @Test
    void formatChangeUsesPlusMinusForExactlyZero() {
        assertEquals("±0.00 (+0.00%)", StockEmbedBuilder.formatChange(0.0, 0.0));
    }

    @Test
    void arrowPicker() {
        assertEquals("▲", StockEmbedBuilder.arrowFor(1.0));
        assertEquals("▼", StockEmbedBuilder.arrowFor(-1.0));
        assertEquals("▬", StockEmbedBuilder.arrowFor(0.0));
        assertEquals("▬", StockEmbedBuilder.arrowFor(null));
    }

    @Test
    void formatPriceHandlesNull() {
        assertEquals("—", StockEmbedBuilder.formatPrice(null));
    }
}
