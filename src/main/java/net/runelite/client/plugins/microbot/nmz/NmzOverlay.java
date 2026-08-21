package net.runelite.client.plugins.microbot.nmz;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class NmzOverlay extends OverlayPanel {
    private final NmzConfig config;
    private final NmzScript script;
    private final PrayerPotionScript prayerScript;

    @Inject
    NmzOverlay(NmzPlugin plugin, NmzConfig config, NmzScript script, PrayerPotionScript prayerScript) {
        super(plugin);
        this.config = config;
        this.script = script;
        this.prayerScript = prayerScript;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(290, 540));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro NMZ V" + NmzPlugin.version)
                    .color(Color.GREEN)
                    .build());
            add("Profile", config.togglePrayerPotions() ? "Prayer" : "Overload/absorption");
            add("State", script.getOverlayState());
            add("Next", script.getOverlayNextAction());
            add("Last", script.getOverlayLastAction());
            add("State age", ageSeconds(script.getOverlayStateChangedAt()) + "s");
            add("Action gen", String.valueOf(script.getOverlayActionGeneration()));
            add("Rapid Heal", yesNo(config.randomlyTriggerRapidHeal()));
            add("Power-ups", powerUpConfig());
            add("Spec target", specTarget());
            add("Restore main", configuredOrAuto(config.mainWeapon()));
            add("Restore off-hand", configuredOrAuto(config.offhand()));

            WorldPoint location = Microbot.getClient().getLocalPlayer() == null
                    ? null : Microbot.getClient().getLocalPlayer().getWorldLocation();
            add("Location", location == null ? "Unknown" : location.getX() + "," + location.getY() + "," + location.getPlane());
            add("Area", location == null ? "Unknown" : script.isOutside() ? "NMZ lobby/outside" : "NMZ instance");
            add("Tab", tabName(Rs2Tab.getCurrentTab()));
            add("HP", skill(Skill.HITPOINTS));
            add("Prayer", skill(Skill.PRAYER));
            add("Absorption", String.valueOf(Microbot.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS)));
            add("Self-damage at", String.valueOf(NmzScript.maxHealth));
            add("Absorb target", String.valueOf(NmzScript.minAbsorption));

            add("Prayer worker", prayerScript.getOverlayState());
            add("Prayer potion", prayerScript.getOverlayPotion());
            add("Prayer gen", String.valueOf(prayerScript.getOverlayActionGeneration()));
            add("Prayer in flight", yesNo(prayerScript.isActionInFlight()));

            add("Power-up", script.getOverlayPowerUp());
            add("Special", script.getOverlaySpecial());
            add("Spec energy", (Rs2Combat.getSpecEnergy() / 10) + "%");
            add("Surge active", yesNo(NmzScript.isHasSurge()));

            add("Overload barrel", String.valueOf(Microbot.getVarbitValue(VarbitID.NZONE_POTION_3)));
            add("Absorb barrel", String.valueOf(Microbot.getVarbitValue(VarbitID.NZONE_POTION_4)));
        } catch (Exception ex) {
            add("Overlay error", ex.getClass().getSimpleName());
        }
        return super.render(graphics);
    }

    private void add(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right == null ? "" : right).build());
    }

    private String skill(Skill skill) {
        return Microbot.getClient().getBoostedSkillLevel(skill) + "/" + Microbot.getClient().getRealSkillLevel(skill);
    }

    private String tabName(InterfaceTab tab) {
        return tab == null ? "Unknown" : tab.getName();
    }

    private String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private String powerUpConfig() {
        return (config.useZapper() ? "Z" : "-")
                + (config.useReccurentDamage() ? "R" : "-")
                + (config.usePowerSurge() ? "P" : "-");
    }

    private String specTarget() {
        if (config.useAncientMace()) return "Ancient mace";
        if (!config.useSpecWeapon() || config.specWeapon() == null || config.specWeapon().trim().isEmpty()) return "Disabled";
        return config.specWeapon().trim();
    }

    private String configuredOrAuto(String value) {
        return value == null || value.trim().isEmpty() ? "Auto snapshot" : value.trim();
    }

    private long ageSeconds(long changedAt) {
        return Math.max(0, (System.currentTimeMillis() - changedAt) / 1000);
    }
}
