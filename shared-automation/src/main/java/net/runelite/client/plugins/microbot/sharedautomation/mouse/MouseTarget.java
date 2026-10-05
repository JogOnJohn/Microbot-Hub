package net.runelite.client.plugins.microbot.sharedautomation.mouse;

import java.awt.Point;

/** Supplies the target's current canvas point; return null when the target is not currently valid. */
@FunctionalInterface
public interface MouseTarget
{
	Point projectCurrentPoint();
}
