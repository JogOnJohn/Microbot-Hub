package net.runelite.client.plugins.microbot.mahoganyhomez;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesDialogueTest
{
	@Test
	void selectedContractContinueIsNotAnOptionFailure()
	{
		assertFalse(MahoganyHomesScript.contractOptionFailed(106, 100, true, true));
	}

	@Test
	void closedOptionsDuringTransitionAreNotAnOptionFailure()
	{
		assertFalse(MahoganyHomesScript.contractOptionFailed(106, 100, false, false));
	}

	@Test
	void genuinelyMissingOptionRetainsBoundedFailure()
	{
		assertFalse(MahoganyHomesScript.contractOptionFailed(105, 100, true, false));
		assertTrue(MahoganyHomesScript.contractOptionFailed(106, 100, true, false));
		assertFalse(MahoganyHomesScript.contractOptionFailed(106, -1, true, false));
	}
}
