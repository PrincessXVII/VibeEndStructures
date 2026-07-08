# VibeDragon Test Plan

## LuckPerms Setup

Use a dedicated tester group. This gives access only to `/vibedragon`, not `/vibeend` structure commands.

```text
/lp creategroup dragon_tester
/lp group dragon_tester permission set vibedragon.tester true
/lp user <nick> parent add dragon_tester
```

Remove access after testing:

```text
/lp user <nick> parent remove dragon_tester
```

Operator/admin fallback:

```text
/lp group admin permission set vibedragon.admin true
```

## Before Testing

1. Restart the server after uploading the new jar.
2. Use a clean test arena when possible:
   `/vibedragon despawn ender_arena`
3. Confirm config is loaded:
   `/vibedragon list`
4. Confirm End world/portal data:
   `/vibedragon seed`
5. Record console logs during every test.

## Command Matrix

| ID | Command | Tester Action | Expected Result | Pass Notes |
| --- | --- | --- | --- | --- |
| C1 | `/vibedragon list` | Run as tester | Shows 3 dragon types and arenas | Types: `ender_dragon`, `fire_dragon`, `frost_dragon` |
| C2 | `/vibedragon seed` | Run as tester | Shows End world seed, portal location, arena centers | No errors in console |
| C3 | `/vibedragon spawn ender_dragon ender_arena` | Spawn normal dragon | Ritual starts, crystals appear around center portal, dragon spawns | No second dragon |
| C4 | Repeat C3 while active | Try duplicate spawn | Command is rejected | Arena remains ACTIVE |
| C5 | `/vibedragon contribute ender_arena` | During/after fight | Shows contribution data if players dealt damage | Empty is acceptable only before damage |
| C6 | `/vibedragon despawn ender_arena` | Despawn active dragon/ritual | Dragon/ritual/minions removed, arena resets | No delayed dragon appears |
| C7 | `/vibedragon cooldown ender_arena 0` | Clear cooldown for retest | Cooldown updates | Next spawn allowed |
| C8 | `/vibedragon reward <nick> ender_dragon rare` | Give manual reward | Player receives named/lored reward item | No egg required for this check |
| C9 | `/vibedragon wipe status` | Check schedule | Shows End schedule state | No errors |
| C10 | `/vibedragon reload` | Reload config | Config reloads without restart | Dragon types remain visible |

## Spawn And Visual Tests

| ID | Scenario | Steps | Expected Result |
| --- | --- | --- | --- |
| S1 | Vanilla-style spawn animation | Spawn `ender_dragon` at `ender_arena` | Crystals are centered around `0 0` portal, vanilla beams/respawn animation plays |
| S2 | Fire dragon spawn | Spawn `fire_dragon fire_arena` | Dragon has custom name and fire-themed ritual particles |
| S3 | Frost dragon spawn | Spawn `frost_dragon frost_arena` | Dragon has custom name and frost-themed ritual particles |
| S4 | Active arena protection | Spawn while arena ACTIVE | Command rejects the spawn |
| S5 | Missing entity recovery | Despawn/kill entity unexpectedly, then spawn again | Arena does not stay stuck ACTIVE |

## Combat Tests

| ID | Scenario | Steps | Expected Result |
| --- | --- | --- | --- |
| B1 | Damage contribution | 2 testers damage dragon | `/vibedragon contribute` shows both players with damage |
| B2 | Phase changes | Reduce dragon HP below 60% and 30% | Actionbar phase changes, phase particles/sound play |
| B3 | Abilities | Stay in arena during fight | Breath, charge, minion/frost/fire abilities run without console errors |
| B4 | Bossbar policy | Fight active dragon | Only actionbar should be used by plugin; no custom top bossbar from plugin |
| B5 | Death animation | Kill dragon | Death ritual runs about 10 seconds before loot appears |

## Loot And Egg Tests

| ID | Scenario | Steps | Expected Result |
| --- | --- | --- | --- |
| L1 | Normal kill with contribution | Tester damages and kills normal dragon | Loot scatters around island after death ritual |
| L2 | Kill with no contribution | Kill with command/creative | Fallback loot still scatters; no silent empty drop |
| L3 | Loot naming | Pick up dropped loot | Items have colored custom names, lore, rarity; epic/legendary glint |
| L4 | Egg chance | Kill normal/auto dragon multiple times or temporarily set chance to `1.0` | Renewable egg falls from sky if roll succeeds |
| L5 | Egg fall animation | Observe egg drop | Armor stand egg falls with white/purple style particles and no console particle errors |
| L6 | Egg glowing | Pick up renewable egg | Holder receives glowing effect while egg is in inventory |
| L7 | Egg summon | Place renewable egg on bedrock near `0 0` | Egg disappears, dragon summon ritual starts |
| L8 | Summoned dragon egg rule | Kill dragon summoned by egg | Loot drops, but no new egg drops |

## Schedule Tests

| ID | Scenario | Steps | Expected Result |
| --- | --- | --- | --- |
| T1 | Start wipe timer | `/vibedragon wipe start 6` | End is closed for non-admin players |
| T2 | Manual open | `/vibedragon wipe open` | End opens; first dragon timer is ready |
| T3 | First dragon after open | Open End and wait 5 minutes | First dragon spawn starts automatically |
| T4 | Daily spawn slots | Observe or temporarily simulate time | Auto spawn runs at 16:00 and 21:00 MSK |

## Bug Report Format

Use this format in Discord/issue tracker:

```text
Test ID:
Tester:
Server time:
Command/steps:
Expected:
Actual:
Coordinates:
Screenshots/video:
Console error:
```

## Known Expected Behavior

- Dragon summoned by renewable egg must not drop another egg.
- Egg drop is chance-based unless `egg-drop-chance` is temporarily set to `1.0`.
- If contribution is empty, fallback loot should still drop.
- Testers should not receive `/vibeend` structure permissions from `vibedragon.tester`.
