package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.features.weather.dto.CurrentCondition;
import ca.ryanmorrison.chatterbox.features.weather.dto.DailyForecast;
import ca.ryanmorrison.chatterbox.features.weather.dto.HourlyForecast;
import ca.ryanmorrison.chatterbox.features.weather.dto.NearestArea;
import ca.ryanmorrison.chatterbox.features.weather.dto.WeatherResponse;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Renders a {@link WeatherResponse} as a Discord embed.
 *
 * <p>Layout:
 * <ul>
 *   <li>Title: "Weather in {city}, {region}, {country}"</li>
 *   <li>Description: current condition emoji + temp + feels-like + a one-line
 *       summary of wind / humidity / UV.</li>
 *   <li>Three-day forecast as a single field (one line per day with emoji,
 *       weekday name, high/low, and the day's representative description).</li>
 *   <li>Footer: "via wttr.in"</li>
 * </ul>
 *
 * <p>{@link Units#METRIC} renders °C and km/h; {@link Units#IMPERIAL} renders
 * °F and mph. wttr.in returns both, so it's a render-time switch.
 */
final class WeatherEmbedBuilder {

    /** wttr.in's date format (the {@code "date"} field on each forecast day). */
    private static final DateTimeFormatter SOURCE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final Color EMBED_COLOR = new Color(0x4F8DDC);

    enum Units { METRIC, IMPERIAL }

    private WeatherEmbedBuilder() {}

    static MessageEmbed build(WeatherResponse response, String requestedLocation, Units units) {
        EmbedBuilder eb = new EmbedBuilder().setColor(EMBED_COLOR);
        eb.setTitle("Weather in " + locationLine(response.nearest(), requestedLocation));

        Optional<CurrentCondition> currentOpt = response.current();
        if (currentOpt.isEmpty()) {
            eb.setDescription("_No current observation available._");
        } else {
            eb.setDescription(currentDescription(currentOpt.get(), units));
        }

        List<DailyForecast> forecast = response.forecast();
        if (!forecast.isEmpty()) {
            eb.addField("3-day forecast", forecastBlock(forecast, units), false);
        }

        eb.setFooter("via wttr.in");
        return eb.build();
    }

    /** "Toronto, Ontario, Canada" or "Toronto, Canada" or just the requested string. */
    static String locationLine(Optional<NearestArea> nearestOpt, String requestedLocation) {
        if (nearestOpt.isEmpty()) return requestedLocation;
        NearestArea n = nearestOpt.get();
        StringBuilder sb = new StringBuilder();
        n.city().ifPresent(sb::append);
        n.regionName().ifPresent(r -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r);
        });
        n.countryName().ifPresent(c -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(c);
        });
        return sb.length() == 0 ? requestedLocation : sb.toString();
    }

    private static String currentDescription(CurrentCondition c, Units units) {
        String emoji = WeatherEmoji.forCode(c.weatherCode());
        String temp = formatTemp(c.tempC(), c.tempF(), units);
        String feels = formatTemp(c.feelsLikeC(), c.feelsLikeF(), units);
        String wind = formatWind(c.windSpeedKmph(), c.windSpeedMiles(), c.windDir(), units);
        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append(" **").append(c.description().trim()).append("** · ");
        sb.append(temp).append(" (feels like ").append(feels).append(")\n");
        sb.append("💨 Wind ").append(wind);
        if (notBlank(c.humidity()))   sb.append(" · 💧 Humidity ").append(c.humidity()).append("%");
        if (notBlank(c.uvIndex()))    sb.append(" · 🌅 UV ").append(c.uvIndex());
        return sb.toString();
    }

    private static String forecastBlock(List<DailyForecast> forecast, Units units) {
        StringBuilder sb = new StringBuilder();
        for (DailyForecast d : forecast) {
            String weekday = formatWeekday(d.date());
            String high = formatTemp(d.maxTempC(), d.maxTempF(), units);
            String low  = formatTemp(d.minTempC(), d.minTempF(), units);
            Optional<HourlyForecast> noon = d.noonish();
            String emoji = noon.map(h -> WeatherEmoji.forCode(h.weatherCode())).orElse("🌡️");
            String desc  = noon.map(HourlyForecast::description).orElse("");
            sb.append(emoji).append(" **").append(weekday).append("** ");
            sb.append("↑ ").append(high).append(" / ↓ ").append(low);
            if (!desc.isBlank()) sb.append(" — ").append(desc.trim());
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String formatWeekday(String date) {
        if (date == null) return "";
        try {
            return WEEKDAY.format(LocalDate.parse(date, SOURCE_DATE));
        } catch (DateTimeParseException e) {
            return date;
        }
    }

    private static String formatTemp(String celsius, String fahrenheit, Units units) {
        return units == Units.METRIC
                ? formatNumber(celsius) + "°C"
                : formatNumber(fahrenheit) + "°F";
    }

    private static String formatWind(String kmph, String mph, String direction, Units units) {
        String speed = units == Units.METRIC
                ? formatNumber(kmph) + " km/h"
                : formatNumber(mph) + " mph";
        return notBlank(direction) ? speed + " " + direction : speed;
    }

    /** Strips any trailing ".0" wttr.in might emit and falls back to "?" for missing values. */
    private static String formatNumber(String s) {
        if (s == null || s.isBlank()) return "?";
        String t = s.trim();
        if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
        return t;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
