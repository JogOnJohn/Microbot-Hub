package net.runelite.client.plugins.microbot.agility;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import java.util.Set;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.sharedautomation.mouse.MouseIntentController;
import net.runelite.client.plugins.microbot.sharedautomation.mouse.MousePort;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;

/** Owns the cursor only during an obstacle wait. All game reads and controller calls are client-tick confined. */
final class RooftopPrehover
{
    private static final String OWNER = "agility-rooftop";
    private final MouseIntentController controller = new MouseIntentController(new MousePort()
    {
        public Point getPosition()
        {
            net.runelite.api.Point p = Microbot.getClient().getMouseCanvasPosition();
            return new Point(p.getX(), p.getY());
        }
        public void moveTo(int x, int y) { Microbot.getMouse().move(x, y); }
    });
    private AgilityCourse course;
    private WorldPoint origin;
    private int targetId = -1;
    private boolean wraps;
    private long expires;
    private volatile String status = "Idle";
    private long trackedTicks;
    private long requests;
    private TileObject cachedTarget;
    private long lastScan;
    private boolean resetMotion;
    private boolean reached;
    private boolean canLootMarks;
    private Set<WorldPoint> ignoredMarks;
    private String activeTargetKey = "";

    synchronized void begin(AgilityCourse selected, TileObject clicked, List<AgilityObstacleModel> obstacles,
        boolean canLootMarks, Set<WorldPoint> ignoredMarks)
    {
        cancel();
        int index = nextIndex(selected, obstacles, clicked.getId());
        if (index < 0) return;
        course = selected;
        targetId = obstacles.get(index).getObjectID();
        wraps = index == 0;
        origin = clicked.getWorldLocation();
        expires = System.nanoTime() + 15_000_000_000L;
        status = "Waiting " + targetId;
        requests++;
        reached = false;
        this.canLootMarks = canLootMarks;
        this.ignoredMarks = ignoredMarks;
    }

    synchronized void cancel()
    {
        // Do not touch the shared controller from the script thread. tick() releases it.
        targetId = -1;
        resetMotion = true;
        activeTargetKey = "";
        status = "Idle";
    }

    synchronized void tick(MicroAgilityConfig config, boolean running)
    {
        if (resetMotion)
        {
            controller.cancel();
            cachedTarget = null;
            lastScan = 0;
            resetMotion = false;
        }
        if (!running || !config.rooftopPrehover() || course != config.agilityCourse()
            || targetId < 0 || System.nanoTime() >= expires || !Microbot.isLoggedIn()
            || Microbot.pauseAllScripts.get() || Rs2AntibanSettings.microBreakActive
            || Rs2AntibanSettings.actionCooldownActive)
        {
            cancel();
            controller.cancel();
            return;
        }
        // Human input and UI actions suspend motion; resumption always starts a fresh trajectory.
        if (InputArbiter.isHuman() || Microbot.getClient().isMenuOpen()
            || Microbot.getClient().isWidgetSelected() || Microbot.getClient().getMouseCurrentButton() != 0)
        {
            controller.cancel();
            return;
        }
        WorldView view = Microbot.getClient().getTopLevelWorldView();
        if (view == null || Microbot.getClient().getLocalPlayer() == null)
        {
            cancel();
            controller.cancel();
            return;
        }
        WorldPoint player = Microbot.getClient().getLocalPlayer().getWorldLocation();
        if (player.distanceTo2D(origin) > 40 || (!wraps && origin.getPlane() > 0 && player.getPlane() == 0))
        {
            cancel();
            controller.cancel();
            return;
        }
        if (cachedTarget != null && !isInScene(cachedTarget, view)) cachedTarget = null;
        if (cachedTarget == null && System.nanoTime() - lastScan >= 100_000_000L)
        {
            cachedTarget = findTarget(view, player);
            lastScan = System.nanoTime();
        }
        TileObject target = cachedTarget;
        Rectangle viewport = new Rectangle(Microbot.getClient().getViewportXOffset(),
            Microbot.getClient().getViewportYOffset(), Microbot.getClient().getViewportWidth(),
            Microbot.getClient().getViewportHeight());
        Shape clickbox = target == null ? null : target.getClickbox();
        Point point = interiorPoint(clickbox, viewport);
        WorldPoint selectedMark = null;
        if (canLootMarks && target != null)
        {
            Tile mark = findUpcomingMark(view, player, target.getWorldLocation());
            if (mark != null)
            {
                Polygon markTile = Perspective.getCanvasTilePoly(Microbot.getClient(), mark.getLocalLocation());
                Point markPoint = interiorPoint(markTile, viewport);
                if (markPoint != null)
                {
                    clickbox = markTile;
                    point = markPoint;
                    selectedMark = mark.getWorldLocation();
                }
            }
        }
        if (point == null)
        {
            controller.cancel();
            status = "Waiting " + targetId;
            return;
        }
        String targetKey = selectedMark == null ? "obstacle#" + targetId : "mark@" + selectedMark;
        if (!targetKey.equals(activeTargetKey))
        {
            controller.cancel();
            activeTargetKey = targetKey;
            reached = false;
            status = "Waiting " + (selectedMark == null ? targetId : "mark");
        }
        final Point hoverPoint = point;
        controller.request(OWNER, 10, () -> hoverPoint);
        controller.tick();
        trackedTicks++;
        if (!status.startsWith("Tracking"))
            Microbot.log("Rooftop prehover: tracking " + (selectedMark == null ? "obstacle " + targetId : "mark " + selectedMark)
                + " (requests=" + requests + ", ticks=" + trackedTicks + ")");
        status = "Tracking " + (selectedMark == null ? targetId : "mark");
        net.runelite.api.Point cursor = Microbot.getClient().getMouseCanvasPosition();
        if (!reached && clickbox.contains(cursor.getX(), cursor.getY()))
        {
            reached = true;
            Microbot.log("Rooftop prehover: cursor inside live " + (selectedMark == null ? "clickbox " + targetId : "mark tile " + selectedMark));
        }
    }

    private Tile findUpcomingMark(WorldView view, WorldPoint player, WorldPoint next)
    {
        if (player.getPlane() != next.getPlane() || next.distanceTo2D(player) > 16) return null;
        Tile[][][] tiles = view.getScene().getTiles();
        int plane = next.getPlane();
        if (plane < 0 || plane >= tiles.length) return null;
        Tile[][] section = tiles[plane];
        Tile best = null;
        int bestDistance = Integer.MAX_VALUE;
        int nextX = next.getX() - view.getBaseX();
        int nextY = next.getY() - view.getBaseY();
        for (int x = Math.max(0, nextX - 6); x <= Math.min(section.length - 1, nextX + 6); x++)
            for (int y = Math.max(0, nextY - 6); y <= Math.min(section[x].length - 1, nextY + 6); y++)
            {
                Tile tile = section[x][y];
                if (tile == null || tile.getGroundItems() == null) continue;
                WorldPoint markLocation = tile.getWorldLocation();
                if (!isBeforeNextObstacle(origin, next, player, markLocation)
                    || ignoredMarks.contains(markLocation)) continue;
                for (TileItem item : tile.getGroundItems())
                {
                    if (item == null || item.getId() != ItemID.GRACE
                        || item.getOwnership() == TileItem.OWNERSHIP_OTHER) continue;
                    int distance = origin.distanceTo2D(markLocation);
                    if (distance < bestDistance) { best = tile; bestDistance = distance; }
                    break;
                }
            }
        return best;
    }

    /** A small corridor on the clicked-obstacle side of the next one. Distant and later-section marks lose priority. */
    static boolean isBeforeNextObstacle(WorldPoint clicked, WorldPoint next, WorldPoint player, WorldPoint mark)
    {
        if (clicked == null || next == null || player == null || mark == null
            || mark.getPlane() != next.getPlane() || player.getPlane() != next.getPlane()
            || player.distanceTo2D(mark) > 12 || next.distanceTo2D(mark) > 6) return false;
        long dx = next.getX() - clicked.getX(), dy = next.getY() - clicked.getY();
        long lengthSquared = dx * dx + dy * dy;
        if (lengthSquared < 4) return false;
        long mx = mark.getX() - clicked.getX(), my = mark.getY() - clicked.getY();
        long progress = mx * dx + my * dy;
        long lateral = mx * dy - my * dx;
        return progress * 5 >= lengthSquared && progress < lengthSquared
            && lateral * lateral <= 4 * lengthSquared;
    }

    private TileObject findTarget(WorldView view, WorldPoint player)
    {
        // Resolve against the live scene each frame, including other planes during rooftop transitions.
        // No reachability/pathfinder calls here: those can block the client thread.
        TileObject best = null;
        int distance = Integer.MAX_VALUE;
        Tile[][][] tiles = view.getScene().getTiles();
        for (Tile[][] plane : tiles)
        {
            int minX = Math.max(0, player.getX() - view.getBaseX() - 32);
            int maxX = Math.min(plane.length - 1, player.getX() - view.getBaseX() + 32);
            for (int x = minX; x <= maxX; x++)
            {
                int minY = Math.max(0, player.getY() - view.getBaseY() - 32);
                int maxY = Math.min(plane[x].length - 1, player.getY() - view.getBaseY() + 32);
                for (int y = minY; y <= maxY; y++)
                {
                    Tile tile = plane[x][y];
                    if (tile == null) continue;
                    TileObject[] fixed = {tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()};
                    for (TileObject object : fixed)
                    {
                        if (matches(object, view) && object.getWorldLocation().distanceTo2D(origin) < distance)
                        { best = object; distance = object.getWorldLocation().distanceTo2D(origin); }
                    }
                    for (TileObject object : tile.getGameObjects())
                    {
                        if (matches(object, view) && object.getWorldLocation().distanceTo2D(origin) < distance)
                        { best = object; distance = object.getWorldLocation().distanceTo2D(origin); }
                    }
                }
            }
        }
        return distance <= 40 ? best : null;
    }

    private boolean matches(TileObject object, WorldView view)
    {
        return object != null && object.getId() == targetId && object.getWorldView() == view
            && (wraps || object.getPlane() > 0);
    }

    private boolean isInScene(TileObject object, WorldView view)
    {
        if (!matches(object, view) || object.getLocalLocation() == null) return false;
        int x = object.getLocalLocation().getSceneX();
        int y = object.getLocalLocation().getSceneY();
        Tile[][][] tiles = view.getScene().getTiles();
        int plane = object.getPlane();
        if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length
            || y < 0 || y >= tiles[plane][x].length) return false;
        Tile tile = tiles[plane][x][y];
        if (tile == null) return false;
        if (tile.getWallObject() == object || tile.getDecorativeObject() == object || tile.getGroundObject() == object) return true;
        for (TileObject candidate : tile.getGameObjects()) if (candidate == object) return true;
        return false;
    }

    static int nextIndex(AgilityCourse course, List<AgilityObstacleModel> obstacles, int clickedId)
    {
        if (course == null || !course.isRooftopCourse() || obstacles.isEmpty()) return -1;
        int found = -1;
        for (int i = 0; i < obstacles.size(); i++)
        {
            if (obstacles.get(i).getObjectID() != clickedId) continue;
            if (found >= 0) return -1; // Ambiguous routes must not guess.
            found = i;
        }
        return found < 0 ? -1 : (found + 1) % obstacles.size();
    }

    static Point interiorPoint(Shape clickbox, Rectangle viewport)
    {
        if (clickbox == null) return null;
        Rectangle bounds = clickbox.getBounds().intersection(viewport);
        if (bounds.isEmpty()) return null;
        Point center = new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        if (clickbox.contains(center)) return center;
        for (int y = 1; y < 10; y++)
            for (int x = 1; x < 10; x++)
            {
                Point p = new Point(bounds.x + bounds.width * x / 10, bounds.y + bounds.height * y / 10);
                if (clickbox.contains(p)) return p;
            }
        return null;
    }

    String getStatus() { return status; }
}
