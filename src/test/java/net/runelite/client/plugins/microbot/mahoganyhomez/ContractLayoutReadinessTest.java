package net.runelite.client.plugins.microbot.mahoganyhomez;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.runelite.client.plugins.microbot.mahoganyhomez.ContractLayoutReadiness.Decision.*;

class ContractLayoutReadinessTest
{
	@Test void tauOutsideHandoffWaitsForHouseContextAndLayout()
	{
		ContractLayoutReadiness guard = new ContractLayoutReadiness();
		assertEquals(WAIT, guard.check(false, false, 100));
		assertEquals(WAIT, guard.check(false, true, 102));
		assertEquals(WAIT, guard.check(true, false, 104));
		assertEquals(READY, guard.check(true, true, 106));
		assertFalse(guard.pending());
	}

	@Test void anAlreadyLoadedHouseDoesNotDelayArrival()
	{
		assertEquals(READY, new ContractLayoutReadiness().check(true, true, 100));
	}

	@Test void unknownAndEmptyValuesAreNotValidLayouts()
	{
		assertFalse(MahoganyHomesScript.isLoadedContractLayout(new int[8]));
		assertFalse(MahoganyHomesScript.isLoadedContractLayout(new int[]{9, 3, 0, 3, 3, 3, 0, 0}));
		assertTrue(MahoganyHomesScript.isLoadedContractLayout(new int[]{1, 3, 0, 3, 3, 3, 0, 0}));
	}

	@Test void changingHouseReadinessDoesNotRenewTheDeadline()
	{
		ContractLayoutReadiness guard = new ContractLayoutReadiness();
		assertEquals(WAIT, guard.check(false, false, 100));
		assertEquals(WAIT, guard.check(true, false, 159));
		assertEquals(EXPIRED, guard.check(false, true, 160));
		guard.reset();
		assertEquals(WAIT, guard.check(true, false, 170));
	}
}
