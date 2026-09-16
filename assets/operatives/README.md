# Custom operative animations

Artwork was created with the built-in image generation tool, using the approved
role concepts in `designs/characters-v1` and Sentinel's animation sheets as style
and layout references. Full prompts and selected output paths are recorded in
`PROMPTS.md`. These assets replace the old playable-character artwork in class
selection, dungeon exploration, solo combat and both sides of PvP combat.

| Role | Stable game/save ID | Asset directory | Accent and equipment |
|---|---|---|---|
| Sentinel | WARRIOR | sentinel | Blue visor, shield and baton |
| Hacker | MAGE | hacker | Cyan glasses and wrist deck |
| Sniper | THIEF | sniper | Amber scope, rifle and short cape |
| Enforcer | GRAPPLER | enforcer | Orange piston gauntlets |
| Bio-Medic | CLERIC | bio-medic | Teal injector and medical backpack |

## Sheets and playback

Every role has `world-keyed.png` and `battle-keyed.png`. The magenta background is
an export key removed by `KeyedSpriteAtlas` during loading. Runtime textures have
real transparency and nearest-neighbour filtering. Do not use the keyed sources
directly as transparent textures in other engines.

World sheets contain 6 columns × 4 rows: down, left, right, up. Columns 0–1 are
idle; columns 2–5 are the walk cycle. Runtime frames are 64×96 pixels, played at
0.13 seconds per frame.

Battle sheets contain 8 columns × 6 rows. Runtime frames are 256×192 pixels;
unused columns pad the sheet and are not part of the active animation.
The extra transparent width accommodates rifles, extended gauntlets and effects
without changing the character's visible size.

| Row | Animation | Active frames | Seconds/frame |
|---|---|---|---|
| 0 | Idle | 4 | 0.12 |
| 1 | Attack | 6 | 0.12 |
| 2 | Skill | 8 | 0.12 |
| 3 | Guard | 4 | 0.12 |
| 4 | Hurt | 3 | 0.12 |
| 5 | Defeat | 6 | 0.16 |

Battle art faces right. The renderer mirrors frames for the opponent. The
existing combat engine controls pose selection and eligibility. Tech skills
remain offensive, including Bio-Medic's nano disruptor; combat balance, sounds,
save IDs and network packets are unchanged by the artwork update.

## Consistent size

Sentinel's approved import and display settings are preserved. Other roles are
calibrated to its visible idle height: 141 pixels in battle frames and 70 pixels
in world frames. Calibration uses the initial idle frame, not each animation
pose, so kneeling, recoil and defeat keep their natural proportions. World
directions are calibrated separately to account for different camera views.

All roles share Sentinel's display factors: 1.12 for battle and 1.30 for world.
Their aspect ratios are preserved and feet share the same ground anchor. Widths
vary naturally with equipment and build. Movement and collision remain tile based.

## Preview and export

Run `PREVIEW_OPERATIVES_WINDOWS.bat` in the repository root. Select a role and
animation, pause or step, switch the world idle/walk state, or change the
background. Opening the viewer does not modify a saved run.

Run `./gradlew test` to check all sheets and export transparent atlases to
`core/build/reports/operative-sprites/<role>/`. For a standalone export after
compiling the game, run from the repository root:

```text
java --class-path core/build/classes/java/main tools/InspectOperativeSprites.java sentinel hacker sniper enforcer bio-medic
```

The exporter uses the exact runtime decoder. It reports per-frame visible bounds
so scale, clipping and short collapsed poses can be checked independently.
