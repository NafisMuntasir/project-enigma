package com.projectenigma;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.projectenigma.audio.SoundEffects;
import com.projectenigma.audio.SoundCue;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.projectenigma.model.DungeonEnemy;
import com.projectenigma.model.GameSession;
import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;
import com.projectenigma.network.HeroLoadout;
import com.projectenigma.network.PvPBattleState;
import com.projectenigma.network.PvPClient;
import com.projectenigma.network.PvPMatch;
import com.projectenigma.network.PvPServer;
import com.projectenigma.network.RaceStartPacket;
import com.projectenigma.network.RaceTimerSyncPacket;
import com.projectenigma.screen.ClassSelectScreen;
import com.projectenigma.screen.CombatScreen;
import com.projectenigma.screen.DungeonScreen;
import com.projectenigma.screen.GameOverScreen;
import com.projectenigma.screen.MenuScreen;
import com.projectenigma.screen.MultiplayerMenuScreen;
import com.projectenigma.screen.PvPCombatScreen;
import com.projectenigma.screen.SpritePreviewScreen;
import com.projectenigma.screen.EnemyPreviewScreen;

import java.io.IOException;

public final class ProjectEnigmaGame extends Game {
    public static final String DISPLAY_NAME = "PROJECT Enigma";

    /** Phase 1 default TCP port for the embedded PvP host. No discovery/matchmaking -- the guest is told this out of band. */
    public static final int PVP_DEFAULT_PORT = 54777;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private UtopiaAssets assets;
    private BitmapFont font;
    private BitmapFont mediumFont;
    private BitmapFont titleFont;
    private SaveService saveService;
    private GameSession session;
    private final SoundEffects sounds = new SoundEffects();

    // ---- PvP (Phase 1: LAN, one guest, no persistence across app restarts) ----
    private PvPServer pvpServer;
    private PvPClient pvpClient;
    private boolean pvpHosting;
    private Hero pvpLocalHero;
    private HeroClass pvpRemoteClassPending;
    private PvPMatch pvpMatch;

    // ---- Race-to-PvP (built on top of the PvP lobby/networking above) ----
    // Two players explore their own independent GameSession using the
    // existing DungeonScreen/single-player rules for a fixed duration, then
    // converge into the existing PvPMatch/PvPCombatScreen using the heroes
    // they actually grew. See DESIGN.md's "Race-to-PvP" section for the
    // full state-machine diagram and rationale.
    /** Default exploration length. Not exposed in the UI yet -- see DESIGN.md for why a fixed value was chosen for Phase 1. */
    public static final int RACE_DEFAULT_DURATION_SECONDS = 180;
    private static final float RACE_TIMER_SYNC_INTERVAL_SECONDS = 5f;

    private enum RaceState { NONE, EXPLORING, WAITING_FOR_PVP }
    private RaceState raceState = RaceState.NONE;
    private boolean raceModeRequested;
    /** True from the moment a race GameSession is created until stopPvPNetworking() tears the whole race/PvP flow down. Lets cleanup null out `session` only when it actually belongs to a race, never a real single-player run. */
    private boolean raceSessionActive;
    private HeroClass raceLocalHeroClass;
    private int raceDurationSeconds = RACE_DEFAULT_DURATION_SECONDS;
    private float raceSecondsRemaining;
    private float raceSyncCountdown;
    private boolean raceHostFinished;
    private HeroLoadout raceGuestLoadout;
    /** Host-only: whether tickRaceTimer should still tick/broadcast. Deliberately independent of raceState -- if the host's own hero dies early, raceState moves to WAITING_FOR_PVP for the host, but the guest still deserves their full exploration time, so the host keeps timekeeping until the shared duration truly elapses. */
    private boolean raceTimerActive;
    /**
     * Guest-only safety net: how long until the guest re-sends its
     * ReadyForPvPPacket while still stuck in WAITING_FOR_PVP. A live TCP
     * connection shouldn't lose a message, but this makes the one-shot
     * "I'm ready" handshake self-healing against anything else that could
     * go wrong (e.g. the packet arriving before the host had finished
     * setting up, a dropped/late postRunnable) rather than leaving the
     * guest stuck forever with no recovery path.
     */
    private static final float RACE_READY_RESEND_INTERVAL_SECONDS = 2f;
    private float raceReadyResendCountdown;

    private final String[] launchArgs;

    /** Normal entry point: no command-line PvP shortcuts. */
    public ProjectEnigmaGame() {
        this(new String[0]);
    }

    /**
     * @param launchArgs forwarded from {@code Lwjgl3Launcher}. Recognizes
     *                   {@code --host} and {@code --connect <ip>} as manual
     *                   testing shortcuts (see DESIGN.md, "Setup
     *                   instructions") -- everything else is ignored.
     */
    public ProjectEnigmaGame(String[] launchArgs) {
        this.launchArgs = launchArgs == null ? new String[0] : launchArgs;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        assets = new UtopiaAssets();
        sounds.load();
        font = new BitmapFont();
        mediumFont = new BitmapFont();
        titleFont = new BitmapFont();
        font.getData().setScale(1.05f);
        mediumFont.getData().setScale(1.45f);
        titleFont.getData().setScale(2.35f);
        saveService = new SaveService();
        showMenu();
        applyLaunchArgs();
    }

    private void applyLaunchArgs() {
        for (int i = 0; i < launchArgs.length; i++) {
            if ("--enemy-preview".equals(launchArgs[i])) {
                switchScreen(new EnemyPreviewScreen(this));
                return;
            }
            if ("--sprite-preview".equals(launchArgs[i])) {
                switchScreen(new SpritePreviewScreen(this));
                return;
            }
            if ("--host".equals(launchArgs[i])) {
                switchScreen(MultiplayerMenuScreen.autoHost(this));
                return;
            }
            if ("--connect".equals(launchArgs[i]) && i + 1 < launchArgs.length) {
                switchScreen(MultiplayerMenuScreen.autoJoin(this, launchArgs[i + 1]));
                return;
            }
        }
    }

    public void showMenu() {
        switchScreen(new MenuScreen(this));
    }

    public void showOperativePreview() {
        switchScreen(new SpritePreviewScreen(this));
    }

    public void showClassSelect() {
        switchScreen(new ClassSelectScreen(this));
    }

    public void startNewGame(HeroClass heroClass) {
        long seed = System.currentTimeMillis() ^ System.nanoTime();
        session = new GameSession(seed, heroClass);
        saveService.save(session);
        showDungeon();
    }

    public boolean continueGame() {
        GameSession loaded = saveService.load();
        if (loaded == null) {
            return false;
        }
        session = loaded;
        showDungeon();
        return true;
    }

    public void showDungeon() {
        if (session == null) {
            showMenu();
            return;
        }
        // CombatScreen (single-player, unaware of Race mode) also calls this
        // to return from a fight -- passing raceSessionActive through keeps
        // the timer HUD, race-only pause options, and waiting-for-opponent
        // overlay intact instead of silently reverting to a classic screen.
        switchScreen(new DungeonScreen(this, session, raceSessionActive));
    }

    public void startCombat(DungeonEnemy enemy) {
        if (session == null || enemy == null) {
            return;
        }
        switchScreen(new CombatScreen(this, session, enemy));
        sounds.play(SoundCue.ENCOUNTER);
    }

    public void showGameOver() {
        if (raceSessionActive) {
            // A Race-to-PvP exploration-phase death has no "New Run" to
            // offer -- there is no single-player run to restart, only a
            // clock synced with an opponent -- and GameOverScreen's normal
            // path would also wrongly call saveService.deleteSave() against
            // the player's real save file, which this session never
            // touched. Treat it the same as the exploration timer expiring
            // early for this player: submit whatever the hero was at the
            // moment of death (HeroLoadout.toHero() clamps health to at
            // least 1, so a 0-HP hero doesn't hand PvPMatch an already-dead
            // Combatant) and show the waiting-for-opponent state.
            completeExplorationAndSendReady();
            if (raceState != RaceState.NONE) {
                // Still waiting on the other player -- if both were already
                // ready, completeExplorationAndSendReady() above already
                // switched to PvPCombatScreen and raceState is NONE, so
                // this only runs when there's still something to wait for.
                showDungeon();
            }
            return;
        }
        saveService.deleteSave();
        switchScreen(new GameOverScreen(this));
    }

    public void deleteCurrentRunSave() { if (!raceSessionActive) saveService.deleteSave(); }

    private boolean raceEndRequested;
    public void progressionSelectionCompleted() {
        if (raceEndRequested && session != null && session.hero.progression().pendingChoices == 0)
            completeExplorationAndSendReady();
    }

    public void saveGame() {
        if (raceSessionActive) {
            // A Race-to-PvP exploration session is never the player's real
            // save and must never overwrite it. DungeonScreen's own call
            // sites already skip calling this in race mode, but guarding
            // here too covers paths that call saveGame() unconditionally
            // regardless of mode -- quit() and dispose() (window close),
            // in particular.
            return;
        }
        saveService.save(session);
    }

    public void quit() {
        saveGame();
        Gdx.app.exit();
    }

    public void abandonToMenu() {
        saveGame();
        session = null;
        showMenu();
    }

    // ======================================================================
    // PvP lifecycle
    //
    // ProjectEnigmaGame is the PvP listener during the "lobby" part of the flow
    // (waiting for a connection, waiting for both class picks) because that
    // spans several screens and nobody screen owns the whole thing. Once a
    // match actually begins, PvPCombatScreen takes over as the listener for
    // its own duration -- see beginPvPMatchAsHost()/enterPvPCombatAsGuest().
    // ======================================================================

    public void showMultiplayerMenu() {
        stopPvPNetworking();
        switchScreen(new MultiplayerMenuScreen(this));
    }

    /** Starts hosting classic (immediate) PvP. Throws so the caller (MultiplayerMenuScreen) can show a message instead of crashing. */
    public void hostPvPMatch() throws IOException {
        beginHosting(false, RACE_DEFAULT_DURATION_SECONDS);
    }

    /**
     * Starts hosting a Race-to-PvP match: same lobby/connection as {@link
     * #hostPvPMatch()}, different post-class-select flow.
     *
     * @param durationSeconds exploration length chosen on {@code
     *                        MultiplayerMenuScreen}'s duration selector;
     *                        clamped to a sane floor here regardless of
     *                        caller.
     */
    public void hostRaceMatch(int durationSeconds) throws IOException {
        beginHosting(true, durationSeconds);
    }

    private void beginHosting(boolean raceMode, int durationSeconds) throws IOException {
        stopPvPNetworking();
        PvPServer server = new PvPServer(PVP_DEFAULT_PORT); // may throw -- leave state untouched until this succeeds
        raceModeRequested = raceMode;
        if (raceMode) {
            raceDurationSeconds = Math.max(15, durationSeconds);
        }
        pvpHosting = true;
        pvpServer = server;
        pvpServer.setListener(new PvPServer.EventListener() {
            @Override
            public void onGuestConnected(boolean isReconnect) {
                if (!isReconnect) {
                    if (raceMode) {
                        showRaceClassSelect();
                    } else {
                        showPvPClassSelect();
                    }
                }
                // A reconnect that happens to land here (before combat ever
                // started) needs no special handling -- there is nothing to
                // resume yet.
            }

            @Override
            public void onGuestDisconnected() {
                // Pre-match (or pre-fight, for Race mode) disconnect: nothing
                // to pause, just keep waiting -- see DESIGN.md's Race-to-PvP
                // "known limitations" for why this stays this simple in
                // Phase 1 (no resume of a half-finished exploration phase).
                Gdx.app.log("ProjectEnigmaGame", "Guest disconnected"
                        + (raceMode ? " during the race." : " before the match started."));
            }

            @Override
            public void onClassSelected(HeroClass guestClass) {
                pvpRemoteClassPending = guestClass;
                if (raceMode) {
                    tryBeginRaceAsHost();
                } else {
                    tryBeginPvPMatchAsHost();
                }
            }

            @Override
            public void onActionReceived(com.projectenigma.model.BattleAction action) {
                // Actions before the match exists are ignored.
            }

            @Override
            public void onAbandon() {
                Gdx.app.log("ProjectEnigmaGame", "Guest abandoned before the match started.");
            }

            @Override
            public void onReadyForPvp(HeroLoadout loadout) {
                if (raceMode) {
                    onGuestReadyForPvpReceived(loadout);
                }
            }
        });
    }

    /** Starts joining classic (immediate) PvP. Non-blocking; connection progress is reported via the client's listener. */
    public void joinPvPMatch(String hostAddress) {
        beginJoining(hostAddress, false);
    }

    /** Starts joining a Race-to-PvP match: same lobby/connection as {@link #joinPvPMatch}, different post-class-select flow. */
    public void joinRaceMatch(String hostAddress) {
        beginJoining(hostAddress, true);
    }

    private void beginJoining(String hostAddress, boolean raceMode) {
        stopPvPNetworking();
        raceModeRequested = raceMode;
        pvpHosting = false;
        pvpClient = new PvPClient();
        pvpClient.setListener(new PvPClient.EventListener() {
            @Override
            public void onConnected(boolean isReconnect) {
                if (!isReconnect) {
                    if (raceMode) {
                        showRaceClassSelect();
                    } else {
                        showPvPClassSelect();
                    }
                }
            }

            @Override
            public void onDisconnected() {
                Gdx.app.log("ProjectEnigmaGame", "Disconnected from host; retrying...");
            }

            @Override
            public void onStateReceived(PvPBattleState state) {
                // The very first state broadcast after both players are
                // ready (classic: both picked a class; Race: both finished
                // exploring) is what moves the guest into the combat screen.
                enterPvPCombatAsGuest(state);
            }

            @Override
            public void onRaceStart(RaceStartPacket packet) {
                    // The host's own Race Mode selection -- not whatever this guest
                    // happened to toggle locally before pressing Join -- is what
                    // actually decides whether this match is a race. A classic host
                    // never sends this packet, so forwarding unconditionally is safe,
                    // and it's what lets a guest who joined through the plain "Join"
                    // button still be pulled into exploration when the host is
                    // running Race Mode.
                    onRaceStartReceived(packet);
            }

            @Override
            public void onRaceTimerSync(RaceTimerSyncPacket packet) {
                    // Same reasoning as onRaceStart -- onRaceTimerSyncReceived already
                    // no-ops unless this client's own raceState is EXPLORING.
                    onRaceTimerSyncReceived(packet);
            }

        });

        pvpClient.connect(hostAddress, PVP_DEFAULT_PORT);
    }

    public void showPvPClassSelect() {
        switchScreen(new ClassSelectScreen(this, this::onPvPClassSelected));
    }

    private void onPvPClassSelected(HeroClass heroClass) {
        pvpLocalHero = new Hero(heroClass);
        if (pvpHosting) {
            tryBeginPvPMatchAsHost();
        } else {
            pvpClient.sendClassSelection(heroClass);
            // Guest now waits on ClassSelectScreen; enterPvPCombatAsGuest()
            // fires the moment the host's first PvPBattleState arrives.
        }
    }

    // ======================================================================
    // Race-to-PvP lifecycle
    //
    // Reuses the exact same ClassSelectScreen/PvPClassSelectPacket handshake
    // as classic PvP above (see onClassSelected/onPvPClassSelected). Once
    // both classes are known, instead of building a PvPMatch immediately,
    // the host hands out a shared dungeon seed and both sides run a normal
    // single-player GameSession/DungeonScreen for raceDurationSeconds. The
    // host's own local countdown (ticked in render() -> tickRaceTimer, never
    // trusted from the guest) is authoritative for when exploration ends;
    // see RaceTimerSyncPacket's Javadoc. Once both sides have submitted a
    // result (the host directly, the guest via ReadyForPvPPacket), the host
    // builds a real PvPMatch from the two *actual* progressed Heroes and
    // hands off to the existing PvPCombatScreen unchanged.
    // ======================================================================

    public void showRaceClassSelect() {
        switchScreen(new ClassSelectScreen(this, this::onRaceClassSelected, "CHOOSE YOUR OPERATIVE FOR THE PVP RACE"));
    }

    private void onRaceClassSelected(HeroClass heroClass) {
        raceLocalHeroClass = heroClass;
        if (pvpHosting) {
            tryBeginRaceAsHost();
        } else {
            pvpClient.sendClassSelection(heroClass);
            // Guest now waits on ClassSelectScreen for the host's RaceStartPacket (see beginJoining's onRaceStart).
        }
    }

    private void tryBeginRaceAsHost() {
        if (raceLocalHeroClass == null || pvpRemoteClassPending == null) {
            return; // still waiting on one side
        }
        long dungeonSeed = System.currentTimeMillis() ^ System.nanoTime();
        GameSession raceSession = new GameSession(dungeonSeed, raceLocalHeroClass);
        beginRaceExploration(raceSession);
        pvpServer.sendToGuest(new RaceStartPacket(dungeonSeed, raceDurationSeconds));
    }

    /**
     * The local hero class for a Race-to-PvP match. Normally set by
     * onRaceClassSelected when this side joined through Race Mode. A guest
     * that instead joined through the classic "Join" button (because only
     * the host toggled Race Mode) picks its class on the classic
     * ClassSelectScreen instead, which records it in pvpLocalHero via
     * onPvPClassSelected -- fall back to that so a host-initiated race still
     * has a class to build the guest's exploration GameSession from.
     */
    private HeroClass resolvedLocalRaceHeroClass() {
            if (raceLocalHeroClass != null) {
                    return raceLocalHeroClass;
            }
            return pvpLocalHero != null ? pvpLocalHero.heroClass : null;
    }

    private void onRaceStartReceived(RaceStartPacket packet) {
            HeroClass localClass = resolvedLocalRaceHeroClass();
            if (localClass == null) {
                    // Should never happen: the host only sends this after it has
                    // already received this client's class selection (see
                    // tryBeginRaceAsHost()), so one of the two fields above must
                    // already be set. Guard anyway rather than handing GameSession
                    // a null class.
                    Gdx.app.log("ProjectEnigmaGame", "Received RaceStartPacket before a local class was chosen; ignoring.");
                    return;
            }
            raceLocalHeroClass = localClass;
            raceDurationSeconds = packet.durationSeconds();
            GameSession raceSession = new GameSession(packet.dungeonSeed(), localClass);
            beginRaceExploration(raceSession);
    }

    private void beginRaceExploration(GameSession raceSession) {
            session = raceSession;
        raceSessionActive = true;
        raceEndRequested = false;
        raceState = RaceState.EXPLORING;
        raceSecondsRemaining = raceDurationSeconds;
        raceSyncCountdown = RACE_TIMER_SYNC_INTERVAL_SECONDS;
        raceHostFinished = false;
        raceGuestLoadout = null;
        raceTimerActive = true; // host-only meaning; harmless (unread) on the guest side
        switchScreen(new DungeonScreen(this, raceSession, true));
    }

    /**
     * Host-only: ticks the authoritative countdown and periodically (and
     * finally) broadcasts it. Called from render(). Deliberately keeps
     * running even after the host's own {@code raceState} has already
     * moved past EXPLORING (e.g. the host's hero died early) -- the guest
     * is still entitled to its full exploration time, so timekeeping duty
     * is independent of whether the host personally is still exploring.
     */
    private void tickRaceTimer(float delta) {
        if (!pvpHosting) {
            if (raceState != RaceState.EXPLORING) {
                return;
            }
            // Guest: purely cosmetic smoothing between RaceTimerSyncPacket
            // corrections. The actual end-of-exploration trigger only ever
            // comes from onRaceTimerSyncReceived, never from this reaching zero.
            raceSecondsRemaining = Math.max(0f, raceSecondsRemaining - delta);
            return;
        }
        if (!raceTimerActive || pvpServer == null) {
            return;
        }
        raceSecondsRemaining = Math.max(0f, raceSecondsRemaining - delta);
        raceSyncCountdown -= delta;
        boolean timeUp = raceSecondsRemaining <= 0f;
        if (timeUp || raceSyncCountdown <= 0f) {
            raceSyncCountdown = RACE_TIMER_SYNC_INTERVAL_SECONDS;
            pvpServer.sendToGuest(new RaceTimerSyncPacket(Math.round(raceSecondsRemaining)));
        }
        if (timeUp) {
            raceTimerActive = false;
            completeExplorationAndSendReady(); // no-op if the host already finished early (see class Javadoc above)
        }
    }

    /** Guest-only: reconciles the local display countdown and, on secondsRemaining <= 0, ends exploration -- see RaceTimerSyncPacket's Javadoc. */
    private void onRaceTimerSyncReceived(RaceTimerSyncPacket packet) {
        if (raceState != RaceState.EXPLORING) {
            return;
        }
        raceSecondsRemaining = packet.secondsRemaining();
        if (packet.secondsRemaining() <= 0) {
            completeExplorationAndSendReady();
        }
    }

    /** Called once, by whichever side's exploration just ended (host: its own timer; guest: the host's broadcast). */
    private void completeExplorationAndSendReady() {
        if (raceState != RaceState.EXPLORING) {
            return;
        }
        raceEndRequested = true;
        if (session.hero.isAlive() && session.hero.progression().pendingChoices > 0) return;
        raceState = RaceState.WAITING_FOR_PVP;
        if (pvpHosting) {
            raceHostFinished = true;
            tryBeginRaceMatch();
        } else if (pvpClient != null) {
            pvpClient.sendReadyForPvp(HeroLoadout.of(session.hero));
            raceReadyResendCountdown = RACE_READY_RESEND_INTERVAL_SECONDS;
        }
    }

    /**
     * Guest-only: while still WAITING_FOR_PVP, periodically re-sends
     * ReadyForPvPPacket as a safety net -- see {@link #RACE_READY_RESEND_INTERVAL_SECONDS}'s
     * Javadoc. Fully idempotent on the host side: a duplicate arriving
     * before the match exists just re-sets the same HeroLoadout and
     * re-checks (harmless no-op if already waiting on the host's own
     * timer); a duplicate arriving after the match exists lands on
     * PvPCombatScreen's default no-op onReadyForPvp and is ignored.
     */
    private void tickRaceReadyResend(float delta) {
        if (pvpHosting || raceState != RaceState.WAITING_FOR_PVP || pvpClient == null || session == null) {
            return;
        }
        raceReadyResendCountdown -= delta;
        if (raceReadyResendCountdown <= 0f) {
            raceReadyResendCountdown = RACE_READY_RESEND_INTERVAL_SECONDS;
            pvpClient.sendReadyForPvp(HeroLoadout.of(session.hero));
        }
    }

    /** Host-only: the guest's one-time post-exploration hero snapshot. */
    private void onGuestReadyForPvpReceived(HeroLoadout loadout) {
        raceGuestLoadout = loadout;
        tryBeginRaceMatch();
    }

    /** Host-only: proceeds only once both sides have reported their exploration result, in either order. */
    private void tryBeginRaceMatch() {
        if (!raceHostFinished || raceGuestLoadout == null) {
            return; // still waiting on one side
        }
        Hero hostHero = session.hero; // the exact, fully-progressed Hero the host just explored with
        Hero guestHero = raceGuestLoadout.toHero();
        long seed = System.currentTimeMillis() ^ System.nanoTime();
        pvpMatch = new PvPMatch(hostHero, guestHero, seed);
        raceState = RaceState.NONE;

        PvPCombatScreen screen = PvPCombatScreen.forHost(this, pvpMatch);
        pvpServer.setListener(screen);
        pvpServer.broadcast(pvpMatch.currentState());
        switchScreen(screen);
    }

    /** Bound to DungeonScreen's race-mode pause menu ("Abandon Race") and its waiting-for-opponent overlay. */
    public void abandonRace() {
        if (!pvpHosting && pvpClient != null) {
            pvpClient.sendAbandon();
        }
        leavePvPMatch();
    }

    public int raceDurationSeconds() {
        return raceDurationSeconds;
    }

    /** Whole seconds remaining in the exploration phase, for the on-screen timer/time-bar. Meaningless (0) outside EXPLORING/WAITING_FOR_PVP. */
    public float raceSecondsRemaining() {
        return raceSecondsRemaining;
    }

    public boolean isRaceWaitingForOpponent() {
        return raceState == RaceState.WAITING_FOR_PVP;
    }

    // ======================================================================

    private void tryBeginPvPMatchAsHost() {
        if (pvpLocalHero == null || pvpRemoteClassPending == null) {
            return; // still waiting on one side
        }
        Hero hostHero = pvpLocalHero;
        Hero guestHero = new Hero(pvpRemoteClassPending);
        long seed = System.currentTimeMillis() ^ System.nanoTime();
        pvpMatch = new PvPMatch(hostHero, guestHero, seed);

        PvPCombatScreen screen = PvPCombatScreen.forHost(this, pvpMatch);
        pvpServer.setListener(screen);
        pvpServer.broadcast(pvpMatch.currentState());
        switchScreen(screen);
    }

    private boolean enteredPvpCombatAsGuest;

    private void enterPvPCombatAsGuest(PvPBattleState firstState) {
        if (enteredPvpCombatAsGuest) {
            return; // subsequent states are handled by PvPCombatScreen itself once installed as listener
        }
        enteredPvpCombatAsGuest = true;
        // Race-to-PvP: the guest side of the WAITING_FOR_PVP state never had
        // its own "we're in combat now" transition (only the host side did,
        // inside tryBeginRaceMatch()) -- reset it here so isRaceWaitingForOpponent()
        // correctly turns false once PvPCombatScreen is actually showing, and so
        // the ready-packet resend safety net below knows to stop.
        raceState = RaceState.NONE;
        PvPCombatScreen screen = PvPCombatScreen.forGuest(this, firstState);
        pvpClient.setListener(screen);
        switchScreen(screen);
    }

    /** Called by PvPCombatScreen when the match ends (win/lose/abandon) and the player confirms. */
    public void leavePvPMatch() {
        stopPvPNetworking();
        showMenu();
    }

    private void stopPvPNetworking() {
        if (pvpServer != null) {
            pvpServer.close();
            pvpServer = null;
        }
        if (pvpClient != null) {
            pvpClient.close();
            pvpClient = null;
        }
        pvpLocalHero = null;
        pvpRemoteClassPending = null;
        pvpMatch = null;
        enteredPvpCombatAsGuest = false;

        raceModeRequested = false;
        raceState = RaceState.NONE;
        raceLocalHeroClass = null;
        raceDurationSeconds = RACE_DEFAULT_DURATION_SECONDS;
        raceSecondsRemaining = 0f;
        raceHostFinished = false;
        raceGuestLoadout = null;
        raceTimerActive = false;
        raceReadyResendCountdown = 0f;
        if (raceSessionActive) {
            // The exploration-phase session never touches SaveService and
            // was never meant to be resumed -- unlike single-player's
            // `session`, which this method must never clear (it's also
            // called just from opening the Multiplayer menu with a paused
            // single-player run still in memory).
            session = null;
            raceSessionActive = false;
        }
    }

    public PvPServer pvpServer() {
        return pvpServer;
    }

    public PvPClient pvpClient() {
        return pvpClient;
    }

    public boolean isPvPHost() {
        return pvpHosting;
    }

    // ======================================================================

    private void switchScreen(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null) {
            previous.dispose();
        }
    }

    public SoundEffects sounds() { return sounds; }

    @Override public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        sounds.update(delta);
        tickRaceTimer(delta);
        tickRaceReadyResend(delta);
        super.render();
    }

    public SpriteBatch batch() {
        return batch;
    }

    public ShapeRenderer shapes() {
        return shapes;
    }

    public UtopiaAssets assets() {
        return assets;
    }

    public BitmapFont font() {
        return font;
    }

    public BitmapFont mediumFont() {
        return mediumFont;
    }

    public BitmapFont titleFont() {
        return titleFont;
    }

    public SaveService saves() {
        return saveService;
    }

    public GameSession session() {
        return session;
    }

    @Override
    public void dispose() {
        saveGame();
        stopPvPNetworking();
        Screen current = getScreen();
        super.dispose();
        if (current != null) {
            current.dispose();
        }
        sounds.dispose();
        assets.dispose();
        batch.dispose();
        shapes.dispose();
        font.dispose();
        mediumFont.dispose();
        titleFont.dispose();
    }
}
