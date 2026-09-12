# Original sound effects

Eight original synthesized sci-fi cues, authored for PROJECT Enigma. No third-party
samples, recordings, voices, or music are used. Licensed under the project's GPL-3.0.
Regenerate byte-for-byte with `python tools/generate_sound_effects.py`.
Add `--preview` to write `build/sfx-preview.wav` in the order below.

| File | Character | Trigger |
| --- | --- | --- |
| hover.wav | Soft electronic tick | Entering an enabled menu/action button with the mouse |
| click.wav | Crisp two-layer confirmation | Releasing a valid click on an enabled control |
| damage.wav | Compact metallic impact | Confirmed damage to either combatant |
| heal.wav | Warm ascending triad | Actual HP restoration, including opponent healing in PvP |
| chest.wav | Latch, pneumatic opening, light chime | Opening an unopened chest |
| skill.wav | Rising energy charge and burst | Confirmed tech-skill energy expenditure |
| encounter.wav | Two-note descending alert | Entering a solo or PvP battle |
| power_up.wav | Bright ascending arpeggio | Level-ups and actual energy restoration from cells/floors |

Format: 44.1 kHz, 16-bit signed PCM, mono; all under one second with silent
endpoints and peak headroom. `SoundEffects` loads each once, applies a restrained
master gain and per-effect mix, and disposes them on shutdown. Damage cues are
offset to combat animation phases; queued cues are cancelled when leaving a
screen. Duplicate/reconnection snapshots and rejected combat actions do not
produce combat sounds. An unavailable audio device leaves the game playable.
