# PROJECT Enigma — transparent animation sprite sheets

These PNGs are exported from the exact game texture importer and have real RGBA transparency. All five roles share the approved Sentinel scale. Source artwork and complete imagegen prompts are included separately in the project under assets/operatives.

Each role folder contains:
- world-transparent.png: 384×384 atlas, 6 columns × 4 rows, 64×96 cells. Rows DOWN, LEFT, RIGHT, UP. Idle columns 0–1; walk columns 2–5. 0.13 seconds/frame.
- battle-transparent.png: 2048×1152 atlas, 8 columns × 6 rows, 256×192 cells. Rows IDLE (4 frames), ATTACK (6), SKILL (8), GUARD (4), HURT (3), DEFEAT (6). 0.12 seconds/frame except defeat at 0.16. Unused columns are padding. Mirror horizontally for opponents.

Draw with nearest-neighbour filtering. Visible idle heights are approximately 70px world and 141px battle before display scaling. Feet sit four transparent pixels above the cell bottom. The game applies 1.30 world and 1.12 battle display factors; transparent padding does not alter visible character size.

To view the integrated animations, run PREVIEW_OPERATIVES_WINDOWS.bat in the repository root. This pack includes the generated artwork prompts in PROMPTS.md.
