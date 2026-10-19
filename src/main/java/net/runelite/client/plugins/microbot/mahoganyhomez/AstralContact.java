package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.util.Text;

/** Lunar contact selection; all mouse targets come from the current widget geometry. */
final class AstralContact
{
	static final int INTERFACE = 75;
	static final String AMY_ACTION = "Contact Amy";
	enum Selection { CLOSED, MISSING, SCROLLED, CONTACTED }

	static String missingRequirement()
	{
		if (!Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR)) return "Astral Contact requires the Lunar spellbook.";
		if (!Rs2Spells.NPC_CONTACT.hasRequiredLevel()) return "Astral Contact requires 67 Magic.";
		if (!Rs2Magic.hasRequiredRunes(Rs2Spells.NPC_CONTACT))
			return "Astral Contact needs air, astral and cosmic runes in your inventory or rune pouch.";
		return null;
	}

	static boolean pickerOpen()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget list = Microbot.getClient().getWidget(INTERFACE, 4);
			return list != null && !list.isHidden();
		}).orElse(false);
	}

	static Selection selectAmy()
	{
		Click click = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget list = Microbot.getClient().getWidget(INTERFACE, 4);
			if (list == null || list.isHidden()) return null;
			Widget amy = Stream.of(list.getStaticChildren(), list.getDynamicChildren(), list.getNestedChildren())
				.filter(Objects::nonNull).flatMap(Arrays::stream).filter(Objects::nonNull)
				.filter(w -> hasAmyAction(w.getActions())).findFirst().orElse(null);
			if (amy == null || list.getParent() == null) return new Click(null, null, -1, -1);
			return new Click(list.getParent().getBounds(), amy.getBounds(), amy.getId(), amy.getIndex());
		}).orElse(null);
		if (click == null) return Selection.CLOSED;
		if (click.viewport == null || click.target == null || click.viewport.isEmpty() || click.target.isEmpty())
			return Selection.MISSING;
		int direction = scrollDirection(click.viewport, click.target);
		if (direction != 0)
		{
			Point point = new Point((int) click.viewport.getCenterX(), (int) click.viewport.getCenterY());
			if (direction > 0) Microbot.getMouse().scrollDown(point);
			else Microbot.getMouse().scrollUp(point);
			return Selection.SCROLLED;
		}
		Microbot.doInvoke(new NewMenuEntry().option(AMY_ACTION).target("").identifier(1)
			.opcode(MenuAction.CC_OP.getId()).param0(click.index).param1(click.id).itemId(-1), click.target);
		return Selection.CONTACTED;
	}

	static boolean hasAmyAction(String[] actions)
	{
		return actions != null && Arrays.stream(actions).filter(Objects::nonNull)
			.map(Text::removeTags).anyMatch(AMY_ACTION::equals);
	}

	static int scrollDirection(Rectangle viewport, Rectangle target)
	{
		if (viewport.contains(target)) return 0;
		return target.y < viewport.y ? -1 : 1;
	}

	private static final class Click
	{
		final Rectangle viewport, target;
		final int id, index;
		Click(Rectangle viewport, Rectangle target, int id, int index)
		{
			this.viewport = viewport;
			this.target = target;
			this.id = id;
			this.index = index;
		}
	}
}
