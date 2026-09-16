# Enemy animation assets

Created with the built-in image generation tool, using the existing sci-fi enemy
lineup for identity and the approved Sentinel sheets for rendering style.
Full generation/correction prompts and selected originals are in `PROMPTS.md`.

| Save ID | Enemy | Folder | Visible world / battle idle height |
|---|---|---|---|
| CAVE_SLIME | Recon Drone | recon-drone | 34 / 72 px |
| BONE_SENTINEL | Aegis Robot | aegis-robot | 73 / 145 px |
| ABYSS_MAGE | Helix Cyborg | helix-cyborg | 70 / 141 px |
| FLOOR_WARDEN | Enhanced Warden | enhanced-warden | 78 / 150 px |

The drone is compact and floats above its ground anchor. Humanoids are calibrated
against Sentinel's 70px world and 141px battle idle height, with larger armor on
Aegis and Warden. Each sheet uses one scale across battle poses so a defeated
enemy stays collapsed. Different world directions calibrate independently.

## Frame layouts

Each folder contains `world-keyed.png` and `battle-keyed.png`. The historical
`-keyed` naming is retained for compatibility with the shared importer; these
new generated PNGs have alpha transparency. The importer also supports magenta
keyed sources. Alpha below 128 is treated as transparent before frame measurement,
preventing faint background matte noise from affecting scale or ground alignment.
Original source files are preserved intact. Runtime textures use nearest filtering.

World: 4 columns × 4 rows, decoded into 96×96 cells. Rows are down, left, right,
up, each with four idle frames at 0.13 seconds/frame. Enemies remain stationary
sentries in dungeon gameplay; all four views are available in the art viewer.

Battle: 6 columns × 4 rows, decoded into 256×192 cells. Right-facing source art is
mirrored for enemies on the right side of combat.

| Row | Animation | Active frames | Seconds/frame |
|---|---|---|---|
| 0 | Idle | 4, looping | 0.12 |
| 1 | Attack / existing tech attack | 6 | 0.12 |
| 2 | Hurt | 3 | 0.12 |
| 3 | Defeat | 6 | 0.16 |

Remaining columns are padding. Existing guard behavior uses the idle row. Enemy
statistics, behavior, save IDs, attack timing and sound events are unchanged.

## Preview and transparent export

Run `PREVIEW_ENEMIES_WINDOWS.bat`, or use `--enemy-preview` when launching the
game. Choose an enemy, animation, or the Supply Chest tab. The viewer includes a
Sentinel size reference, four world directions, pause/step controls and a light
background option. It does not start a run or write a save.

After compiling, export transparent atlases using the same runtime decoder:

```text
java --class-path core/build/classes/java/main tools/InspectEnemySprites.java
```

Files go to `core/build/reports/enemy-sprites/<enemy>/`. The chest is exported to
the `chest` subfolder. The tool optionally accepts folder IDs to inspect a subset.

Tests check every frame for usable content, transparent margins, calibrated size
and base alignment, plus chest opening/clamping and already-open save behavior.
