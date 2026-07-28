package net.runelite.client.plugins.microbot.pestcontrol;

import net.runelite.client.plugins.pestcontrol.Portal;

public final class PestControlCombatPlanTest {
    private PestControlCombatPlanTest() {
    }

    public static void main(String[] args) {
        defaultPlanUsesOnlyStyleOne();
        tribridPlanResolvesPortalLoadouts();
        meleeVariantsAreIndependent();
        openingModesExcludeRed();
        weightedOpeningUsesNormalizedBoundaries();
        missingMeleeVariantFallsBackWithinMelee();
        duplicateStylesAreIgnored();
        System.out.println("PestControlCombatPlanTest passed");
    }

    private static void defaultPlanUsesOnlyStyleOne() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new PestControlConfig() { });
        check(plan.enabledStyles().size() == 1, "default should enable only Style 1");
        check(plan.primaryLoadout().combatStyle == PestControlCombatStyle.RANGED,
                "default Style 1 should be ranged");
        check(plan.loadoutForPortal(Portal.RED).combatStyle == PestControlCombatStyle.RANGED,
                "disabled melee should fall back to Style 1");
    }

    private static void tribridPlanResolvesPortalLoadouts() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        check(plan.enabledStyles().size() == 3, "tribrid should enable three styles");
        check(plan.loadoutForPortal(Portal.PURPLE).combatStyle == PestControlCombatStyle.RANGED,
                "Purple should resolve ranged");
        check(plan.loadoutForPortal(Portal.BLUE).combatStyle == PestControlCombatStyle.MAGIC,
                "Blue should resolve magic");
        check(plan.loadoutForPortal(Portal.YELLOW).combatStyle == PestControlCombatStyle.MELEE,
                "Yellow should resolve melee");
    }

    private static void meleeVariantsAreIndependent() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        PestControlLoadout yellow = plan.loadoutForPortal(Portal.YELLOW);
        PestControlLoadout red = plan.loadoutForPortal(Portal.RED);
        check(yellow.meleeStyle == PestControlMeleeStyle.STAB,
                "Yellow should use configured stab variant");
        check("Abyssal dagger".equals(yellow.weapon), "Yellow stab weapon mismatch");
        check("Dragon defender".equals(yellow.offhand), "Yellow stab off-hand mismatch");
        check(red.meleeStyle == PestControlMeleeStyle.CRUSH,
                "Red should use configured crush variant");
        check("Saradomin godsword".equals(red.weapon), "Red crush weapon mismatch");
        check(red.requiresEmptyOffhand(), "two-handed crush loadout should require empty off-hand");
    }

    private static void openingModesExcludeRed() {
        PestControlCombatPlan mainMagic = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlCombatStyle primaryCombatStyle() {
                return PestControlCombatStyle.MAGIC;
            }

            @Override
            public PestControlOpeningMode openingMode() {
                return PestControlOpeningMode.MAIN_STYLE;
            }
        });
        check(mainMagic.openingPortal(1234) == Portal.BLUE,
                "main magic opening should choose Blue");

        PestControlCombatPlan even = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public PestControlOpeningMode openingMode() {
                return PestControlOpeningMode.EVEN_RANDOM;
            }
        });
        check(even.openingPortal(0) == Portal.PURPLE, "even roll 0 should be Purple");
        check(even.openingPortal(1) == Portal.BLUE, "even roll 1 should be Blue");
        check(even.openingPortal(2) == Portal.YELLOW, "even roll 2 should be Yellow");

        for (int roll = 0; roll < 300; roll++) {
            check(even.openingPortal(roll) != Portal.RED, "Red must never be an opening target");
        }
    }

    private static void duplicateStylesAreIgnored() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new PestControlConfig() {
            @Override
            public PestControlOptionalCombatStyle secondaryCombatStyle() {
                return PestControlOptionalCombatStyle.RANGED;
            }
        });
        check(plan.enabledStyles().size() == 1, "duplicate style should be ignored");
        check(!plan.validationMessages().isEmpty(), "duplicate style should produce a warning");
    }

    private static void weightedOpeningUsesNormalizedBoundaries() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig());
        check(plan.openingPortal(54) == Portal.PURPLE, "roll 54 should be Purple");
        check(plan.openingPortal(55) == Portal.BLUE, "roll 55 should be Blue");
        check(plan.openingPortal(77) == Portal.BLUE, "roll 77 should be Blue");
        check(plan.openingPortal(78) == Portal.YELLOW, "roll 78 should be Yellow");
        check(plan.openingPortal(99) == Portal.YELLOW, "roll 99 should be Yellow");
    }

    private static void missingMeleeVariantFallsBackWithinMelee() {
        PestControlCombatPlan plan = new PestControlCombatPlan(new TribridConfig() {
            @Override
            public String stabWeapon() {
                return "None";
            }
        });
        PestControlLoadout yellow = plan.loadoutForPortal(Portal.YELLOW);
        check(yellow.combatStyle == PestControlCombatStyle.MELEE,
                "missing stab should stay within enabled melee");
        check(yellow.meleeStyle == PestControlMeleeStyle.SLASH,
                "missing stab should fall back to the default slash variant");
        check("Dragon scimitar".equals(yellow.weapon),
                "missing stab should use the configured slash weapon");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class TribridConfig implements PestControlConfig {
        @Override
        public PestControlOptionalCombatStyle secondaryCombatStyle() {
            return PestControlOptionalCombatStyle.MAGIC;
        }

        @Override
        public PestControlOptionalCombatStyle tertiaryCombatStyle() {
            return PestControlOptionalCombatStyle.MELEE;
        }

        @Override
        public String rangedWeapon() {
            return "Magic Shortbow (i)";
        }

        @Override
        public String magicWeapon() {
            return "Trident of the swamp";
        }

        @Override
        public String magicOffhand() {
            return "Mage's book";
        }

        @Override
        public String stabWeapon() {
            return "Abyssal dagger";
        }

        @Override
        public String stabOffhand() {
            return "Dragon defender";
        }

        @Override
        public String crushWeapon() {
            return "Saradomin godsword";
        }

        @Override
        public PestControlMeleeStyle yellowMeleeStyle() {
            return PestControlMeleeStyle.STAB;
        }
    }
}
