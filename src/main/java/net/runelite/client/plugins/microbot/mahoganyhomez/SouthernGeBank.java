package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

final class SouthernGeBank
{
    private SouthernGeBank() { }

    static boolean isSouthernBooth(WorldPoint point)
    {
        // The exchange's southern banking counters, not the northern counters/traders.
        return point != null && point.getPlane() == 0
            && point.getX() >= 3155 && point.getX() <= 3177
            && point.getY() >= 3478 && point.getY() <= 3489;
    }

    static WorldPoint choose(List<WorldPoint> available, WorldPoint previous, IntUnaryOperator randomIndex)
    {
        List<WorldPoint> pool = available.stream().filter(SouthernGeBank::isSouthernBooth)
            .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (pool.size() > 1) pool.remove(previous);
        return pool.isEmpty() ? null : pool.get(randomIndex.applyAsInt(pool.size()));
    }
}
