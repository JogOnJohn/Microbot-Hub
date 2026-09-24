package net.runelite.client.plugins.microbot.mmcaves;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

/** Keeps one glide aimed at the moving canvas projection of the next wall click. */
final class MmCavesHoverTracker {
    private static final long CLICK_SETTLE_NANOS = 100_000_000L;
    private volatile WorldPoint wallTile;
    private volatile long startNanos;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private long lastNanos;
    private boolean initialized;

    void start(WorldPoint tile) {
        wallTile = tile;
        startNanos = System.nanoTime();
        initialized = false;
    }

    void stop() {
        wallTile = null;
        initialized = false;
    }

    void onClientTick() {
        WorldPoint tile = wallTile;
        if (tile == null || System.nanoTime() - startNanos < CLICK_SETTLE_NANOS) return;
        if (!Microbot.isLoggedIn() || Microbot.getClient().isMenuOpen()
                || Microbot.getClient().getTopLevelWorldView() == null) return;

        LocalPoint local = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), tile);
        if (local == null) return;
        Point target = Perspective.localToCanvas(Microbot.getClient(), local,
                Microbot.getClient().getTopLevelWorldView().getPlane());
        if (target == null || target.getX() < 0 || target.getY() < 0) return;

        java.awt.Point cursor = Microbot.getMouse().getMousePosition();
        if (cursor == null) return;
        long now = System.nanoTime();
        if (!initialized) {
            x = cursor.x;
            y = cursor.y;
            vx = vy = 0;
            lastNanos = now;
            initialized = true;
            return;
        }

        // A different action or a human moved the cursor; do not fight for control.
        if (Math.hypot(cursor.x - x, cursor.y - y) > 45) {
            stop();
            return;
        }

        double dt = Math.min(0.05, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        if (dt <= 0) return;
        double desiredVx = clamp((target.getX() - x) * 8, -850, 850);
        double desiredVy = clamp((target.getY() - y) * 8, -850, 850);
        vx += clamp(desiredVx - vx, -3500 * dt, 3500 * dt);
        vy += clamp(desiredVy - vy, -3500 * dt, 3500 * dt);
        x += vx * dt;
        y += vy * dt;
        if (wallTile == tile && (Math.round(x) != cursor.x || Math.round(y) != cursor.y)) {
            Microbot.getMouse().move((int) Math.round(x), (int) Math.round(y));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
