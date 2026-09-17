# Augmentations and general skills

## Integration with the existing game

`Hero.gainExperience` still uses the original XP thresholds, class stat gains,
and full HP/EN restoration. Each level gained also queues one saved upgrade
choice. Victory resolves and finishes its animation, then a blocking Upgrades
menu appears. Any offered option may be selected; the catalogue is paginated,
not randomly rolled. A multi-level reward requires one choice per level.
Eligibility uses the oldest outstanding level, so a large XP reward cannot
unlock late skills using an earlier level's choice.

Choose an active unlock or a passive augmentation. Selection applies once,
saves immediately in solo play, and closes after the last pending choice.
Quit/reload preserves outstanding choices. Old saves retain their stats,
items and XP; old levels do not retroactively grant unearned choices.

## Controls and loadouts

- **L / Skills** opens the same menu in exploration, solo combat, or PvP.
- Select a list row to read details. Use Previous/Next, mouse wheel, or arrow
  keys to browse. W/S selects a row. Enter confirms a choice or uses a skill.
- The General tab shows every active skill, including locked ones; Passives
  shows augmentation ranks. The Technical button retains the class skill.
- In exploration, click Assign slot 1/2/3 (or press 1/2/3) to equip an unlocked
  skill. Three general skills fit a loadout. The class technique is always
  available separately and does not occupy a slot.
- New unlocks automatically enter an empty slot. Replacements are manual.
  Loadouts can only change outside combat. Swapping slots does not clear field
  recovery counters.
- Click Use Skill or press Enter to activate. T activates the technical skill
  in combat. Esc closes Skills; it cannot dismiss a required upgrade choice.
- Existing combat controls remain: solo 1 Attack, 2 Tech, 3 Guard, 4 Run, I
  Items; classic PvP keeps its original 1-5 actions and potion handling.

## Skill balance

All combat targets are the single opponent in the existing encounter. There
is no distance or area-of-effect targeting in this turn-based arena. Direct
damage uses the original variance (+/-2 ATK), crit chance (1.6x), armor, and
guard rules, unless a skill explicitly ignores armor.

Cooldowns count **other accepted actions by the owner** after casting. A
cooldown of 2 requires two other actions before reuse. Opponent actions and
invalid/repeated clicks do not advance it. Items and interrupted turns do.
Combat cooldowns/statuses reset for each encounter and do not enter saves.
DoT and regeneration trigger before the affected combatant's next action.
DoT can end a fight before that combatant attacks; normal loot/XP still apply.

| Skill | Unlock level | EN | Cooldown | Effect |
|---|---:|---:|---:|---|
| Disruption Pulse | 2 | 3 | 2 | Stun: skip the next opponent action; no damage. |
| Arc Discharge | 3 | 5 | 4 | Shock: 90% ATK damage plus one skipped action. |
| Thermal Overload | 2 | 3 | 3 | 40% ATK hit, then 7 + level damage before each of two target actions. |
| Reactor Burst | 4 | 8 | 5 | Explosion: 320% ATK burst. |
| Photon Lance | 2 | 4 | 2 | 135% ATK; ignores armor, but not protective effects. |
| Nanite Corrosion | 3 | 4 | 4 | 4 + level damage before each of three target actions. |
| Deflection Field | 2 | 3 | 3 | Halve the next two direct hits. |
| Energy Barrier | 2 | 4 | 4 | Absorb 24 + 2 x level damage, including DoT. |
| Phase Shift | 3 | 4 | 4 | Negate the next direct hit; does not stop DoT. |
| Nanite Restoration | 2 | 4 | 3 | Restore 24 + 2 x level HP; usable in exploration with a separate 12-tile recovery. |
| Emergency Repair | 3 | 4 | 4 | Restore 10 + level HP before each of three owner actions. |
| System Scan | 2 | 2 | 3 | Reveal ATK/DEF in the log; amplify the next two direct hits against the target by 25%. |
| Overclock | 3 | 3 | 4 | Costs an additional 8 HP; next three damaging attacks gain 30%. Requires more than 8 HP. |
| Vector Boost | 2 | 3 | 30 tiles | Exploration only: +40% movement speed for 20 successful tile moves. |

A target that loses an action to an interrupt resists another interrupt until
it completes one subsequent action. This prevents permanent stun chains.
Reapplying the same DoT/buff refreshes or replaces it, rather than stacking.
Burn and corrosion bypass armor/guard but respect barriers. All timers and
costs are displayed; unavailable buttons explain why.

## Passive upgrades

| Upgrade | Per rank | Rank cap |
|---|---|---:|
| Precision Actuators | +2 base ATK | 5 |
| Vitality Lattice | +12 max HP, restore 12 HP | 5 |
| Adaptive Plating | +1 base DEF | 5 |
| Vector Servos | +8% exploration movement speed | 5 |
| Parallel Processors | -1 general skill cooldown action, minimum 1 | 2 |
| Predictive Targeting | +3 percentage points crit, total cap 50% | 5 |
| Expanded Capacitor | +2 max EN, restore 2 EN | 5 |
| Nanite Amplifier | +10% healing effectiveness | 5 |

Normal class-based level bonuses still apply in addition to the chosen upgrade.
Once every unlock and passive is complete, a level-up choice grants 25 credits.
There is no attack-speed passive: the arena has discrete turns, not a real-time
attack rate. Field movement speed applies equally to keyboard and mouse routes.

## Multiplayer and saves

Skills use the same `BattleEngine` in solo and host-authoritative PvP. The guest
only requests an ID; the host checks the turn, unlock, equipped slot, cost,
cooldown, target resistance and connection state. Snapshots include authoritative
skill eligibility, cooldowns and status labels, including after reconnect.

Classic PvP still starts with fresh level-1 heroes and their class technique;
general unlocks are earned in solo or Rush exploration. Rush transfers earned
unlocks, equipped skills and passive modifiers to PvP on both sides. The shared
Rush clock continues during menus for fairness. If time expires during an earned
upgrade choice, that player completes the pending choices before submitting the
final loadout; exploration does not resume. Rush selections never overwrite the
solo save. Both multiplayer peers must run the updated build for skill packets
and enriched snapshots.

Exploration cooldowns/buff steps are persisted. Battle-only status/cooldowns
are deliberately transient, matching the pre-existing guard/encounter lifetime.
There is no mid-battle resume format; closing the game resumes the dungeon using
the project's existing save behavior.

## Extension points / changed files

- `model/Skill.java`: complete active catalogue, level gates, costs, durations,
  cooldowns, effect types and descriptions. Add an enum entry to extend the menu;
  add a resolver case only for a new effect type. Keep saved IDs stable.
- `model/PassiveUpgrade.java`: rank limits, names, flat stat changes. Append
  entries instead of reordering them, because saved ranks use catalogue order.
- `model/Progression.java`: saved choices, unlocks, three slots, passive ranks,
  field effects, validation and migration defaults.
- `model/CombatEffects.java`: encounter-local counters and status summaries.
- `model/BattleEngine.java`: accepted-action status/cooldown lifecycle, skills,
  items, existing class technique and original damage formula.
- `model/Hero.java`, `model/GameSession.java`: level-up queue, derived modifiers,
  initialization and movement recovery.
- `screen/ProgressionOverlay.java`, `screen/AbstractGameScreen.java`: shared
  modal rendering/input, paging, descriptions and loadout controls.
- `screen/DungeonScreen.java`, `screen/CombatScreen.java`,
  `screen/PvPCombatScreen.java`, `screen/MenuScreen.java`: integration, status
  feedback, existing animation/audio and updated controls.
- `ProjectEnigmaGame.java`: pending Rush choices and solo-save protection.
- `network/ProgressionSnapshot.java`, `SkillSnapshot.java`, `PvPSkillPacket.java`,
  `HeroSnapshot.java`, `HeroLoadout.java`, `PvPMatch.java`, `PvPServer.java`,
  `PvPClient.java`: immutable transfer and host validation.
- Tests: `ProgressionTest`, `GeneralSkillTest`, `GeneralSkillNetworkTest`, plus
  real input/transition cases in `MouseIntegrationTest`.

Allies, multi-target attacks, map teleportation, and real-time action speed were
not added: these require mechanics absent from the current one-opponent arena.
Existing sprites/skill animations and sound cues are reused; no art rewrite.
