package net.runelite.client.plugins.microbot.mahoganyhomez;

import net.runelite.api.gameval.ItemID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesSupplyPlanTest
{
	@Test void fullSackBalancesSeveralBarsAgainstLoosePlanksAndKeepsOneRouteSlot()
	{
		// Sack, hammer, saw and rune pouch already occupy four slots.
		MahoganyHomesSupplyPlan p = plan(28, 0, 0, 24, 0, 100, 100, 17, 1);
		assertEquals(3, p.barsTarget);
		assertEquals(20, p.looseTarget);
		assertTrue(p.minimumFits && p.minimumStock);
	}

	@Test void partialAndEmptySacksUseBoundedRepeatedFills()
	{
		assertEquals(18, PlankSack.fillWithdrawal(10, 0, 100));
		assertEquals(28, PlankSack.fillWithdrawal(0, 0, 100));
		assertEquals(0, PlankSack.fillWithdrawal(28, 0, 100));
		assertEquals(4, PlankSack.fillWithdrawal(10, 0, 4));
		assertEquals(10, PlankSack.fillWithdrawal(10, 8, 100));
		assertTrue(MahoganyHomesSupplyPlan.ready(17, 1, 7, 10, 1));
	}

	@Test void mixedAndWrongContentsDoNotCountTowardsTheTier()
	{
		assertTrue(PlankSack.hasWrongContents(28, 10));
		assertTrue(PlankSack.hasWrongContents(28, 0));
		assertFalse(PlankSack.hasWrongContents(28, 28));
		assertFalse(MahoganyHomesSupplyPlan.ready(17, 1, 0, 10, 1));
		assertEquals(3, PlankSack.available(3, 28, false));
	}

	@Test void notedAndWrongTierInventoryPlanksAreSanitized()
	{
		assertTrue(PlankSack.isWrongPlankId(ItemID.Cert.PLANK_TEAK, MahoganyHomesData.ADEPT));
		assertTrue(PlankSack.isWrongPlankId(ItemID.Cert.PLANK_OAK, MahoganyHomesData.ADEPT));
		assertTrue(PlankSack.isWrongPlankId(ItemID.PLANK_OAK, MahoganyHomesData.ADEPT));
		assertFalse(PlankSack.isWrongPlankId(ItemID.PLANK_TEAK, MahoganyHomesData.ADEPT));
		assertFalse(PlankSack.isWrongPlankId(ItemID.PLANK_SACK, MahoganyHomesData.ADEPT));
	}

	@Test void existingMultipleBarsAreRetainedWithinCapacity()
	{
		MahoganyHomesSupplyPlan p = plan(28, 10, 4, 10, 0, 100, 100, 17, 1);
		assertEquals(4, p.barsTarget);
		assertEquals(19, p.looseTarget);
	}

	@Test void crampedInventoryPreservesActualMinimumAndDropsOptionalSpareSlot()
	{
		MahoganyHomesSupplyPlan p = plan(10, 0, 0, 8, 0, 100, 100, 17, 1);
		assertEquals(1, p.barsTarget);
		assertEquals(7, p.looseTarget);
		assertTrue(p.minimumFits);
		assertTrue(MahoganyHomesSupplyPlan.ready(17, 1, p.looseTarget, 10, p.barsTarget));
	}

	@Test void missingToolsConsumeSpaceWhileEquippedToolsDoNot()
	{
		MahoganyHomesSupplyPlan equipped = plan(28, 0, 0, 25, 0, 100, 100, 17, 1);
		MahoganyHomesSupplyPlan missing = plan(28, 0, 0, 25, 2, 100, 100, 17, 1);
		assertEquals(equipped.looseTarget - 2, missing.looseTarget);
	}

	@Test void limitedStockLeavesWithViableContractInsteadOfDemandingBatch()
	{
		MahoganyHomesSupplyPlan p = plan(10, 0, 0, 24, 0, 7, 1, 17, 1);
		assertTrue(p.minimumStock);
		assertEquals(7, p.looseTarget);
		assertEquals(1, p.barsTarget);
		assertTrue(MahoganyHomesSupplyPlan.ready(17, 1, 7, 10, 1));
	}

	@Test void actualContractMinimumCanBeViableBelowDefaultRefillBudget()
	{
		MahoganyHomesSupplyPlan p = plan(4, 0, 0, 24, 0, 4, 0, 8, 0);
		assertTrue(p.minimumStock);
		assertTrue(MahoganyHomesSupplyPlan.ready(8, 0, 4, 4, 0));
		assertFalse(MahoganyHomesSupplyPlan.ready(17, 1, 4, 4, 0));
	}

	@Test void noSackKeepsSingleContractLoadout()
	{
		MahoganyHomesSupplyPlan p = MahoganyHomesSupplyPlan.plan(false, 0, 0, 0,
			26, 0, 100, 100, 17, 1, 17);
		assertEquals(17, p.looseTarget);
		assertEquals(1, p.barsTarget);
	}

	@Test void missingMinimumStockAndImpossibleCapacityAreReportedSeparately()
	{
		assertFalse(plan(0, 0, 0, 24, 0, 16, 0, 17, 1).minimumStock);
		assertFalse(plan(0, 0, 0, 8, 2, 100, 100, 17, 1).minimumFits);
	}

	@Test void carriedSuppliesNeedNotMatchPreferredBatch()
	{
		assertTrue(MahoganyHomesSupplyPlan.ready(17, 1, 0, 28, 1));
		assertTrue(MahoganyHomesSupplyPlan.ready(6, 0, 2, 4, 0));
	}

	@Test void viablePlansNeverExceedSpaceOrStockAcrossCrampedAndPartialLoads()
	{
		for (int stored : new int[]{0, 4, 14, 28})
		for (int loose : new int[]{0, 3, 12})
		for (int bars : new int[]{0, 1, 4})
		for (int free : new int[]{0, 2, 10})
		for (int bank : new int[]{0, 4, 100})
		{
			MahoganyHomesSupplyPlan p = plan(stored, loose, bars, free, 0, bank, 4, 17, 1);
			assertTrue(p.looseTarget + p.barsTarget <= free + loose + bars);
			assertTrue(p.looseTarget <= loose + bank);
			assertTrue(p.barsTarget <= bars + 4);
			if (p.minimumFits && p.minimumStock)
				assertTrue(MahoganyHomesSupplyPlan.ready(17, 1, p.looseTarget, stored, p.barsTarget));
		}
	}

	private static MahoganyHomesSupplyPlan plan(int stored, int loose, int bars, int free,
		int tools, int bankPlanks, int bankBars, int requiredPlanks, int requiredBars)
	{
		return MahoganyHomesSupplyPlan.plan(true, stored, loose, bars, free, tools,
			bankPlanks, bankBars, requiredPlanks, requiredBars, 17);
	}
}
