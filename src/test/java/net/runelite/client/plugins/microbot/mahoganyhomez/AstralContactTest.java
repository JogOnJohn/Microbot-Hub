package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.awt.Rectangle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AstralContactTest
{
	@Test void modeIsOptInAndTierRemainsConfigurable()
	{
		MahoganyHomesConfig config = new MahoganyHomesConfig() {
			@Override public MahoganyHomesData contractTier() { return MahoganyHomesData.ADEPT; }
		};
		assertFalse(config.useAstralContact());
		assertEquals("Adept Contract", config.contractTier().getContractOption());
	}

	@Test void amyIsFoundInColoredLiveActionsAndRandomContactIsRejected()
	{
		assertTrue(AstralContact.hasAmyAction(new String[]{"Contact <col=ff9040>Amy</col>", "Adept Contract"}));
		assertTrue(AstralContact.hasAmyAction(new String[]{null, "Contact Amy"}));
		assertFalse(AstralContact.hasAmyAction(new String[]{"Contact Random"}));
		assertFalse(AstralContact.hasAmyAction(new String[]{"Contact Amy's assistant"}));
		assertFalse(AstralContact.hasAmyAction(null));
	}

	@Test void pickerScrollsDownUntilTheEntireAmyEntryIsVisible()
	{
		Rectangle viewport = new Rectangle(469, 217, 467, 287);
		assertEquals(1, AstralContact.scrollDirection(viewport, new Rectangle(469, 600, 65, 125)));
		assertEquals(1, AstralContact.scrollDirection(viewport, new Rectangle(469, 450, 65, 125)));
		assertEquals(0, AstralContact.scrollDirection(viewport, new Rectangle(469, 379, 65, 125)));
	}

	@Test void pickerCanRecoverWhenAmyHasBeenScrolledAboveTheViewport()
	{
		assertEquals(-1, AstralContact.scrollDirection(new Rectangle(469, 217, 467, 287),
			new Rectangle(469, 100, 65, 125)));
	}

	@Test void announcementSurvivesQolSkippingTheDialogueAndContainsTheActualTier()
	{
		MahoganyHomesScript.Assignment a = MahoganyHomesScript.parseAssignment(
			"<col=ff0000>Adept Contract</col>: Go see <col=ff0000>Bob </col>in north-east Varrock, opposite the church.",
			MahoganyHomesData.EXPERT);
		assertNotNull(a);
		assertEquals("Bob", a.homeowner);
		assertEquals(MahoganyHomesData.ADEPT, a.tier);
	}

	@Test void amyDialogUsesConfiguredTierWhenTheAnnouncementHasNotArrived()
	{
		MahoganyHomesScript.Assignment a = MahoganyHomesScript.parseAssignment(
			"Amy|Please could you go see <col=ff0000>Bob </col>in north-east Varrock, opposite the church for us? "
				+ "You can get another job once you have furnished his home.", MahoganyHomesData.ADEPT);
		assertNotNull(a);
		assertEquals("Bob", a.homeowner);
		assertEquals(MahoganyHomesData.ADEPT, a.tier);
	}

	@Test void unrelatedDialogueCannotBecomeAnAssignment()
	{
		assertNull(MahoganyHomesScript.parseAssignment("Amy|What kind of contract would you like?", MahoganyHomesData.ADEPT));
		assertNull(MahoganyHomesScript.parseAssignment("Grand Exchange: Bought 100 planks", MahoganyHomesData.ADEPT));
	}
}
