package ca.ryanmorrison.chatterbox.features.nhl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NhlTeamsTest {

    @Test
    void hasAllThirtyTwoFranchises() {
        assertEquals(32, NhlTeams.abbreviations().size());
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertEquals("Toronto Maple Leafs", NhlTeams.displayName("tor").orElseThrow());
        assertEquals("Toronto Maple Leafs", NhlTeams.displayName("TOR").orElseThrow());
        assertTrue(NhlTeams.isKnown("eDm"));
    }

    @Test
    void unknownAbbreviationsReturnEmpty() {
        assertFalse(NhlTeams.isKnown("ZZZ"));
        assertFalse(NhlTeams.isKnown(""));
        assertFalse(NhlTeams.isKnown(null));
    }
}
