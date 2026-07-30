package net.runelite.client.plugins.microbot.giantsfoundry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
final class FoundryRewardShop
{
    private static final int SHOP_GROUP = 753;
    private static final int SHOP_ITEMS_CHILD = 22;
    private static final int SHOP_ITEMS_PARENT = (SHOP_GROUP << 16) | SHOP_ITEMS_CHILD;
    private static final int PURCHASE_IDENTIFIER = 2;
    private static final Map<String, Integer> ITEM_INDICES = createItemIndices();

    private FoundryRewardShop()
    {
    }

    static boolean isOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget items = Rs2Widget.getWidget(SHOP_GROUP, SHOP_ITEMS_CHILD);
            return items != null && !items.isHidden();
        }).orElse(false);
    }

    static boolean open()
    {
        if (isOpen())
        {
            return true;
        }
        log.info("Giants' Foundry shop: opening via Kovac");
        var kovac = Microbot.getRs2NpcCache().query().withName("kovac").nearestOnClientThread();
        if (kovac == null)
        {
            log.warn("Giants' Foundry shop: Kovac is not available");
            return false;
        }
        boolean opened = kovac.click("Shop");
        log.info("Giants' Foundry shop: interaction issued={}", opened);
        return opened;
    }

    static boolean purchase(FoundryShopPlanner.Purchase purchase)
    {
        Integer index = ITEM_INDICES.get(purchase.getName());
        if (index == null)
        {
            log.warn("Foundry shop item '{}' is not in the allowlisted shop layout", purchase.getName());
            return false;
        }

        String action = purchase.isMould() ? "Unlock" : "Buy 1";
        Microbot.doInvoke(new NewMenuEntry(
                        index,
                        SHOP_ITEMS_PARENT,
                        MenuAction.CC_OP.getId(),
                        PURCHASE_IDENTIFIER,
                        -1,
                        action),
                new Rectangle(1, 1));
        return true;
    }

    private static Map<String, Integer> createItemIndices()
    {
        Map<String, Integer> indices = new HashMap<>();
        indices.put("Smiths tunic", 52);
        indices.put("Smiths trousers", 65);
        indices.put("Smiths boots", 78);
        indices.put("Smiths gloves", 91);
        indices.put("Stiletto Forte", 117);
        indices.put("Defender Base", 130);
        indices.put("Juggernaut Forte", 143);
        indices.put("Chopper Forte +1", 156);
        indices.put("Spiker!", 169);
        indices.put("Flamberge Blade", 182);
        indices.put("Serpent Blade", 195);
        indices.put("Claymore Blade", 208);
        indices.put("Fleur de Blade", 221);
        indices.put("Choppa!", 234);
        indices.put("Defenders Tip", 260);
        indices.put("Serrated Tip", 273);
        indices.put("Needle Point", 286);
        indices.put("The Point!", 299);
        return Collections.unmodifiableMap(indices);
    }
}
