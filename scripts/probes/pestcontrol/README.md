# Pest Control strategy probe

`PestControlStrategyProbePlugin.java` is a read-only dynamic Agent Server probe.
It records no player names and performs no interactions. Samples are submitted
under `pestcontrol-strategy-probe` and can be read from `/scripts/results`.

## Confirmed scene anchors

All coordinates below are region-local, so they remain stable across instance
template changes.

| Feature | Region coordinate | Live IDs / notes |
| --- | ---: | --- |
| Void Knight | `(32,32)` | Glow objects `14310`-`14313` |
| Purple portal | `(8,30)` | Active NPC `1739`, shielded NPC `1743` |
| Blue portal | `(55,29)` | Active NPC `1740`, shielded NPC `1744` |
| Yellow portal | `(48,13)` | Active NPC `1741`, shielded NPC `1745` |
| Red portal | `(22,12)` | Active NPC `1742`, shielded NPC `1746` |
| West gate | `(19,32)` | Purple lane |
| South gate | `(32,24)` | Shared red/yellow approach |
| East gate | `(47,32)` | Blue lane |

The second boat-tier portal IDs are `1747`-`1750` (active) and `1751`-`1754`
(shielded). Gate objects use `14233`-`14248`; even IDs are open variants and
odd IDs are closed variants. Barricades use fixed IDs `14224`-`14226`, damaged
IDs `14227`-`14229`, and destroyed IDs `14230`-`14232`.

## 2026-07-26 manual-round observations

The status-enriched probe captured 14 samples at roughly five-second intervals
from a round already in progress:

- Purple and yellow were already destroyed when this capture began. Between
  four and eight players remained assigned to the dead yellow lane in the
  earliest samples while blue and red were still alive.
- Once blue became attackable, seven to nine players converged on it. Its HP
  fell from 250 to 0 while the Void Knight fell from 189 HP to a low of 70 HP.
- Destroying blue restored the Void Knight by about 50 HP (70 to 119), matching
  the strategy-guide mechanic.
- Once red was the final portal, its crowd rose from three to 13-16 players and
  its HP fell from 238 to 0.
- Activity ranged from 37% to 74% during the captured window.
- The west gate was normally closed. The south gate alternated open/closed
  while the knight was under pressure; all three were closed late in the round.

## Current implementation implications

- Use attackable portal NPCs as the live source of truth instead of relying only
  on shield-drop chat messages.
- Choose among valid portals using other-player density; use purple as a
  tie-break when the west lane is under-covered.
- Kill Spinners at the selected portal before attacking the portal.
- Do not remain at a destroyed portal when another valid portal objective
  exists.
- Treat emergency defence and gate-closing as separate strategy work. The
  observed 70 HP low shows value, but indiscriminate gate interactions could
  also disrupt the mass route.

Source reviewed: <https://oldschool.runescape.wiki/w/Pest_Control/Strategies>
