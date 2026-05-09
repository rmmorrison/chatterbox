package ca.ryanmorrison.chatterbox.features.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WeatherEmojiTest {

    @Test
    void clearAndSunnyMapToSun() {
        assertEquals("☀️", WeatherEmoji.forCode("113"));
    }

    @Test
    void partlyCloudyAndCloudy() {
        assertEquals("🌤️", WeatherEmoji.forCode("116"));
        assertEquals("☁️", WeatherEmoji.forCode("119"));
        assertEquals("☁️", WeatherEmoji.forCode("122"));
    }

    @Test
    void rainCodes() {
        assertEquals("🌦️", WeatherEmoji.forCode("176"));
        assertEquals("🌧️", WeatherEmoji.forCode("308"));
    }

    @Test
    void snowCodes() {
        assertEquals("❄️", WeatherEmoji.forCode("338"));
    }

    @Test
    void thunderCodes() {
        assertEquals("⛈️", WeatherEmoji.forCode("200"));
        assertEquals("⛈️", WeatherEmoji.forCode("389"));
    }

    @Test
    void unknownCodeFallsBackToThermometer() {
        assertEquals("🌡️", WeatherEmoji.forCode("999"));
        assertEquals("🌡️", WeatherEmoji.forCode(""));
        assertEquals("🌡️", WeatherEmoji.forCode(null));
    }

    @Test
    void differentCategoriesProduceDifferentEmoji() {
        // Loose sanity: sun, cloud, rain, snow, thunder shouldn't collapse.
        assertNotEquals(WeatherEmoji.forCode("113"), WeatherEmoji.forCode("119"));
        assertNotEquals(WeatherEmoji.forCode("119"), WeatherEmoji.forCode("308"));
        assertNotEquals(WeatherEmoji.forCode("308"), WeatherEmoji.forCode("338"));
        assertNotEquals(WeatherEmoji.forCode("338"), WeatherEmoji.forCode("200"));
    }
}
