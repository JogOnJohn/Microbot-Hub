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
| **Boat Selection**        | Boards the correct boat based on your combat level.                         |
| **Quick Prayer**          | Automatically enables Quick Prayer at game start (optional).                |
| **Special Attack**        | Uses special attack when above configured energy threshold.                  |
| **Target Priority**       | Customizable attack order: Brawlers, Portals, Spinners.                      |
| **Adaptive Portal Targeting** | Selects the least-covered live portal and uses purple as a tie-break.     |
| **Weapon Switching**      | Captures the equipped primary weapon, uses a configured weakness switch, then restores the primary. |
| **Combat Idle Handling**  | Attacks nearby NPCs when idle.                                               |
| **Brawler Blocking Fix**  | Attacks brawlers if they block movement.                                     |
| **Boat Alching**          | High-alchs a chosen item while waiting in the boat (optional).               |
| **Error Handling**        | Catches exceptions and prevents script crashes.                              |
| **Fast Loop**             | Runs every 300 ms for near real-time responses.                              |
| **Automatic Requeue**     | Re-boards the correct boat after each game.                                  |


---

## Requirements
- Microbot RuneLite client
- A primary weapon equipped and the configured switch weapon in inventory
- Pest Control world access

---

## Configuration Options
- **World**: Target world to play on.
- **Quick Prayer**: Enable/disable Quick Prayer usage.
- **Special Attack Percentage**: Energy threshold for using special attacks.
- **Target Priority**: Set the attack order for Brawlers, Portals, and Spinners.
- **Alching in Boat**: Enable/disable high-alching between matches.
- **Alch Item**: Name of item to alch.
- **Primary Combat Style**: The normal style used across the minigame (preloaded as Ranged).
- **Switch Combat Style**: The configured alternate style (preloaded as Melee).
- **Switch Weapon**: Exact inventory weapon name (preloaded as Dragon scimitar). A scimitar is used at the yellow slash/stab-weak portal; ranged remains active elsewhere.

---

## How It Works
1. The script checks if you are logged in and on the right world.
2. If needed, it hops worlds and travels to the Pest Control island.
3. Captures the equipped primary weapon and boards the correct boat for your combat level.
4. During games:
    - Moves to the center.
    - Activates prayers and special attacks as configured.
    - Attacks NPCs or portals based on your chosen priorities.
5. After games, it restores the primary weapon and queues for the next round.

---

## Disclaimer
This script is intended for use within the **Microbot RuneLite Client** only.  
Use of automation software in Old School RuneScape is against Jagex’s rules and can result in penalties to your account.  
Use at your own risk.

