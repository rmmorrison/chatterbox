package ca.ryanmorrison.chatterbox.features.weather;

import ca.ryanmorrison.chatterbox.features.weather.dto.CurrentCondition;
import ca.ryanmorrison.chatterbox.features.weather.dto.DailyForecast;
import ca.ryanmorrison.chatterbox.features.weather.dto.HourlyForecast;
import ca.ryanmorrison.chatterbox.features.weather.dto.NearestArea;
import ca.ryanmorrison.chatterbox.features.weather.dto.ValueWrapper;
import ca.ryanmorrison.chatterbox.features.weather.dto.WeatherResponse;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherEmbedBuilderTest {

    private static List<ValueWrapper> wrap(String value) {
        return List.of(new ValueWrapper(value));
    }

    private static CurrentCondition torontoCurrent() {
        return new CurrentCondition(
                "8", "47",      // temp C/F
                "7", "44",      // feels-like C/F
                "81",           // humidity
                "116",          // weatherCode (Partly cloudy)
                wrap("Partly cloudy"),
                "9", "6",       // wind kmph/mph
                "SSW",
                "1",            // uv
                "14", "8",      // visibility
                "1006",         // pressure
                "75");          // cloud cover
    }

    private static NearestArea torontoArea() {
        return new NearestArea(wrap("Toronto"), wrap("Ontario"), wrap("Canada"),
                "43.667", "-79.417");
    }

    private static HourlyForecast noon(String code, String desc) {
        return new HourlyForecast("1200", "10", "50", code, wrap(desc), "0", "0");
    }

    private static DailyForecast day(String date, String maxC, String minC, String code, String desc) {
        return new DailyForecast(date,
                maxC, String.valueOf(Integer.parseInt(maxC) * 9 / 5 + 32),
                minC, String.valueOf(Integer.parseInt(minC) * 9 / 5 + 32),
                "?", "?",
                "5",
                List.of(noon(code, desc)));
    }

    private static WeatherResponse sampleResponse() {
        return new WeatherResponse(
                List.of(torontoCurrent()),
                List.of(torontoArea()),
                List.of(
                        day("2026-05-09", "12", "5",  "116", "Partly cloudy"),
                        day("2026-05-10", "15", "7",  "308", "Heavy rain"),
                        day("2026-05-11", "18", "10", "113", "Sunny")));
    }

    // ---- title / location ----

    @Test
    void titleUsesNearestAreaWhenAvailable() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.METRIC);
        assertEquals("Weather in Toronto, Ontario, Canada", embed.getTitle());
    }

    @Test
    void titleFallsBackToRequestedWhenNoArea() {
        WeatherResponse resp = new WeatherResponse(
                List.of(torontoCurrent()), List.of(), List.of());
        MessageEmbed embed = WeatherEmbedBuilder.build(resp, "Some place",
                WeatherEmbedBuilder.Units.METRIC);
        assertEquals("Weather in Some place", embed.getTitle());
    }

    @Test
    void locationLineHandlesPartialFields() {
        // City + country, no region.
        NearestArea area = new NearestArea(wrap("Tokyo"), List.of(), wrap("Japan"), "0", "0");
        assertEquals("Tokyo, Japan",
                WeatherEmbedBuilder.locationLine(Optional.of(area), "fallback"));
    }

    // ---- current description ----

    @Test
    void currentDescriptionMentionsTempFeelsAndWind_metric() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.METRIC);
        String desc = embed.getDescription();
        assertTrue(desc.contains("Partly cloudy"), desc);
        assertTrue(desc.contains("8°C"),  () -> desc);
        assertTrue(desc.contains("7°C"),  () -> desc);
        assertTrue(desc.contains("9 km/h"), () -> desc);
        assertTrue(desc.contains("SSW"),  () -> desc);
        assertTrue(desc.contains("81%"),  () -> desc);
        // The condition emoji (Partly cloudy → 🌤️).
        assertTrue(desc.startsWith("🌤️"), () -> desc);
    }

    @Test
    void currentDescriptionUsesImperialWhenRequested() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.IMPERIAL);
        String desc = embed.getDescription();
        assertTrue(desc.contains("47°F"), desc);
        assertTrue(desc.contains("44°F"), desc);
        assertTrue(desc.contains("6 mph"), desc);
        assertTrue(!desc.contains("°C"), () -> desc);
        assertTrue(!desc.contains("km/h"), () -> desc);
    }

    // ---- forecast block ----

    @Test
    void forecastFieldRenders3DaysWithWeekdayAndHighLow() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.METRIC);
        MessageEmbed.Field forecast = embed.getFields().get(0);
        assertEquals("3-day forecast", forecast.getName());
        String body = forecast.getValue();
        // 2026-05-09 is a Saturday; 2026-05-10 Sunday; 2026-05-11 Monday.
        assertTrue(body.contains("Saturday"),  () -> body);
        assertTrue(body.contains("Sunday"),    () -> body);
        assertTrue(body.contains("Monday"),    () -> body);
        assertTrue(body.contains("↑ 12°C / ↓ 5°C"),  () -> body);
        assertTrue(body.contains("↑ 15°C / ↓ 7°C"),  () -> body);
        assertTrue(body.contains("↑ 18°C / ↓ 10°C"), () -> body);
        assertTrue(body.contains("Heavy rain"), () -> body);
        assertTrue(body.contains("Sunny"),      () -> body);
    }

    @Test
    void forecastImperialUnits() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.IMPERIAL);
        String body = embed.getFields().get(0).getValue();
        assertTrue(body.contains("°F"), body);
        assertTrue(!body.contains("°C"), () -> body);
    }

    // ---- footer ----

    @Test
    void footerCreditsWttrIn() {
        MessageEmbed embed = WeatherEmbedBuilder.build(sampleResponse(), "Toronto",
                WeatherEmbedBuilder.Units.METRIC);
        assertEquals("via wttr.in", embed.getFooter().getText());
    }

    // ---- defensive paths ----

    @Test
    void emptyResponseStillProducesEmbedWithoutThrowing() {
        WeatherResponse empty = new WeatherResponse(List.of(), List.of(), List.of());
        MessageEmbed embed = WeatherEmbedBuilder.build(empty, "Mars",
                WeatherEmbedBuilder.Units.METRIC);
        assertEquals("Weather in Mars", embed.getTitle());
        assertTrue(embed.getDescription().toLowerCase().contains("no current"),
                () -> embed.getDescription());
        assertEquals(0, embed.getFields().size());
    }
}
