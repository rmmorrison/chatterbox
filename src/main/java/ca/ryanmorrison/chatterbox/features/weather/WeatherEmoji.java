package ca.ryanmorrison.chatterbox.features.weather;

import java.util.Map;

/**
 * Maps wttr.in's {@code weatherCode} (a 3-digit string from the
 * world-weather-online code list) to a Discord-renderable emoji.
 *
 * <p>The full list has ~50 codes; we collapse them into ~10 visual
 * groups (sun, partly cloudy, cloudy, fog, rain, heavy rain, snow,
 * sleet, thunder, ice). Anything we don't recognise falls back to the
 * thermometer so the embed still renders coherently.
 */
final class WeatherEmoji {

    private WeatherEmoji() {}

    private static final String DEFAULT = "🌡️";

    private static final Map<String, String> CODES = Map.<String, String>ofEntries(
            // Clear / sunny
            Map.entry("113", "☀️"),
            // Partly / mostly cloudy
            Map.entry("116", "🌤️"),
            Map.entry("119", "☁️"),
            Map.entry("122", "☁️"),
            // Mist / fog
            Map.entry("143", "🌫️"),
            Map.entry("248", "🌫️"),
            Map.entry("260", "🌫️"),
            // Patchy / light rain
            Map.entry("176", "🌦️"),
            Map.entry("263", "🌦️"),
            Map.entry("266", "🌦️"),
            Map.entry("293", "🌦️"),
            Map.entry("296", "🌦️"),
            Map.entry("353", "🌦️"),
            // Moderate / heavy rain
            Map.entry("299", "🌧️"),
            Map.entry("302", "🌧️"),
            Map.entry("305", "🌧️"),
            Map.entry("308", "🌧️"),
            Map.entry("356", "🌧️"),
            Map.entry("359", "🌧️"),
            // Freezing rain / sleet
            Map.entry("281", "🌨️"),
            Map.entry("284", "🌨️"),
            Map.entry("311", "🌨️"),
            Map.entry("314", "🌨️"),
            Map.entry("317", "🌨️"),
            Map.entry("320", "🌨️"),
            Map.entry("362", "🌨️"),
            Map.entry("365", "🌨️"),
            // Snow
            Map.entry("179", "🌨️"),
            Map.entry("227", "❄️"),
            Map.entry("230", "❄️"),
            Map.entry("323", "🌨️"),
            Map.entry("326", "🌨️"),
            Map.entry("329", "❄️"),
            Map.entry("332", "❄️"),
            Map.entry("335", "❄️"),
            Map.entry("338", "❄️"),
            Map.entry("368", "🌨️"),
            Map.entry("371", "❄️"),
            // Ice pellets / hail
            Map.entry("350", "🌨️"),
            Map.entry("374", "🌨️"),
            Map.entry("377", "🌨️"),
            // Thunder
            Map.entry("200", "⛈️"),
            Map.entry("386", "⛈️"),
            Map.entry("389", "⛈️"),
            Map.entry("392", "⛈️"),
            Map.entry("395", "⛈️"));

    static String forCode(String weatherCode) {
        if (weatherCode == null) return DEFAULT;
        return CODES.getOrDefault(weatherCode.trim(), DEFAULT);
    }
}
