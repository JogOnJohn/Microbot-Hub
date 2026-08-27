package net.runelite.client.plugins.microbot.blackjack.entryswapper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BlackjackEntrySwapConfig.GROUP)
public interface BlackjackEntrySwapConfig extends Config
{
    String GROUP = "blackjackEntrySwap";

    @ConfigItem(
            keyName = "requirements",
            name = "Required pairing",
            description = "How this helper is used",
            position = 0
    )
    default String requirements()
    {
        return "Enable this plugin together with [JOJ] Blackjack. It makes Knock-Out the left-click " +
                "option while a supported target is standing or attacking, and Pickpocket while the target is unconscious.";
    }
}
