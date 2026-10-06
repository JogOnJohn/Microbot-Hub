package net.runelite.client.plugins.microbot.agility;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.agility.models.AgilityObstacleModel;
import org.junit.jupiter.api.Test;
import net.runelite.client.plugins.microbot.sharedautomation.mouse.MouseIntentController;
import net.runelite.client.plugins.microbot.sharedautomation.mouse.MousePort;
import static org.junit.jupiter.api.Assertions.*;

class RooftopPrehoverTest
{
    @Test void sharedControllerTracksChangingProjectionAndReleasesOnInvalidTarget()
    {
        Point cursor = new Point(0, 0);
        Point[] target = {new Point(100, 100)};
        MouseIntentController controller = new MouseIntentController(new MousePort()
        {
            public Point getPosition() { return new Point(cursor); }
            public void moveTo(int x, int y) { cursor.setLocation(x, y); }
        });
        controller.request("agility", 10, () -> target[0]);
        for (int i = 0; i < 100; i++) controller.tick(1_000_000_000L + i * 20_000_000L);
        assertTrue(cursor.distance(target[0]) <= 2);
        target[0] = new Point(200, 50);
        for (int i = 100; i < 200; i++) controller.tick(1_000_000_000L + i * 20_000_000L);
        assertTrue(cursor.distance(target[0]) <= 2);
        target[0] = null;
        assertFalse(controller.tick(5_000_000_000L));
        assertFalse(controller.isActive());
    }

    @Test void advancesFromActualClickAndWraps()
    {
        var route = Arrays.asList(new AgilityObstacleModel(10), new AgilityObstacleModel(20));
        assertEquals(1, RooftopPrehover.nextIndex(AgilityCourse.ARDOUGNE_ROOFTOP_COURSE, route, 10));
        assertEquals(0, RooftopPrehover.nextIndex(AgilityCourse.ARDOUGNE_ROOFTOP_COURSE, route, 20));
        assertEquals(-1, RooftopPrehover.nextIndex(AgilityCourse.ARDOUGNE_ROOFTOP_COURSE, route, 30));
        assertEquals(-1, RooftopPrehover.nextIndex(AgilityCourse.AGILITY_PYRAMID, route, 10));
        assertEquals(-1, RooftopPrehover.nextIndex(null, route, 10));
        assertEquals(-1, RooftopPrehover.nextIndex(AgilityCourse.ARDOUGNE_ROOFTOP_COURSE, Collections.emptyList(), 10));
    }

    @Test void rejectsAmbiguousRoutes()
    {
        assertEquals(-1, RooftopPrehover.nextIndex(AgilityCourse.ARDOUGNE_ROOFTOP_COURSE,
            Arrays.asList(new AgilityObstacleModel(10), new AgilityObstacleModel(10)), 10));
    }

    @Test void allRooftopRoutesHaveUnambiguousSuccessors()
    {
        for (AgilityCourse course : AgilityCourse.values())
        {
            if (!course.isRooftopCourse()) continue;
            var route = course.getHandler().getObstacles();
            for (int i = 0; i < route.size(); i++)
                assertEquals((i + 1) % route.size(), RooftopPrehover.nextIndex(course, route, route.get(i).getObjectID()), course.name());
        }
    }

    @Test void pointMustBeInsideActualClickboxAndViewport()
    {
        Polygon triangle = new Polygon(new int[]{0, 100, 0}, new int[]{0, 0, 100}, 3);
        Rectangle viewport = new Rectangle(5, 5, 90, 90);
        Point point = RooftopPrehover.interiorPoint(triangle, viewport);
        assertNotNull(point);
        assertTrue(triangle.contains(point));
        assertTrue(viewport.contains(point));
        assertNull(RooftopPrehover.interiorPoint(null, viewport));
        assertNull(RooftopPrehover.interiorPoint(triangle, new Rectangle(200, 200, 10, 10)));
        assertNull(RooftopPrehover.interiorPoint(new Rectangle(0, 0, 0, 0), viewport));
    }
}
