# Pest Control Script – Microbot RuneLite Client

![img.png](assets/img.png)

The **Pest Control Script** automates the Old School RuneScape Pest Control mini-game using the Microbot RuneLite client.  
It handles portal and NPC combat while preserving a simple user-supplied loadout.

---

## Features

## Feature Overview

| Feature                   | Description                                                                 |
|---------------------------|-----------------------------------------------------------------------------|
| **Auto World Hop**        | Hops to your configured Pest Control world before starting.                 |
| **Travel to Island**      | Walks to the Pest Control island if not already there.                      |
| **Boat Selection**        | Uses novice below 70, intermediate at 70-99, and veteran at 100+.           |
| **Quick Prayer**          | Automatically enables Quick Prayer at game start (optional).                |
| **Special Attack**        | Uses special attack when above configured energy threshold.                  |
| **Target Priority**       | Customizable attack order: Brawlers, Portals, Spinners.                      |
| **Portal Zerg Targeting** | Joins the largest group at a live portal, stays through small crowd changes, and uses purple as a tie-break. |
| **Weapon and Style Switching** | Uses per-weakness weapon slots, selects the matching attack option, then restores the primary. |
| **Combat Idle Handling**  | Attacks nearby NPCs when idle.                                               |
| **Brawler Blocking Fix**  | Attacks brawlers if they block movement.                                     |
| **Boat Alching**          | High-alchs a chosen item while waiting in the boat (optional).               |
| **Error Handling**        | Catches exceptions and prevents script crashes.                              |
| **Fast Loop**             | Runs every 300 ms for near real-time responses.                              |
| **Priority Requeue**      | Clicks the correct gangplank before post-round weapon restoration or cleanup. |


---

## Requirements
- Microbot RuneLite client
- A primary weapon equipped and every configured non-None portal weapon in inventory
- Pest Control world access

---

## Configuration Options
- **World**: Target world to play on.
- **Quick Prayer**: Enable/disable Quick Prayer usage.
- **Special Attack Percentage**: Energy threshold for using special attacks.
- **Target Priority**: Set the attack order for Brawlers, Portals, and Spinners.
- **Alching in Boat**: Enable/disable high-alching between matches.
- **Alch Item**: Name of item to alch.
- **Primary Combat Style**: The style used whenever a portal weapon is `None` (preloaded as Ranged).
- **Ranged Weapon (Purple)**: Preloaded as `Adamant crossbow`; it is also the primary restore target while the primary style is Ranged and is kept on Rapid.
- **Magic Weapon (Blue)**: Preloaded as `None`, so the primary ranged weapon is retained.
- **Slash/Stab Weapon (Yellow)**: Preloaded as `Dragon scimitar`. The script prefers its Slash option and falls back to a Stab option when necessary.
- **Crush Weapon (Red)**: Preloaded as `None`, so the primary ranged weapon is retained.

`None` (or a blank value) means to restore and use the captured primary weapon. A configured magic weapon preserves its existing spell/autocast setup; spell selection is not changed.

---

## How It Works
1. The script checks if you are logged in and on the right world.
2. If needed, it hops worlds and travels to the Pest Control island.
3. Captures the equipped primary weapon and boards the correct boat for your combat level.
4. During games:
    - Moves to the center.
    - Activates prayers and special attacks as configured.
    - Follows the largest live-portal group, kills nearby Spinners, then focuses the portal.
    - Keeps ranged on Rapid and selects Slash/Stab or Crush when using those configured portal weapons.
5. After games, it immediately reboards, then restores the primary weapon once the boat is confirmed.

---

## Disclaimer
This script is intended for use within the **Microbot RuneLite Client** only.  
Use of automation software in Old School RuneScape is against Jagex’s rules and can result in penalties to your account.  
Use at your own risk.

