package ca.ryanmorrison.chatterbox.features.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code chart.result[0].meta} block from Yahoo's
 * {@code v8/finance/chart/{symbol}} response — everything the embed
 * needs is here, nothing nested.
 *
 * <p>Field names mirror Yahoo's camelCase JSON keys verbatim. Numeric
 * fields are boxed so missing values come through as {@code null}
 * rather than {@code 0.0}, which would otherwise look like real data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartMeta(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("longName") String longName,
        @JsonProperty("shortName") String shortName,
        @JsonProperty("currency") String currency,
        @JsonProperty("exchangeName") String exchangeName,
        @JsonProperty("fullExchangeName") String fullExchangeName,
        @JsonProperty("regularMarketPrice") Double regularMarketPrice,
        @JsonProperty("chartPreviousClose") Double chartPreviousClose,
        @JsonProperty("regularMarketDayHigh") Double regularMarketDayHigh,
        @JsonProperty("regularMarketDayLow") Double regularMarketDayLow,
        @JsonProperty("fiftyTwoWeekHigh") Double fiftyTwoWeekHigh,
        @JsonProperty("fiftyTwoWeekLow") Double fiftyTwoWeekLow,
        @JsonProperty("regularMarketVolume") Long regularMarketVolume,
        @JsonProperty("regularMarketTime") Long regularMarketTime) {

    /** Best-effort display name: longName → shortName → symbol. */
    public String displayName() {
        if (longName != null && !longName.isBlank()) return longName;
        if (shortName != null && !shortName.isBlank()) return shortName;
        return symbol == null ? "" : symbol;
    }
}
