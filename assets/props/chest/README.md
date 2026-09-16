# Supply chest

`opening-keyed.png` is a generated sci-fi supply crate with four frames in a
2-column × 2-row grid, read left-to-right then top-to-bottom:

1. Closed
2. Unlatched / lid starting to rise
3. Lid opening
4. Fully open, empty interior

`EnemyArt.CHEST` imports this into transparent 64×96 cells. The closed frame's
visible height is calibrated to 32 pixels, and all frames use that one scale.
This keeps the base width stable while the lid rises. The original PNG contains
alpha transparency; the filename follows the shared importer convention.

Playback runs at 0.12 seconds/frame and holds the final frame. `DungeonScreen`
records a transient animation start only when the existing loot action opens a
closed chest. Loot, chest sound and save logic are unchanged. Loading an already
open chest displays the final frame immediately and cannot grant loot again.
No new fields are added to saved chest data.

Use `PREVIEW_ENEMIES_WINDOWS.bat` and select Supply Chest to inspect all states.
The generation prompt is recorded in `assets/adversaries/PROMPTS.md`.
