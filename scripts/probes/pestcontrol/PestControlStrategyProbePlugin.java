package pestcontrolprobe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/**
 * Read-only live telemetry for Pest Control strategy development.
 *
 * <p>The probe deliberately records no player names and performs no game
 * interactions. Coordinates are reduced to region-local values so observations
 * remain useful across Pest Control instance templates.</p>
 */
@PluginDescriptor(
    name = "Pest Control Strategy Probe",
    description = "Read-only crowd, lane, and pest telemetry for Pest Control",
    enabledByDefault = false
)
public class PestControlStrategyProbePlugin extends Plugin
{
    private static final String RESULT_KEY = "pestcontrol-strategy-probe";
    private static final int SAMPLE_EVERY_TICKS = 8;

    private static final Zone PURPLE = new Zone("purple", 8, 30);
    private static final Zone BLUE = new Zone("blue", 55, 29);
    private static final Zone YELLOW = new Zone("yellow", 48, 13);
    private static final Zone RED = new Zone("red", 22, 12);
    private static final List<Zone> PORTALS = List.of(PURPLE, BLUE, YELLOW, RED);

    private static final List<Zone> GATES = List.of(
        new Zone("west", 19, 32),
        new Zone("south", 32, 24),
        new Zone("east", 47, 32)
    );

    private static final Set<Integer> OPEN_GATE_IDS = Set.of(
        14234, 14236, 14238, 14240, 14242, 14244, 14246, 14248
    );
    private static final Set<Integer> CLOSED_GATE_IDS = Set.of(
        14233, 14235, 14237, 14239, 14241, 14243, 14245, 14247
    );
    private static final Set<Integer> FIXED_BARRICADE_IDS = Set.of(14224, 14225, 14226);
    private static final Set<Integer> DAMAGED_BARRICADE_IDS = Set.of(14227, 14228, 14229);
    private static final Set<Integer> DESTROYED_BARRICADE_IDS = Set.of(14230, 14231, 14232);
    private static final Set<Integer> VOID_KNIGHT_GLOW_IDS = Set.of(14310, 14311, 14312, 14313);

    @Inject
    private Client client;

    private int ticks;

    @Override
    protected void startUp()
    {
        ticks = 0;
        ScriptResultStore.clear(RESULT_KEY);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (++ticks % SAMPLE_EVERY_TICKS != 0
            || client.getGameState() != GameState.LOGGED_IN
            || client.getLocalPlayer() == null
            || client.getWidget(WidgetInfo.PEST_CONTROL_BLUE_SHIELD) == null)
        {
            return;
        }

        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return;
        }

        List<Player> players = new ArrayList<>();
        worldView.players().stream().filter(p -> p != null).forEach(players::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playerCount", players.size());
        result.put("portalCrowd", countPlayersNearZones(players, PORTALS, 12));
        result.put("portalLane", assignPlayersToNearestPortal(players, 22));
        result.put("gateCrowd", countPlayersNearZones(players, GATES, 5));
        result.put("status", readStatusWidgets());
        result.put("structures", summariseStructures());
        result.put("playerHeatmap", buildHeatmap(players));
        result.put("npcs", summariseNpcs(worldView));

        WorldPoint local = client.getLocalPlayer().getWorldLocation();
        result.put("localPlayerRegion", point(local));
        ScriptResultStore.submit(RESULT_KEY, result);
    }

    private static Map<String, Integer> countPlayersNearZones(
        List<Player> players,
        List<Zone> zones,
        int radius)
    {
        Map<String, Integer> counts = zeroCounts(zones);
        for (Player player : players)
        {
            WorldPoint location = player.getWorldLocation();
            if (location == null)
            {
                continue;
            }
            int x = location.getRegionX();
            int y = location.getRegionY();
            for (Zone zone : zones)
            {
                if (zone.distanceTo(x, y) <= radius)
                {
                    counts.compute(zone.name, (ignored, count) -> count + 1);
                }
            }
        }
        return counts;
    }

    private static Map<String, Integer> assignPlayersToNearestPortal(
        List<Player> players,
        int maximumDistance)
    {
        Map<String, Integer> counts = zeroCounts(PORTALS);
        counts.put("unassigned", 0);
        for (Player player : players)
        {
            WorldPoint location = player.getWorldLocation();
            if (location == null)
            {
                continue;
            }
            int x = location.getRegionX();
            int y = location.getRegionY();
            Zone nearest = PORTALS.stream()
                .min(Comparator.comparingInt(zone -> zone.distanceTo(x, y)))
                .orElse(null);
            if (nearest == null || nearest.distanceTo(x, y) > maximumDistance)
            {
                counts.compute("unassigned", (ignored, count) -> count + 1);
            }
            else
            {
                counts.compute(nearest.name, (ignored, count) -> count + 1);
            }
        }
        return counts;
    }

    private static Map<String, Integer> buildHeatmap(List<Player> players)
    {
        Map<String, Integer> heatmap = new LinkedHashMap<>();
        for (Player player : players)
        {
            WorldPoint location = player.getWorldLocation();
            if (location == null)
            {
                continue;
            }
            int bucketX = (location.getRegionX() / 4) * 4;
            int bucketY = (location.getRegionY() / 4) * 4;
            String bucket = bucketX + "," + bucketY;
            heatmap.merge(bucket, 1, Integer::sum);
        }
        return heatmap;
    }

    private static Map<String, Object> summariseNpcs(WorldView worldView)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Map<String, Object>> important = new ArrayList<>();
        worldView.npcs().stream().filter(npc -> npc != null).forEach(npc -> {
            String name = npc.getName();
            if (name == null)
            {
                return;
            }
            String normalized = name.toLowerCase();
            if (isPest(normalized) || isVoidKnight(npc) || isPortal(npc))
            {
                counts.merge(name, 1, Integer::sum);
            }
            if (isVoidKnight(npc) || isPortal(npc))
            {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", npc.getId());
                entry.put("name", name);
                entry.put("region", point(npc.getWorldLocation()));
                entry.put("healthRatio", npc.getHealthRatio());
                entry.put("healthScale", npc.getHealthScale());
                if (isPortal(npc))
                {
                    entry.put("portal", portalName(npc.getId()));
                    entry.put("shielded", isShieldedPortal(npc.getId()));
                }
                important.add(entry);
            }
        });

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("counts", counts);
        summary.put("important", important);
        return summary;
    }

    private static Map<String, Object> summariseStructures()
    {
        List<Rs2TileObjectModel> structures = Microbot.getRs2TileObjectCache().query()
            .where(object -> OPEN_GATE_IDS.contains(object.getId())
                || CLOSED_GATE_IDS.contains(object.getId())
                || FIXED_BARRICADE_IDS.contains(object.getId())
                || DAMAGED_BARRICADE_IDS.contains(object.getId())
                || DESTROYED_BARRICADE_IDS.contains(object.getId())
                || VOID_KNIGHT_GLOW_IDS.contains(object.getId()))
            .toList();

        Map<String, String> gates = new LinkedHashMap<>();
        for (Zone gate : GATES)
        {
            gates.put(gate.name, "missing");
        }

        Map<String, Integer> barricades = new LinkedHashMap<>();
        barricades.put("fixed", 0);
        barricades.put("damaged", 0);
        barricades.put("destroyed", 0);
        Map<String, Integer> voidKnight = Map.of();

        for (Rs2TileObjectModel object : structures)
        {
            WorldPoint location = object.getWorldLocation();
            if (location == null)
            {
                continue;
            }
            if (OPEN_GATE_IDS.contains(object.getId()) || CLOSED_GATE_IDS.contains(object.getId()))
            {
                int x = location.getRegionX();
                int y = location.getRegionY();
                Zone gate = GATES.stream()
                    .min(Comparator.comparingInt(zone -> zone.distanceTo(x, y)))
                    .orElse(null);
                if (gate != null && gate.distanceTo(x, y) <= 2)
                {
                    gates.put(gate.name, OPEN_GATE_IDS.contains(object.getId()) ? "open" : "closed");
                }
            }
            else if (FIXED_BARRICADE_IDS.contains(object.getId()))
            {
                barricades.compute("fixed", (ignored, count) -> count + 1);
            }
            else if (DAMAGED_BARRICADE_IDS.contains(object.getId()))
            {
                barricades.compute("damaged", (ignored, count) -> count + 1);
            }
            else if (DESTROYED_BARRICADE_IDS.contains(object.getId()))
            {
                barricades.compute("destroyed", (ignored, count) -> count + 1);
            }
            else if (VOID_KNIGHT_GLOW_IDS.contains(object.getId()))
            {
                voidKnight = point(location);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("gates", gates);
        summary.put("barricades", barricades);
        summary.put("voidKnight", voidKnight);
        return summary;
    }

    private Map<String, Object> readStatusWidgets()
    {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("time", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_OVER_TIME));
        status.put("voidKnightHealth", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_OVER_HEALTH));
        status.put("damage", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_OVER_DAM));
        status.put("portalHealth", Map.of(
            "purple", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT1),
            "blue", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT2),
            "yellow", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT3),
            "red", widgetText(InterfaceID.PestStatusOverlay.PEST_STATUS_PORTTXT4)
        ));

        Widget container = client.getWidget(InterfaceID.PestStatusOverlay.ACTIVITY_CONTAINER);
        Widget progress = client.getWidget(InterfaceID.PestStatusOverlay.ACTIVITY_BAR);
        if (container != null && progress != null
            && container.getChild(0) != null && progress.getChild(0) != null
            && container.getChild(0).getWidth() > 0)
        {
            int percentage = (int) Math.round(
                100.0 * progress.getChild(0).getWidth() / container.getChild(0).getWidth());
            status.put("activityPercent", percentage);
        }
        return status;
    }

    private String widgetText(int packedId)
    {
        Widget widget = client.getWidget(packedId);
        return widget == null ? "" : widget.getText();
    }

    private static boolean isPortal(NPC npc)
    {
        int id = npc.getId();
        return id >= 1739 && id <= 1754;
    }

    private static boolean isShieldedPortal(int id)
    {
        return (id >= 1743 && id <= 1746) || (id >= 1751 && id <= 1754);
    }

    private static String portalName(int id)
    {
        int normalized = id;
        if (id >= 1743 && id <= 1746)
        {
            normalized -= 4;
        }
        else if (id >= 1747 && id <= 1750)
        {
            normalized -= 8;
        }
        else if (id >= 1751 && id <= 1754)
        {
            normalized -= 12;
        }
        switch (normalized)
        {
            case 1739:
                return "purple";
            case 1740:
                return "blue";
            case 1741:
                return "yellow";
            case 1742:
                return "red";
            default:
                return "unknown";
        }
    }

    private static boolean isVoidKnight(NPC npc)
    {
        return npc.getId() >= 1755 && npc.getId() <= 1758;
    }

    private static boolean isPest(String name)
    {
        return name.equals("brawler")
            || name.equals("defiler")
            || name.equals("ravager")
            || name.equals("shifter")
            || name.equals("spinner")
            || name.equals("splatter")
            || name.equals("torcher");
    }

    private static Map<String, Integer> zeroCounts(List<Zone> zones)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Zone zone : zones)
        {
            counts.put(zone.name, 0);
        }
        return counts;
    }

    private static Map<String, Integer> point(WorldPoint point)
    {
        if (point == null)
        {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("x", point.getRegionX());
        result.put("y", point.getRegionY());
        result.put("plane", point.getPlane());
        return result;
    }

    private static final class Zone
    {
        private final String name;
        private final int x;
        private final int y;

        private Zone(String name, int x, int y)
        {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        private int distanceTo(int otherX, int otherY)
        {
            return Math.max(Math.abs(x - otherX), Math.abs(y - otherY));
        }
    }
}
