# PROJECT Enigma Phase 1 — Minimal LAN PvP

This document is the design/integration companion to the code in this
archive. It covers the seven original deliverables (package structure,
code skeletons, integration plan, setup instructions, extensibility map,
omitted features, threading diagram), plus the story of a real bug this
went through and how it changed the design.

## Revision history (short version)

1. **First pass** used KryoNet 2.22.0-RC1 for networking, with the DTOs
   (`PvPBattleState`, `HeroSnapshot`, the packet types) as `record`s.
2. **On a real run**, hosting/joining threw at startup:
   `UnsupportedOperationException: can't get field offset on a record
   class`, from Kryo 2.24.0 (the version KryoNet 2.22.0-RC1 bundles)
   trying to reflectively serialize `HeroSnapshot`. Recent JDKs
   specifically block `Unsafe.objectFieldOffset` on `record` fields; Kryo
   2.24.0 (2014) predates records entirely and has no dedicated support
   for them.
3. **This revision removes KryoNet and Kryo entirely.** Networking is now
   plain `java.net.Socket` + `java.io.ObjectOutputStream`/
   `ObjectInputStream` (`network/PvPConnection.java`) -- zero third-party
   dependencies. The DTOs are back to being `record`s, `implements
   Serializable`, because the JDK's own serialization *does* have first-class
   record support and doesn't hit the same wall.
4. This version was verified more thoroughly than the first: two new test
   classes (`PvPConnectionTest`, `PvPEndToEndTest`) spin up real sockets --
   not mocks -- and one of them plays a full match to completion between a
   real `PvPServer` and a real `PvPClient`, including the exact
   record/enum/`List` DTO shapes that broke before. See "Verification" below.
5. **Utopian art integration:** the former `ShapeRenderer` silhouettes and
   arena placeholders were replaced with shared, nearest-filtered textures.
   `UtopiaAssets` owns every sheet; dungeon, PvE, PvP, class-selection, and
   menu screens only request frames, so the LAN protocol and authoritative
   battle rules remain unchanged.
6. **Race-to-PvP mode added** (this revision): two players now each get
   their own independent single-player exploration phase, synced by a
   host-owned countdown, before converging into the existing PvP flow.
   See §10. No existing classic-PvP or single-player code path changed
   behavior — Race mode is additive, reusing the same `PvPServer`/
   `PvPClient`/`PvPMatch`/`PvPCombatScreen`/`DungeonScreen` machinery with
   new packet types and a `raceMode` flag rather than parallel copies of
   any of them.

**Why sockets instead of another library (e.g. Netty):** Netty solves a
different problem than this one -- it's an async, event-loop I/O framework
for servers handling many concurrent connections with a custom wire
protocol, and it still doesn't include object serialization, so you'd face
the exact same "how do I turn a `PvPBattleState` into bytes" question again
with more scaffolding around it. For two players on a LAN, plain blocking
sockets plus one reader thread per connection is simpler, has no library
version to get wrong, and is easy to read start to finish.

---

## 1. Verification

This is worth stating plainly, since the previous revision's confidence
turned out to be misplaced on exactly the piece that mattered most:

- **`Combatant`/`BattleEngine`/`Hero`/`DungeonEnemy`/`PvPMatch`**: compiled
  with `javac` and exercised with dedicated smoke tests (turn order,
  guard, mana, victory/surrender attribution, pause/resume, abandon).
  `LogicSelfTest` was run against your actual unchanged
  `DungeonGenerator`/`GameSession`, 250 dungeon seeds included.
- **`PvPConnection`** (the socket + `ObjectOutputStream`/
  `ObjectInputStream` layer that replaced KryoNet): compiled and run
  against **real localhost sockets** in three scenarios --
  a `PvPBattleState` containing a nested `HeroSnapshot` record, an enum,
  and a `List<String>` round-tripping with full equality; five sequential
  sends surviving the stream `reset()` between each one; and one side
  closing correctly notifying the other. These are now
  `PvPConnectionTest.java` under `core/src/test/java`.
- **The full lobby-to-victory flow**: a real `PvPServer` bound to a port, a
  real `PvPClient` connecting to it, a class-selection packet, a
  `PvPMatch`, and a complete `ATTACK`/`ATTACK` fight to a definite winner
  -- driven exactly the way `ProjectEnigmaGame`/`PvPCombatScreen` drive it
  (host applies actions locally and broadcasts; guest sends a packet and
  waits for the broadcast to come back). This is `PvPEndToEndTest.java`.
  This is the test that would have caught the original bug before a
  manual run did.
- **`PvPServer`/`PvPClient`/`PvPCombatScreen`/`MultiplayerMenuScreen`**:
  still cannot be compiled in this sandbox (no libGDX jar reachable here),
  but `PvPServer`/`PvPClient` were additionally compile-checked against a
  hand-written stub of `com.badlogic.gdx.Gdx`/`Application` exposing just
  `log(String, String)` and `postRunnable(Runnable)` -- the only two Gdx
  calls either class makes -- which caught real errors before you had to.
  Run `./gradlew build` as step one below to get the real compiler's
  opinion with the actual libGDX jar.
- **Race-to-PvP (this revision), same caveat applies again:**
  `HeroLoadout`, `RaceStartPacket`, `RaceTimerSyncPacket`,
  `ReadyForPvPPacket` (all pure Java, no libGDX) were compiled and
  exercised with `HeroLoadoutTest`; the extended `PvPServer`/`PvPClient`
  dispatch for the three new packet types was compiled against the same
  Gdx/Application stub as above and exercised end-to-end over **real
  localhost sockets** in `RaceModePacketsTest` -- a real `PvPServer` sends
  `RaceStartPacket` and two `RaceTimerSyncPacket`s (including the
  `secondsRemaining == 0` end-of-exploration signal) to a real connected
  `PvPClient`, and the client sends a `ReadyForPvPPacket` back, all
  asserted for exact round-trip equality. `ProjectEnigmaGame`,
  `DungeonScreen`, `MultiplayerMenuScreen`, and `ClassSelectScreen`'s
  changes could **not** be compiled here for the same libGDX-jar reason as
  the rest of the screen layer always couldn't — read them carefully
  before trusting them, and run `./gradlew build test` as step one.

---

## 2. Package structure

```
core/src/main/java/com/dungeonrpg/
  model/
    Combatant.java              NEW    shared contract BattleEngine depends on
    BattleEngine.java            MOD    now resolve(attacker, defender, action)
    Hero.java                    MOD    implements Combatant
    DungeonEnemy.java            MOD    implements Combatant
    (BattleAction, BattleOutcome, TurnResult, EnemyType, HeroClass,
     DungeonMap, DungeonGenerator, GameSession, GridPoint, Room,
     TileType, DungeonChest — UNCHANGED, not included in this archive)
  network/
    Wire DTOs & packets (records, java.io.Serializable):
      HeroSnapshot.java          NEW
      PvPBattleState.java        NEW
      PvPActionPacket.java       NEW
      PvPClassSelectPacket.java  NEW
      PvPAbandonPacket.java      NEW
    Enums:
      MatchStatus.java           NEW    connection lifecycle
      PvPOutcome.java             NEW    who won
    Logic & wiring:
      PvPMatch.java                NEW    host-authoritative battle state (pure Java)
      PvPConnection.java           NEW    one socket, wrapped for send/receive (package-private)
      PvPServer.java                NEW    host-side wrapper (ServerSocket + PvPConnection)
      PvPClient.java                NEW    guest-side wrapper + auto-reconnect
  screen/
    MultiplayerMenuScreen.java   NEW    Host / Join (+ keyboard IP entry)
    PvPCombatScreen.java         NEW    the PvP battle screen
    ClassSelectScreen.java       MOD    reusable via onConfirm/onCancel callbacks
    CombatScreen.java            MOD    updated to the new BattleEngine API
    MenuScreen.java              MOD    added "Multiplayer" entry
  ProjectEnigmaGame.java             MOD    owns PvPServer/PvPClient lifecycle

core/src/test/java/com/dungeonrpg/
  model/
    BattleEngineTest.java        MOD    updated to the new API + a PvP-shape test
    LogicSelfTest.java           MOD    updated to the new API + a PvP-shape check
  network/
    PvPConnectionTest.java       NEW    real-socket serialization tests
    PvPEndToEndTest.java         NEW    real host+guest+match, full fight to completion

core/build.gradle                MOD    no new dependency (previously added, now removed, KryoNet)

lwjgl3/src/main/java/com/dungeonrpg/lwjgl3/
  Lwjgl3Launcher.java             MOD    parses --host / --connect <ip>
```

Only new/modified files are included in this archive. Everything else in
your project is untouched — copy these files over the matching paths.
`NetworkRegistration.java` from the previous revision is gone; plain Java
serialization needs no class registration.

**This revision (Race-to-PvP) additionally adds/modifies:**

```
core/src/main/java/com/projectenigma/
  network/
    HeroLoadout.java             NEW    full grown-hero transfer, sanity-clamped on arrival
    RaceStartPacket.java         NEW
    RaceTimerSyncPacket.java     NEW
    ReadyForPvPPacket.java       NEW
    PvPServer.java                MOD   + sendToGuest(Object), + onReadyForPvp default method
    PvPClient.java                MOD   + sendReadyForPvp, + onRaceStart/onRaceTimerSync default methods
  screen/
    ClassSelectScreen.java        MOD   + title-overload constructor (Race mode's own wording)
    MultiplayerMenuScreen.java    MOD   + "Race Mode: ON/OFF" toggle
    DungeonScreen.java             MOD   + raceMode flag, timer/time-bar HUD, waiting-for-opponent
                                          overlay, race-only pause menu, guarded SaveService calls
  ProjectEnigmaGame.java           MOD   + full Race-to-PvP lifecycle (see §10)

core/src/test/java/com/projectenigma/network/
  HeroLoadoutTest.java           NEW    clamping/round-trip
  RaceModePacketsTest.java       NEW    real-socket dispatch test for the 3 new packets

DESIGN.md                          MOD   this section
README.md                          MOD   brief mention under Multiplayer
```

---

## 3. The `Combatant` refactor (what changed and why)

Unaffected by the networking pivot; unchanged from the previous revision.

`BattleEngine` no longer takes a `Hero` and a `DungeonEnemy` in its
constructor. It takes nothing but a seed, and `resolve(Combatant attacker,
Combatant defender, BattleAction action)` resolves **one** actor's turn —
it does not automatically resolve a reply.

```java
public final class BattleEngine {
    public BattleEngine(long seed) { ... }
    public TurnResult resolve(Combatant attacker, Combatant defender, BattleAction action) { ... }
}
```

That single change is what makes the same engine serve two different turn
shapes without any branching inside the engine itself:

- **PvE** (`CombatScreen`): hero acts → if `ONGOING`, immediately resolve
  one automatic `enemy.resolve(ATTACK)` reply, in the same method call.
- **PvP** (`PvPMatch`): a player acts → if `ONGOING`, flip `currentTurn`
  and wait for a `PvPActionPacket` instead of resolving anything
  automatically.

Outcomes from `resolve()` are always relative to whichever combatant is
passed as `attacker`: `VICTORY` means the *defender* just died,
`ESCAPED` means the *attacker* fled (in PvP, this is a surrender — the
caller attributes the win to the other player), `ONGOING` means keep
going. Both callers (`CombatScreen` and `PvPMatch`) do their own
attacker-relative → screen-relative translation; the engine itself never
needs to know who's "the player."

`GUARD` is now stateful across two separate `resolve()` calls instead of
one combined call, via `Combatant.isGuarding()/setGuarding(boolean)` —
a `transient` field on `Hero`/`DungeonEnemy` so it never gets persisted
into a save file (same pattern `GameSession` already uses for its
transient `dungeon` field).

### One deliberate, minor PvE balance change

The old `DungeonEnemy` combat had a bespoke 18%-chance "heavy strike"
that added a flat +3 damage, separate from the hero's critical-hit
mechanic. Unifying both sides onto one `Combatant.criticalChance()` /
`1.6x` critical-multiplier path means `DungeonEnemy.criticalChance()`
now returns 18 and enemy crits use the same 1.6x multiplier heroes use,
instead of the old flat +3. Flagged here and in
`DungeonEnemy.criticalChance()`'s Javadoc so it isn't mysterious later.

---

## 4. Networking notes (`PvPConnection`, `PvPServer`, `PvPClient`)

### The `ObjectOutputStream`/`ObjectInputStream` header-order rule

`ObjectOutputStream`'s constructor writes a small stream header
immediately. `ObjectInputStream`'s constructor *blocks* until it has read
the peer's header. If both ends of a socket construct their
`ObjectInputStream` first, they deadlock waiting on each other's header.
`PvPConnection`'s constructor always creates and flushes the output
stream before creating the input stream, on both the host and guest side,
which is exactly why both `PvPServer` and `PvPClient` go through this one
class instead of each managing raw streams themselves. Don't "simplify"
this away.

### One accepted guest at a time

`PvPServer.acceptGuest` rejects (closes immediately) a second incoming
connection while one is already open, rather than silently replacing the
first. This matters more here than it did with KryoNet, which handled
one-server-many-connections natively; plain `ServerSocket.accept()` will
happily hand you a second socket while the first is still live, so the
guard is explicit in this version.

### Stream `reset()` after every send

`PvPConnection.send` calls `out.reset()` after every `writeObject`/
`flush`. `ObjectOutputStream` caches object identities it has already
written so it can send back-references instead of re-serializing shared
objects; without periodic resets that cache grows for the lifetime of the
connection. Since every payload sent here (`PvPBattleState`, action
packets) is a freshly constructed record rather than a mutated,
previously-sent instance, this isn't fixing a correctness bug so much as
avoiding unbounded cache growth over a long match — verified in
`PvPConnectionTest.repeatedSendsSurviveTheStreamResetAfterEachOne`.

### Threads

Two threads live outside the render thread on each side:

- **Host**: one `pvp-server-accept` daemon thread blocked in
  `ServerSocket.accept()`, and (once a guest connects) one
  `pvp-connection-reader` daemon thread blocked in `readObject()`.
- **Guest**: one `pvp-client-reconnect` daemon thread (a
  `ScheduledExecutorService`) that calls `Socket.connect(...)` on a fixed
  schedule, and (once connected) its own `pvp-connection-reader` thread.

None of these ever call a libGDX/Scene2D/GL API directly — see the
threading diagram in §9.

---

## 5. Integration plan (`ProjectEnigmaGame` lifecycle)

Unaffected by the networking pivot — `PvPServer`/`PvPClient`'s public API
(constructor, `EventListener` interfaces, method names) is identical to
the previous revision, so this section, and `ProjectEnigmaGame`/
`PvPCombatScreen`/`MultiplayerMenuScreen`/`ClassSelectScreen` themselves,
did not need to change at all.

`ProjectEnigmaGame` owns `PvPServer`/`PvPClient` the same way it already owns
`SaveService`/`GameSession` — a long-lived field, started/stopped
explicitly, with screens reading from it rather than owning it. It also
*is* the `PvPServer.EventListener`/`PvPClient.EventListener` during the
"lobby" part of the flow (waiting for a connection, waiting for both
class picks), because that spans several screens and no single screen
owns the whole thing. Once the match actually starts, `PvPCombatScreen`
installs *itself* as the listener (`pvpServer.setListener(screen)` /
`pvpClient.setListener(screen)`) for the duration of the fight — see
`ProjectEnigmaGame.tryBeginPvPMatchAsHost()` / `enterPvPCombatAsGuest()`.

```
MenuScreen "Multiplayer"
  → game.showMultiplayerMenu()
      → MultiplayerMenuScreen

Host path:
  MultiplayerMenuScreen "Host"
    → game.hostPvPMatch()                (binds PvPServer, ProjectEnigmaGame becomes listener)
    → screen shows "Hosting on port N, waiting..."
  [guest connects]
    → PvPServer.EventListener.onGuestConnected(false)
    → game.showPvPClassSelect()           (ClassSelectScreen w/ PvP callbacks)
  [host confirms a class]
    → game.onPvPClassSelected(class)      (stores pvpLocalHero)
  [guest's PvPClassSelectPacket arrives, any time before or after the above]
    → PvPServer.EventListener.onClassSelected(class) (stores pvpRemoteClassPending)
  [once both are known, tryBeginPvPMatchAsHost() fires exactly once]
    → new PvPMatch(hostHero, guestHero, seed)
    → PvPCombatScreen.forHost(...)         (installs itself as PvPServer listener)
    → broadcast the initial PvPBattleState
    → switchScreen(...)

Guest path:
  MultiplayerMenuScreen "Join" → type IP → Enter
    → game.joinPvPMatch(ip)                (creates PvPClient, ProjectEnigmaGame becomes listener)
    → screen shows "Connecting..."
  [socket connects]
    → PvPClient.EventListener.onConnected(false)
    → game.showPvPClassSelect()
  [guest confirms a class]
    → game.onPvPClassSelected(class) → pvpClient.sendClassSelection(class)
    → guest now just waits on ClassSelectScreen
  [host's first PvPBattleState broadcast arrives]
    → PvPClient.EventListener.onStateReceived(state)
    → game.enterPvPCombatAsGuest(state)
    → PvPCombatScreen.forGuest(...)        (installs itself as PvPClient listener)
    → switchScreen(...)

Either side, from PvPCombatScreen:
  action submitted → PvPMatch.applyAction (host) or PvPClient.sendAction (guest)
  match ends → PvPCombatScreen shows result, Enter/Esc → game.leavePvPMatch()
    → stopPvPNetworking() (closes PvPServer/PvPClient, clears all PvP fields)
    → showMenu()
```

`ProjectEnigmaGame.dispose()` also calls `stopPvPNetworking()`, so closing
the window mid-match cleanly stops the embedded server/client instead of
leaking a bound port or a background thread.

---

## 6. Step-by-step setup (Linux)

1. **Drop in the files.** Copy every file from `core/src/main/java/...`
   and `core/src/test/java/...` in this archive over the matching paths
   in your project (this includes deleting the old
   `network/NetworkRegistration.java` if it's still there from a previous
   revision), and replace `lwjgl3/src/main/java/.../Lwjgl3Launcher.java`
   and `core/build.gradle`.

2. **Build.**
   ```bash
   ./gradlew build
   ```
   No new dependency to resolve this time.

3. **Run the tests**, including the new socket-based ones:
   ```bash
   ./gradlew test
   ```
   `PvPConnectionTest` and `PvPEndToEndTest` bind real sockets on
   `127.0.0.1`; they don't need network access, just loopback.

4. **Run the pure-logic self-test** (fast, no libGDX needed):
   ```bash
   ./verify-logic.sh
   ```

5. **Run a host and a guest on one machine**, two terminals:
   ```bash
   # Terminal 1 — host
   ./gradlew lwjgl3:run --args="--host"

   # Terminal 2 — guest
   ./gradlew lwjgl3:run --args="--connect 127.0.0.1"
   ```
   Both windows should land on PvP class select within a second or two.
   Pick classes on both, and you should both land on `PvPCombatScreen`.

6. **Run across two machines on the same LAN**: same as above, but the
   guest's `--connect` argument (or the IP typed into `MultiplayerMenuScreen`)
   is the host machine's LAN IP (`ip addr` / `hostname -I` on Linux),
   and the host's TCP port (`54777` by default,
   `ProjectEnigmaGame.PVP_DEFAULT_PORT`) needs to be reachable — open it in
   any local firewall (e.g. `sudo ufw allow 54777/tcp` on Ubuntu) if the
   guest can't connect.

7. **Test the reconnect flow**: start a match, then kill the guest
   process (Ctrl+C) mid-fight and relaunch it with the same
   `--connect <host-ip>` — it should retry every 3 seconds and resume
   the paused match once it reconnects. Kill the *host* instead to see
   the guest sit in "Connecting..." retrying (see "Omitted features" for
   what happens if you then quit the host for good instead of
   restarting it).

### Windows

Identical, using `gradlew.bat` from Command Prompt or PowerShell:

```bat
gradlew.bat lwjgl3:run --args="--host"
gradlew.bat lwjgl3:run --args="--connect 127.0.0.1"
```

For a cross-machine test on Windows, allow the port through Windows
Defender Firewall (Settings → Network → Windows Firewall → Advanced
settings → Inbound Rules → New Rule → Port → TCP `54777`). Nothing in the
core/network code is platform-specific — `java.net.Socket`/
`ServerSocket` behave the same on both OSes, and `Lwjgl3Launcher` is the
only place that ever sees a `String[] args`.

---

## 7. Extensibility map

**Phase 1 only — deliberately not generalized further:**
- Exactly one guest connection per `PvPServer` (no room for a third
  player, no matchmaking, no lobby list) — now an explicit check in
  `acceptGuest`, not just an implicit consequence of a library's shape.
- A single hardcoded arena/screen (`PvPCombatScreen`) with no map choice.
- Player 0/1 role is fixed at "host = 0, guest = 1" for the whole match;
  no seat-swapping or spectators.
- No persistence: a PvP hero exists only for the duration of one match
  and is discarded — it never touches `GameSession`/`SaveService`.
- Reconnection assumes good faith: the host doesn't verify a reconnecting
  peer is *the same* guest (see "Omitted features" below).

**Designed to extend without a rewrite:**
- `Combatant` + `BattleEngine.resolve(attacker, defender, action)` is
  already generic over "who is fighting whom" — a co-op boss fight is
  "N combatants take turns against one `DungeonEnemy`," which is a
  `PvPMatch`-shaped controller, not a `BattleEngine` change.
- `PvPMatch` never touches the network — a future dedicated server can
  reuse it verbatim, feeding it actions from N real network connections
  instead of "one local call + one network callback." The host/guest
  asymmetry lives entirely in `PvPServer`/`PvPClient`/`PvPCombatScreen`,
  not in `PvPMatch`.
- `PvPConnection` is the one place the transport lives. If plain
  serialization ever becomes a real bottleneck (unlikely for a
  turn-based game) or a real security concern (e.g. exposing this beyond
  a trusted LAN, where Java's `ObjectInputStream.readObject()` on
  unvalidated input is a known deserialization-attack surface —
  explicitly out of scope for a LAN-only Phase 1, but worth remembering
  before ever exposing this to the open internet), swapping in a
  different wire format is a change confined to this one class plus the
  `send`/`readObject` calls around it — `PvPServer`/`PvPClient`'s public
  API wouldn't need to change again, the same way it didn't need to
  change during this revision's actual transport swap.
- `core`'s networking code has zero platform (`lwjgl3`) dependencies —
  only `Lwjgl3Launcher` knows about `String[] args`; a future Android or
  iOS launcher supplies host/IP some other way (a UI prompt, most likely)
  and calls the exact same `ProjectEnigmaGame.hostPvPMatch()`/`joinPvPMatch()`.
- `HeroSnapshot` already only exposes what a client should be able to
  see — extending it for spectators or a replay log is additive, not a
  redesign.

---

## 8. Explicit list of omitted features

- No dedicated server, matchmaking, lobby list, or authentication.
- No cloud saves, chat, rematch button, or spectating.
- No custom player names — the PvP HUD labels each side by class
  (`Hero.displayName()` returns `heroClass.displayName()`).
- No mid-turn abandon: a player can surrender via **Run** on their own
  turn, or abandon outright while the match is `WAITING_FOR_RECONNECT`
  (or, for the guest specifically, while its own connection has just
  dropped locally, even before it's received a broadcast saying so —
  `PvPCombatScreen` tracks that with its own `disconnectedLocally` flag
  rather than waiting on a status update it might never receive). They
  cannot instantly quit while it's the opponent's turn and the
  connection is healthy — a deliberate Phase 1 UX simplification.
- **No reconnection identity check.** Phase 1 has no authentication at
  all, so the host accepts *any* new TCP connection during
  `WAITING_FOR_RECONNECT` as "the guest is back." On an open LAN port, a
  third party could theoretically hijack a paused match.
- **If the host abandons while the guest is disconnected**, the guest
  won't receive a clean "match abandoned" message — `leavePvPMatch()`
  closes the `PvPServer` outright, so a guest mid-reconnect will just
  keep retrying and failing until the player gives up and presses Esc.
- No rate limiting, encryption, or packet-size limits, and (see §7)
  plain `ObjectInputStream.readObject()` trusts whatever bytes arrive on
  the socket — all fine for a LAN Phase 1 between two consenting
  players, not fine to expose past that without hardening.
- **Race-to-PvP specific (this revision):**
  - Exploration duration is chosen per-host from a fixed preset list on
    `MultiplayerMenuScreen` (30s-10m) — no free-form/custom value entry,
    and only the host's choice is used (the guest's own selector, if they
    also toggle Race Mode before joining, is ignored).
  - A disconnect during exploration or the ready/transition window gets
    only a log line, not a UI notice — the remaining player just keeps
    exploring/waiting indefinitely until they manually press Esc /
    "Abandon Race." There is no "solo continuation," auto-win, or
    reconnect-into-an-in-progress-exploration path.
  - Best-of-one: reaching PvP always plays exactly one `PvPMatch` fight
    (whatever the existing PvP flow already does), never a round series.
  - `HeroLoadout`'s anti-cheat clamp (see its Javadoc) is a sanity
    ceiling, not a real economy-balance check — it stops "999999 attack,"
    not "slightly-too-generous-for-this-exact-seed" claims. Good enough
    for two consenting LAN players, not for anything adversarial.

---

## 10. Race-to-PvP

Two players each get their own independent single-player exploration
phase — same `GameSession`/`DungeonScreen`/`BattleEngine` rules as normal
single-player — for a fixed duration, synchronized by a host-owned
countdown, and then converge into the *existing* PvP flow (§1-9,
unchanged) using the heroes they actually grew instead of fresh
level-1s. Built entirely on top of the machinery above: no parallel
`PvPMatch`, no parallel `PvPCombatScreen`, no parallel dungeon logic.

### State machine (per player)

```
NONE --(both classes picked)--> EXPLORING --(exploration ends)--> WAITING_FOR_PVP --(both sides ready)--> [PvPCombatScreen, existing flow]
```

- Entered from `MultiplayerMenuScreen`'s new "Race Mode: ON/OFF" toggle,
  which changes what the existing Host/Join buttons do
  (`hostRaceMatch()`/`joinRaceMatch()` vs. `hostPvPMatch()`/`joinPvPMatch()`)
  rather than duplicating a second pair of buttons for a mode that only
  changes what happens *after* the connection is made.
- Class selection reuses `ClassSelectScreen`/`PvPClassSelectPacket`
  unchanged (a new title-only constructor overload distinguishes the
  screen's wording; `isPvP()`'s "Ready" vs. "Begin" button-label check
  still matches because the new title also contains "PVP").
- **EXPLORING → WAITING_FOR_PVP** is triggered by:
  - the host's own local countdown reaching zero (host is always
    authoritative for this transition — a guest's local clock never
    ends its own exploration, see `RaceTimerSyncPacket`'s Javadoc), or
  - the guest receiving a `RaceTimerSyncPacket` with `secondsRemaining <= 0`, or
  - either player's hero dying in a regular single-player fight during
    exploration (`ProjectEnigmaGame.showGameOver()` special-cases this —
    see its Javadoc — instead of showing `GameOverScreen`, which has no
    "New Run" that makes sense mid-race and would otherwise wrongly call
    `SaveService.deleteSave()` against the player's real save).
- The host keeps ticking/broadcasting the shared countdown
  (`raceTimerActive`) even after its **own** exploration ends early
  (e.g. its hero died) — the guest is still entitled to the full
  duration, so "am I personally still exploring" (`raceState`) and "is
  the host still the shared timekeeper" (`raceTimerActive`) are
  deliberately separate flags.
- **WAITING_FOR_PVP → PvP** happens once the host has both its own
  finished exploration and the guest's `ReadyForPvPPacket` (in either
  order — `tryBeginRaceMatch()` is a no-op until both are present), at
  which point the host builds a real `PvPMatch` from `session.hero` (its
  own, actually-progressed `Hero` object) and `HeroLoadout.toHero()`
  (the guest's, reconstructed and sanity-clamped) and hands off to
  `PvPCombatScreen.forHost`/`forGuest` exactly as classic PvP does.

### New packets (`network/`, all `record`s, `implements Serializable`)

| Packet | Direction | Purpose |
|---|---|---|
| `RaceStartPacket(dungeonSeed, durationSeconds)` | host → guest | Sent once, right after both classes are picked. Same seed both sides so the dungeon *layout* is identical (fair), even though each player's session (enemies killed, chests opened, position) is independently mutable from that point on. |
| `RaceTimerSyncPacket(secondsRemaining)` | host → guest | Periodic (every 5s) drift correction for the guest's locally-ticked display countdown, **and** — when `secondsRemaining <= 0` — the sole authoritative signal that ends the guest's exploration. Doubles as both to avoid a fourth packet type. |
| `ReadyForPvPPacket(HeroLoadout)` | guest → host | Sent once, when the guest's exploration ends. |
| `HeroLoadout(heroClass, level, maxHealth, health, maxMana, mana, attack, defense, potions)` | (payload, not sent standalone) | The guest's actual grown hero. See below for why this isn't just `HeroSnapshot`. |

`PvPServer.sendToGuest(Object)` is a new generic one-shot send that
`broadcast(PvPBattleState)` now delegates to, shared by `RaceStartPacket`/
`RaceTimerSyncPacket` since neither has (or needs) a battle-state shape of
its own. `PvPServer.EventListener.onReadyForPvp` and
`PvPClient.EventListener.onRaceStart`/`onRaceTimerSync` are **default
no-op methods**, specifically so `PvPCombatScreen` and classic PvP's
lobby listener in `ProjectEnigmaGame` never had to change to keep
implementing these interfaces.

### Why `HeroLoadout` isn't `HeroSnapshot`

`HeroSnapshot` is a live, once-per-turn, display-only broadcast the host
sends for the duration of an *already-running* match, and it deliberately
omits `attack`/`defense`/`potions` so a client never has the fields it'd
need to fake damage math — that trust decision repeats every turn.
`HeroLoadout` is sent exactly once, *before any match exists*, describing
progress the guest legitimately earned in its own single-player run —
there's no repeated trust decision, only a one-time transfer, which
`HeroLoadout.toHero()` sanity-clamps against what any legitimate level-up
chain could produce (see its Javadoc) rather than trusting it blindly.

### Threading

No new threads, no new synchronization primitives. `RaceStartPacket`/
`RaceTimerSyncPacket`/`ReadyForPvPPacket` flow through the exact same
`ConcurrentLinkedQueue` + `Gdx.app.postRunnable` path described in §9 —
`PvPServer`/`PvPClient`'s connection-reader threads only ever enqueue a
`Runnable` and post it to the render thread; every line of new game logic
in `ProjectEnigmaGame` (`tickRaceTimer`, `completeExplorationAndSendReady`,
`tryBeginRaceMatch`, etc.) runs there, same as the rest of the PvP
lifecycle. The one new per-frame cost is `tickRaceTimer(delta)`, called
from `ProjectEnigmaGame.render()` alongside the existing `sounds.update()`
call — a handful of float comparisons and, at most once every 5 seconds,
one `PvPConnection.send()` — not measurable against a frame budget.
`DungeonScreen`'s race-mode timer bar reuses a single cached `Color`
field (`raceTimerColor`) instead of allocating one per frame for the
low-time flash effect, matching the no-per-frame-allocation discipline
`SoundEffects`/`UtopiaAssets` already follow elsewhere in this codebase.

### Save-file safety

A Race-to-PvP `GameSession` never touches `SaveService`. This is enforced
in two places on purpose, not one:
- `DungeonScreen`'s own call sites skip `game.saveGame()` when
  `raceMode` is true (chest loot, periodic autosave, potion use, next
  floor).
- `ProjectEnigmaGame.saveGame()` itself additionally no-ops whenever
  `raceSessionActive` is true, which also covers the paths that call it
  *unconditionally* regardless of mode — `quit()` and `dispose()` (window
  close) — that a screen-level guard alone would have missed. Race mode's
  pause menu also swaps in `{"Resume", "Abandon Race"}` instead of the
  normal `{"Resume", "Save Game", "Main Menu", "Quit"}`, since "Main
  Menu"'s `abandonToMenu()` would otherwise still try to save the race
  session under the real save's name.

### Known limitations (Phase 1, this revision)

See §8's new "Race-to-PvP specific" bullets: no UI notice for a
mid-exploration disconnect, always best-of-one once PvP starts, and a
generous-but-not-tight anti-cheat clamp on the transferred hero.

### Follow-up fixes made after initial review

- **Configurable duration.** `MultiplayerMenuScreen` now has a "-"/"+"
  duration selector (30s to 10m presets, mouse or Left/Right while the
  Race Mode row is selected) instead of a code-only fixed value. Only the
  *host's* choice matters — it's carried to the guest inside
  `RaceStartPacket`; `ProjectEnigmaGame.RACE_DEFAULT_DURATION_SECONDS`
  (180s) remains the fallback for classic PvP and the menu's default
  selection.
- **Guest-side race-state reset on entering combat.** `enterPvPCombatAsGuest()`
  didn't reset `raceState` back to `NONE` (only the host side did, inside
  `tryBeginRaceMatch()`) — harmless in isolation, but a real gap once a
  resend safety net (below) needed to know when to stop.
- **Ready-packet resend safety net.** A guest stuck in `WAITING_FOR_PVP`
  now re-sends its `ReadyForPvPPacket` every 2 seconds
  (`tickRaceReadyResend`) until the match actually starts. The handshake
  guard itself was re-audited and traced end-to-end for both possible
  orderings (host confirms/finishes first vs. guest first) and found to
  be correctly gated in every case (`tryBeginRaceMatch()` cannot proceed
  without both `raceHostFinished` and a non-null `raceGuestLoadout`) —
  this resend exists as defense-in-depth against anything not caught by
  that trace (a live TCP connection shouldn't lose a message, but this
  makes the one-shot handshake self-healing rather than a single point of
  failure with no recovery). It's fully idempotent on the host: a
  duplicate arriving before the match exists just re-confirms the same
  loadout; one arriving after lands on `PvPCombatScreen`'s default no-op
  `onReadyForPvp` and is ignored.

---

## 9. Threading diagram

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│  Render thread (GL thread)  │        │  pvp-server-accept  (host)    │
│  ──────────────────────────  │        │  pvp-connection-reader (both) │
│  ProjectEnigmaGame.render()     │        │                                │
│    → active Screen.render()  │        │  Blocking calls only:          │
│                               │        │    ServerSocket.accept()       │
│  PvPCombatScreen.render():    │        │    ObjectInputStream.read-     │
│    - input handling           │        │      Object() (one per        │
│    - submitAction(action)     │        │      connection)               │
│        host: PvPMatch.apply-  │        │  NOT a busy-wait -- both       │
│          Action() directly,   │        │  block the OS thread until      │
│          then broadcast()     │        │  something actually happens.    │
│        guest: PvPClient.      │        │                                │
│          sendAction() (safe   │        │  On receipt / accept / close:  │
│          from any thread —    │◄───────┤    NEVER touches Hero,         │
│          PvPConnection.send   │ queue+ │    PvPMatch, or any Scene2D/    │
│          is synchronized)     │postRun-│    GL state directly. Instead,  │
│    - drawing                  │ nable  │    exactly two things:          │
│                               │        │      1. incoming.add(runnable)  │
│  Screen constructors call     │        │         (ConcurrentLinkedQueue, │
│  pvpServer()/pvpClient()      │        │          thread-safe, no Gdx    │
│    .setListener(this) so      │        │          calls)                 │
│    EventListener callbacks     │        │      2. Gdx.app.postRunnable(  │
│    are dispatched here.        │        │           this::drainIncoming) │
└───────────────┬───────────────┘        └────────────────┬───────────────┘
                │                                          │
                │   libGDX's own Application runnable      │
                │   queue guarantees postRunnable tasks     │
                │   execute on the render thread before      │
                │   the next frame, regardless of which      │
                │   Screen is currently active.               │
                └──────────────────────────────────────────┘

  PvPClient also owns one extra background thread:
    "pvp-client-reconnect" (single-thread ScheduledExecutorService, daemon)
      → every 3s (0s for the very first attempt): new Socket().connect(...)
      → runs entirely off the render thread; success is reported back
        via the same queue+postRunnable path as any other network event
      → never touches PvPMatch/Hero/GL state itself
```

**The rule, restated simply:** network threads only ever do two things —
enqueue a `Runnable`, then `Gdx.app.postRunnable(this::drainIncoming)`.
Every `PvPServer.EventListener`/`PvPClient.EventListener` method, and
therefore everything a screen does inside them (mutate `PvPMatch`, call
`switchScreen`, touch Scene2D/GL resources), always runs on the render
thread. `PvPMatch.applyAction`/etc. are additionally `synchronized`, and
`PvPConnection.send` is `synchronized` too, both of which cost nothing
measurable for two players and remove entire classes of races for free —
belt-and-suspenders on top of the queue, not a substitute for it.
