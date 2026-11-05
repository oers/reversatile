package de.earthlingz.oerszebra;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SearchDepthTest {

    private static final String DEFAULT = "8|16|0";

    @Test
    public void fixedLevelIsUsedAsIs() {
        assertDepth(SearchDepth.resolve("20|22|0", "30|30|0", DEFAULT), 20, 22, 0);
        assertDepth(SearchDepth.resolve("4|12|12", null, DEFAULT), 4, 12, 12);
    }

    @Test
    public void customTakesTheSavedDepths() {
        assertDepth(SearchDepth.resolve(SearchDepth.CUSTOM, "30|26|0", DEFAULT), 30, 26, 0);
    }

    @Test
    public void customIsClampedToTheAllowedRange() {
        assertDepth(SearchDepth.resolve(SearchDepth.CUSTOM, "99|99|5", DEFAULT),
                SearchDepth.MAX_MIDGAME, SearchDepth.MAX_EXACT, 0);
        assertDepth(SearchDepth.resolve(SearchDepth.CUSTOM, "0|-3|0", DEFAULT),
                SearchDepth.MIN_MIDGAME, SearchDepth.MIN_EXACT, 0);
    }

    @Test
    public void unreadableValuesFallBackToTheDefault() {
        assertDepth(SearchDepth.resolve(SearchDepth.CUSTOM, null, DEFAULT), 8, 16, 0);
        assertDepth(SearchDepth.resolve(SearchDepth.CUSTOM, "abc", DEFAULT), 8, 16, 0);
        assertDepth(SearchDepth.resolve("1|2", null, DEFAULT), 8, 16, 0);
    }

    @Test
    public void formatClampsAndRoundTrips() {
        assertEquals("24|24|0", SearchDepth.format(24, 24));
        assertEquals(SearchDepth.MAX_MIDGAME + "|0|0", SearchDepth.format(100, -1));
        assertDepth(SearchDepth.parse(SearchDepth.format(33, 28)), 33, 28, 0);
    }

    @Test
    public void parseRejectsMalformedInput() {
        assertNull(SearchDepth.parse(null));
        assertNull(SearchDepth.parse(""));
        assertNull(SearchDepth.parse("1|2|x"));
    }

    @Test
    public void customKeyAppendsTheSuffix() {
        assertEquals("settings_engine_strength_custom",
                SearchDepth.customKey(GlobalSettingsLoader.SETTINGS_KEY_STRENGTH));
    }

    private static void assertDepth(SearchDepth depth, int midgame, int exact, int wld) {
        assertEquals(midgame, depth.midgame);
        assertEquals(exact, depth.exact);
        assertEquals(wld, depth.wld);
    }
}
