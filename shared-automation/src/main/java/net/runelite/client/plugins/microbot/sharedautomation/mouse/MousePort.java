package net.runelite.client.plugins.microbot.sharedautomation.mouse;

import java.awt.Point;

/** Narrow adapter around the mouse implementation used by the host plugin. */
public interface MousePort
{
	Point getPosition();

	void moveTo(int x, int y);
}
