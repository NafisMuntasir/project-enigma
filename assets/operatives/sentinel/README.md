# Sentinel animation assets

Custom artwork generated with the built-in image generation tool from the approved
Sentinel concept in `designs/characters-v1/sentinel.png`.

`world-keyed.png` contains six columns and four rows (down, left, right, up).
Each direction has two idle frames followed by four walking frames. Playback uses
the existing 0.13-second world animation timing.

`battle-keyed.png` contains eight columns and six rows: idle (4 frames), attack (6),
skill (8), guard (4), hurt (3), and defeat (6). Extra columns are padding. Battle
timing remains 0.12 seconds per frame, with 0.16 seconds for defeat.

The source PNGs use magenta backgrounds. `KeyedSpriteAtlas` removes that key,
finds transparent separators between battle frames, and aligns the feet when
importing them into transparent 64×96 world and 256×192 battle frames. The battle
frames include extra transparent width for consistency with the other roles;
Sentinel's visible size stays the same. Textures use
nearest-neighbour filtering. The battle sheet is mirrored for opponents.

Display sizes are calibrated against the original Sentinel's visible idle height:
the new character is approximately 8% taller in both world and battle views.
World frames are rendered at 1.30 times their base size and battle frames at 1.12
times their base size because their transparent padding differs. Feet remain
anchored; movement and collision dimensions are unchanged.

Run `PREVIEW_SENTINEL_WINDOWS.bat` from the repository root to view all six battle
animations and all four world directions. The viewer supports pausing, stepping,
mirroring and light/dark backgrounds. It does not start or save a run.

The asset tests export decoded transparent atlases to
`core/build/reports/operative-sprites/sentinel/` for inspection. The viewer now
also supports switching to the other four operatives.
