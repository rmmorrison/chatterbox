package ca.ryanmorrison.chatterbox.config.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTypeTest {

    @Test
    void booleanParsesTrueAndFalseCaseInsensitive() {
        assertEquals(true,  ConfigType.BOOLEAN.parse("true"));
        assertEquals(true,  ConfigType.BOOLEAN.parse("TRUE"));
        assertEquals(true,  ConfigType.BOOLEAN.parse("  True "));
        assertEquals(false, ConfigType.BOOLEAN.parse("false"));
        assertEquals(false, ConfigType.BOOLEAN.parse("FALSE"));
    }

    @Test
    void booleanRejectsAnythingElse() {
        var ex = assertThrows(ConfigType.InvalidValueException.class, () -> ConfigType.BOOLEAN.parse("yes"));
        assertTrue(ex.getMessage().toLowerCase().contains("true"));
    }

    @Test
    void positiveIntegerRequiresPositive() {
        assertEquals(1,    ConfigType.POSITIVE_INTEGER.parse("1"));
        assertEquals(160,  ConfigType.POSITIVE_INTEGER.parse("160"));
        assertEquals(2000, ConfigType.POSITIVE_INTEGER.parse("  2000 "));

        assertThrows(ConfigType.InvalidValueException.class, () -> ConfigType.POSITIVE_INTEGER.parse("0"));
        assertThrows(ConfigType.InvalidValueException.class, () -> ConfigType.POSITIVE_INTEGER.parse("-5"));
    }

    @Test
    void positiveIntegerRejectsNonInteger() {
        var ex = assertThrows(ConfigType.InvalidValueException.class,
                () -> ConfigType.POSITIVE_INTEGER.parse("abc"));
        assertTrue(ex.getMessage().toLowerCase().contains("integer"));
    }

    @Test
    void formatProducesRoundTripableString() {
        assertEquals("true", ConfigType.BOOLEAN.format(true));
        assertEquals("160",  ConfigType.POSITIVE_INTEGER.format(160));
    }

    @Test
    void nullRawIsRejected() {
        assertThrows(ConfigType.InvalidValueException.class, () -> ConfigType.BOOLEAN.parse(null));
        assertThrows(ConfigType.InvalidValueException.class, () -> ConfigType.POSITIVE_INTEGER.parse(null));
    }
}
