# Sci-fi enemy art direction

## Final roster

| Runtime ID retained for saves | New display name | Category | Combat read |
| --- | --- | --- | --- |
| `CAVE_SLIME` | Recon Drone | Aerial robot | Hovering shell, blue optic, red fins, beam dash |
| `BONE_SENTINEL` | Aegis Robot | Humanoid robot | White armor, blue visor, red forearm emitter |
| `ABYSS_MAGE` | Helix Cyborg | Human cyborg | Visible face and hair, augmented eye/arm, blue projector |
| `FLOOR_WARDEN` | Enhanced Warden | Enhanced human | Visible skin and face, implants, heavy armor, red energy blade |

The generated concept board is stored at
`assets/utopia/concepts/sci_fi_enemy_lineup_reference.png`. The active animation
sheets are now custom imagegen artwork under `assets/adversaries/`, styled to
match the upgraded operatives. See `assets/adversaries/README.md` for layouts,
scale, import behavior and the full animation prompt set. The older sheets made
by `tools/generate_sci_fi_enemies.py` are retained as legacy assets.

The chest has matching generated closed/opening/open states in
`assets/props/chest/`. Run `PREVIEW_ENEMIES_WINDOWS.bat` to inspect the complete
enemy roster and the chest without creating a saved run.

## Final image-generation prompt

```text
Use case: style-transfer
Asset type: visual design reference for a Java/libGDX pixel-art RPG enemy roster
Primary request: Redesign the four enemy characters in the reference as a cohesive utopian science-fiction lineup.
Input image: the supplied enemy sprite montage is a style-and-scale reference only; replace the subjects.
Subject: exactly four separate full-body opponents, left to right: (1) a compact hovering reconnaissance/combat drone with a white aerodynamic shell, graphite underside, blue optic and small red warning fins; (2) a human-sized bipedal security robot with clean off-white armor plates, dark mechanical joints, a single blue visor and red forearm weapon; (3) a visibly human cyborg operative with natural face and hair, one augmented eye, one mechanical arm, white technical coat, graphite undersuit and blue energy projector; (4) a muscular enhanced-human elite warden, unmistakably human face and skin, white segmented combat suit, subtle biomechanical implants, red energy blade and blue status lights.
Style/medium: crisp hand-authored 16-bit/32-bit pixel art, readable silhouettes, limited clusters, no smoothing, no painterly rendering.
Composition/framing: one clean horizontal character lineup, every figure fully visible and separated, neutral three-quarter combat poses, equal visual scale; no sprite-sheet grid and no animation frames.
Scene/backdrop: plain cool light-grey studio background.
Color palette: white, off-white, cool graphite grey, deep navy, vivid red and electric blue; skin and hair only on the cyborg and enhanced human.
Lighting/mood: bright clinical utopian technology with a controlled dystopian edge.
Constraints: preserve the reference game's crisp pixel density and human-like proportions; four subjects only; no labels, text, UI, logos or watermark.
Avoid: fantasy robes, bones, slime, medieval armor, guns dominating the silhouettes, chibi proportions, excessive neon, photorealism, gradients, blur.
```

The concept board used the built-in image-generation workflow. Exact frame
dimensions, alpha transparency, pixel alignment, animation timing, and
runtime compatibility are deterministic outputs of the local generator.
