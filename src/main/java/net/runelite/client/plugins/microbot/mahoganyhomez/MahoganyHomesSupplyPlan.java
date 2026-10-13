package net.runelite.client.plugins.microbot.mahoganyhomez;

/** Banking preferences are independent of the minimum needed to leave the bank. */
final class MahoganyHomesSupplyPlan
{
	final int looseTarget;
	final int barsTarget;
	final boolean minimumStock;
	final boolean minimumFits;

	private MahoganyHomesSupplyPlan(int looseTarget, int barsTarget, boolean stock, boolean fits)
	{
		this.looseTarget = looseTarget;
		this.barsTarget = barsTarget;
		minimumStock = stock;
		minimumFits = fits;
	}

	static boolean ready(int requiredPlanks, int requiredBars, int loose, int stored, int bars)
	{
		return loose + stored >= requiredPlanks && bars >= requiredBars;
	}

	static MahoganyHomesSupplyPlan plan(boolean sack, int stored, int loose, int bars,
		int freeSlots, int missingToolSlots, int bankPlanks, int bankBars,
		int requiredPlanks, int requiredBars, int contractPlankBudget)
	{
		// freeSlots already excludes the sack, carried tools and every retained travel item.
		int slots = Math.max(0, freeSlots + loose + bars - missingToolSlots);
		int minimumLoose = Math.max(0, requiredPlanks - stored);
		boolean fits = minimumLoose + requiredBars <= slots;
		boolean stock = loose + stored + bankPlanks >= requiredPlanks && bars + bankBars >= requiredBars;
		// Prefer one spare slot for route supplies, but never sacrifice a viable contract for it.
		int usable = sack && slots > minimumLoose + requiredBars ? slots - 1 : slots;
		int availablePlanks = Math.max(0, loose + bankPlanks);
		int potentialPlanks = stored + Math.min(availablePlanks, Math.max(0, usable - requiredBars));
		int batchBars = sack ? (potentialPlanks + contractPlankBudget - 1) / contractPlankBudget : requiredBars;
		int barLimit = Math.max(0, usable - minimumLoose);
		int barTarget = Math.min(Math.min(Math.max(requiredBars, Math.max(bars, batchBars)),
			Math.max(0, bars + bankBars)), barLimit);
		int plankPreference = sack ? Math.max(0, usable - barTarget)
			: Math.max(requiredPlanks, contractPlankBudget);
		int looseTarget = Math.min(availablePlanks, Math.min(plankPreference, Math.max(0, usable - barTarget)));
		return new MahoganyHomesSupplyPlan(looseTarget, barTarget, stock, fits);
	}
}
