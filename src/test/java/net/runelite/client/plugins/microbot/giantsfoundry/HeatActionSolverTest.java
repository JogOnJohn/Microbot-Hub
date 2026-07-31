package net.runelite.client.plugins.microbot.giantsfoundry;

import net.runelite.client.plugins.microbot.giantsfoundry.enums.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatActionSolverTest
{
    @Test
    void remainsBoundedAcrossHeatAndStageInputs()
    {
        for (Stage stage : Stage.values())
        {
            int[] range = rangeFor(stage);
            for (int start = 0; start <= 1000; start += 25)
            {
                for (int actionsLeft = 1; actionsLeft <= 30; actionsLeft++)
                {
                    for (boolean fast : new boolean[]{false, true})
                    {
                        for (boolean heating : new boolean[]{false, true})
                        {
                            for (boolean running : new boolean[]{false, true})
                            {
                                HeatActionSolver.DurationResult result =
                                        HeatActionSolver.solve(stage, range, actionsLeft, start, fast, heating, 3, running);
                                assertTrue(result.getDuration() >= 0);
                                assertTrue(result.getDuration() <= HeatActionSolver.MAX_INDEX);
                                assertTrue(result.getPredictedHeat() >= 0 && result.getPredictedHeat() <= 1000,
                                        stage + " predicted " + result.getPredictedHeat() + " from " + start);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void reservesInFlightMomentumBelowTheBandCeiling()
    {
        int[] range = {755, 910};
        int paddedMax = 916;
        for (int start = 400; start <= 740; start += 20)
        {
            HeatActionSolver.DurationResult result =
                    HeatActionSolver.solve(
                            Stage.TRIP_HAMMER, range, 10, start, false, true, 3, true);
            int nextTick = HeatActionSolver.DX_1[
                    Math.min(result.getDuration(), HeatActionSolver.MAX_INDEX)];
            assertTrue(result.getPredictedHeat() + nextTick <= paddedMax,
                    "start " + start + " predicted " + result.getPredictedHeat()
                            + " + nextTick " + nextTick + " exceeds " + paddedMax);
        }
    }

    private static int[] rangeFor(Stage stage)
    {
        switch (stage)
        {
            case POLISHING_WHEEL:
                return new int[]{90, 245};
            case GRINDSTONE:
                return new int[]{420, 580};
            case TRIP_HAMMER:
                return new int[]{755, 910};
            default:
                throw new IllegalArgumentException("Unknown stage " + stage);
        }
    }
}
