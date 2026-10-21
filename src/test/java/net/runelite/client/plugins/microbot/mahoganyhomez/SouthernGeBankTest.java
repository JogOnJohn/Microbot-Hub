package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SouthernGeBankTest
{
    private final WorldPoint west = new WorldPoint(3163, 3487, 0);
    private final WorldPoint east = new WorldPoint(3168, 3487, 0);

    @Test void excludesNorthernCountersAndOtherBanks()
    {
        assertTrue(SouthernGeBank.isSouthernBooth(west));
        assertFalse(SouthernGeBank.isSouthernBooth(new WorldPoint(3163, 3491, 0)));
        assertFalse(SouthernGeBank.isSouthernBooth(new WorldPoint(3163, 3487, 1)));
        assertFalse(SouthernGeBank.isSouthernBooth(new WorldPoint(3093, 3491, 0)));
        assertFalse(SouthernGeBank.isSouthernBooth(null));
    }

    @Test void randomIndexCanSelectEitherSouthernCounter()
    {
        assertEquals(west, SouthernGeBank.choose(List.of(west, east), null, bound -> 0));
        assertEquals(east, SouthernGeBank.choose(List.of(west, east), null, bound -> bound - 1));
    }

    @Test void avoidsImmediateRepeatWhenAnAlternativeExists()
    {
        assertEquals(east, SouthernGeBank.choose(List.of(west, east), west, bound -> 0));
        assertEquals(west, SouthernGeBank.choose(List.of(west, east), east, bound -> 0));
    }

    @Test void oneAvailableBoothCanStillBeUsedAgain()
    {
        assertEquals(west, SouthernGeBank.choose(List.of(west), west, bound -> 0));
    }

    @Test void staleNullAndDuplicateTilesDoNotDistortSelection()
    {
        assertEquals(east, SouthernGeBank.choose(Arrays.asList(null, west, west, east,
            new WorldPoint(3163, 3491, 0)), west, bound -> {
                assertEquals(1, bound);
                return 0;
            }));
    }

    @Test void emptySelectionDoesNotCallRandom()
    {
        assertNull(SouthernGeBank.choose(List.of(), null, bound -> {
            fail("No random choice for an empty candidate list");
            return 0;
        }));
    }
}
