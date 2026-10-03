package net.runelite.client.plugins.microbot.farmingcontract;

import net.runelite.client.plugins.timetracking.farming.Produce;

public class FarmingContractScriptTest {

    public static void main(String[] args) {
        resolvesCurrentProduceName();
        resolvesRemovedContractNameAliases();
        rejectsMissingContractNames();
    }

    private static void resolvesCurrentProduceName() {
        assertEquals(Produce.RANARR, FarmingContractScript.findProduceByContractName("Ranarr"));
    }

    private static void resolvesRemovedContractNameAliases() {
        assertEquals(Produce.POTATO, FarmingContractScript.findProduceByContractName("Potatoes"));
        assertEquals(Produce.MAGIC, FarmingContractScript.findProduceByContractName("Magic tree"));
        assertEquals(Produce.WHITEBERRIES, FarmingContractScript.findProduceByContractName("White berries"));
    }

    private static void rejectsMissingContractNames() {
        assertNull(FarmingContractScript.findProduceByContractName(null));
        assertNull(FarmingContractScript.findProduceByContractName("Not a crop"));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNull(Object actual) {
        if (actual != null) {
            throw new AssertionError("expected null, actual=" + actual);
        }
    }
}
