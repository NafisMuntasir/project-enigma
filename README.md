# PROJECT Enigma

PROJECT Enigma is a desktop Java/libGDX RPG inspired by the gameplay loop of
[hdescottes/GdxGame](https://github.com/hdescottes/GdxGame). It keeps the
top-down exploration, class selection, inventory, progression, profiles, and
turn-based battles while replacing fixed outdoor TMX maps with deterministic
procedural dungeons. Every menu and gameplay action can be completed with a
keyboard; a mouse is not required. LAN multiplayer supports a host and one
guest in host-authoritative turn-based battles.

The project intentionally does not redistribute the reference game's Sword of
Mana maps, music, or sprite assets. It now includes an original utopian
pixel-art presentation built around white/off-white floors, dark graphite wall
architecture, cool grey, signal red, and electric blue.
See `SCI_FI_ENEMY_ART.md` for the enemy roster, compatibility mapping, and art prompt.

## Implemented gameplay

- Seeded room-and-corridor dungeon generation with guaranteed connectivity
- A distant, reachable staircase on every floor
- Fog of exploration, collision, camera following, chests, loot, and enemies
- Five sci-fi operatives—Sentinel, Hacker, Sniper, Enforcer, and Bio-Medic—with distinct statistics and tech skills
- Turn-based Attack, Tech Skill, Guard, Potion, and Run actions
- Enemy scaling, bosses every fifth floor, experience, levels, gold, and drops
- Four utopian sci-fi enemy archetypes: Recon Drone, Aegis Robot, Helix Cyborg, and Enhanced Warden
- Inventory/status overlay and field potion use
- New game, continue, automatic saves, manual saves, and safe window-close saves
- Complete mouse and keyboard control across menus, exploration, overlays, and combat
- LAN Host/Join flow with reconnect handling and synchronized PvP state
- Race-to-PvP mode: both players explore their own independent dungeon for a timed countdown, then fight using the heroes they grew
- 64x64 utopian dungeon tiles with dark graphite walls and red/blue procedural accent variation
- Four-direction idle/walk animation sheets for all heroes and enemies
- Idle, attack, skill, guard, hurt, and defeat battle animations
- Matching 1280x720 rooftop menu and atrium battle backgrounds
- Pure Java model tests for dungeon connectivity, placement, and combat rules

## Requirements

- JDK 17 or newer
- An internet connection the first time Gradle downloads libGDX dependencies

No separate libGDX SDK installation is needed. Gradle retrieves libGDX for the
project.

## Run the game

Open a terminal in the project root.

### Windows

Command Prompt:

```bat
gradlew.bat lwjgl3:run
```

PowerShell or the default VS Code terminal:

```powershell
.\gradlew.bat lwjgl3:run
```

Run that command from **Command Prompt, PowerShell, or the VS Code terminal**
while the terminal is open in the `PROJECT Enigma` folder. Do not double-click
`gradlew.bat`; it is Gradle's command wrapper and a double-clicked window closes
as soon as the process ends. If you prefer to double-click a file, use
`RUN_GAME_WINDOWS.bat` instead. It runs the correct task and pauses if a build
error occurs so the full message remains visible.

### macOS or Linux

```bash
./gradlew lwjgl3:run
```

The Gradle run task automatically adds `-XstartOnFirstThread` on macOS.

### VS Code

1. Install **Extension Pack for Java**.
2. Open this entire `PROJECT Enigma` folder, not only `core` or `lwjgl3`.
3. Allow VS Code to import the Gradle project.
4. Run the Gradle task `lwjgl3 > application > run`, or use the terminal command
   above.

## Build a runnable jar

```bash
./gradlew lwjgl3:dist
```

The jar is created at `lwjgl3/build/libs/PROJECT-Enigma.jar`.

Run it on Windows/Linux with:

```bash
java -jar lwjgl3/build/libs/PROJECT-Enigma.jar
```

On macOS, use:

```bash
java -XstartOnFirstThread -jar lwjgl3/build/libs/PROJECT-Enigma.jar
```

## Sound effects

Eight original sci-fi effects cover mouse hover, accepted clicks, damage, healing,
chest opening, tech skills, encounters, and power-ups. Damage/healing works for
both sides of solo/PvP battles; gameplay effects also work with keyboard actions.
Power-up audio marks level-ups and actual energy restoration. UI effects are
quieter than combat cues, and hover is emitted once per option entry.

Effects load once from `assets/audio`, are included in the runnable JAR, and are
released on shutdown. Failed actions, already-open chests, duplicate network
snapshots, and reconnect synchronization do not replay combat/reward sounds.
Audio failure does not prevent the game from running.

See `assets/audio/README.md` for the cue list and source information. Regenerate
with `python tools/generate_sound_effects.py`; add `--preview` for a combined
`build/sfx-preview.wav` audition in the order hover, click, damage, heal, chest,
skill, encounter, power-up.

## Mouse controls

- **Menus:** hover to highlight, press and release the left button on the same option to activate. Continue is disabled without a save.
- **Operatives:** click a card, then Begin (solo) or Ready (PvP). Back cancels; Ready locks the selection while waiting for the opponent.
- **Explore:** click an explored floor tile to follow the blue route. Paths go around walls and avoid enemy tiles. Click again to retarget; right-click stops. Unreachable clicks cancel the route and explain why.
- **Interact:** click a chest to approach and open it; click a visible enemy to approach and fight. Click stairs to approach, then click Descend to change floors.
- **HUD:** Inventory, Use Potion, Pause, and contextual Descend buttons are clickable. Inventory has Use Potion and Close; pause has Resume, Save Game, Main Menu, and Quit (Race Mode's pause menu is just Resume and Abandon Race, since a race session never saves).
- **Combat:** click an action; hover for descriptions and unavailable-action explanations. Inputs lock during animations and while a PvP request awaits the host. Click Continue after solo combat or Main Menu after PvP.
- **Multiplayer:** click Host or Join. Toggle "Race Mode" first to explore-then-fight instead of an immediate duel — see below. Click the address field to edit an IPv4 address; Ctrl+A selects all, Ctrl+V or Paste inserts the clipboard, and Connect joins. Cancel stops hosting/connecting; Abandon exits a disconnected match.
- **Focus:** keyboard movement, overlays, window focus loss, and screen changes cancel mouse travel. UI and letterbox clicks never move the operative. All previous keyboard shortcuts remain available.

PvP keeps its existing network format. Because guest snapshots do not include potion counts, an exhausted-potion attempt may first be rejected by the host (without spending a turn); further potion clicks are then disabled for that match.

**Race Mode:** toggle it on in the Multiplayer menu before Host/Join, and
use the "-"/"+" buttons (or Left/Right while it's selected) to set the
exploration length from 30 seconds to 10 minutes — the host's choice is
what counts, sent to the guest automatically. Both players pick a class,
then explore their own independent (but identically-seeded) dungeon until
the countdown time-bar at the top of the screen runs out. When time's up,
the screen shows "Waiting for opponent" while the other player finishes;
once both are done, the fight begins using the heroes each player
actually grew (level, HP/MP, attack, defense, potions). Esc or "Abandon
Race" leaves at any point. See `DESIGN.md` §10 for the full design.

## Keyboard controls

### Menus

| Action | Keys |
| --- | --- |
| Select | `W`/`S` or arrow keys |
| Confirm | `Enter` or `Space` |
| Back | `Esc` |

### Dungeon exploration

| Action | Keys |
| --- | --- |
| Move | `WASD` or arrow keys |
| Descend stairs | `E` or `Enter` while on the stairs |
| Inventory | `I` or `Tab` |
| Drink potion | `P` |
| Pause/save/menu | `Esc` |

### Combat

| Action | Keys |
| --- | --- |
| Select action | `W`/`S` or arrow keys |
| Confirm | `Enter` or `Space` |
| Direct action | Number keys `1` through `5` |
| Attempt to run | `Esc` |

## Saves

The save is stored at `save/project-enigma-save.json`, relative to the project
working directory. The game saves after important loot, victories, floor
changes, manual saves, periodic movement, and normal window closure. A corrupt
save is rejected safely instead of crashing the game. Saves from the earlier
build are detected and migrated automatically when loaded.

## Artwork and animation

All runtime artwork is under `assets/utopia`. `UtopiaAssets.java` owns the
textures for the lifetime of the game, applies nearest-neighbour filtering,
selects sprite frames, creates opponent-facing regions once during loading,
and disposes the shared textures on shutdown. Screens never allocate textures
per frame or per screen transition.

The dungeon uses the 64x64 tile atlas and the battle screens use 128x192 frame
grids. The Gradle `lwjgl3:dist` task embeds the complete `assets` directory in
the runnable jar.

## Tests

Run the normal Gradle tests:

```bash
./gradlew test
```

The dependency-free logic smoke test is also available:

```bash
./verify-logic.sh
```

It checks 250 generated dungeon seeds for full connectivity and also exercises
session population and lethal combat resolution.

## Project structure

```text
core/
  src/main/java/com/dungeonrpg/
    model/       Pure dungeon, session, hero, enemy, and battle rules
    screen/      Menu, class selection, dungeon, combat, and game-over screens
    UtopiaAssets.java
    ProjectEnigmaGame.java
assets/
  utopia/         Tiles, backgrounds, hero/enemy sheets, and manifests
lwjgl3/
  src/main/java/com/dungeonrpg/lwjgl3/
    Lwjgl3Launcher.java
REFERENCE_AUDIT.md
verify-logic.sh
```

See [REFERENCE_AUDIT.md](REFERENCE_AUDIT.md) for the source review and the bugs
that informed this implementation. See [ART_INTEGRATION.md](ART_INTEGRATION.md)
for the exact atlas, animation, screen, and packaging mappings.

## License

GPL-3.0. The reference repository is also GPL-3.0; attribution is retained here
because this project was built from its design and source review.
