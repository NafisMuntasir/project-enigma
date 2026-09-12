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
import com.projectenigma.network.PvPBattleState;
import com.projectenigma.network.PvPClient;
import com.projectenigma.network.PvPMatch;
import com.projectenigma.network.PvPServer;
import com.projectenigma.screen.ClassSelectScreen;
import com.projectenigma.screen.CombatScreen;
import com.projectenigma.screen.DungeonScreen;
import com.projectenigma.screen.GameOverScreen;
import com.projectenigma.screen.MenuScreen;
import com.projectenigma.screen.MultiplayerMenuScreen;
import com.projectenigma.screen.PvPCombatScreen;

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
        switchScreen(new DungeonScreen(this, session));
    }

    public void startCombat(DungeonEnemy enemy) {
        if (session == null || enemy == null) {
            return;
        }
        switchScreen(new CombatScreen(this, session, enemy));
        sounds.play(SoundCue.ENCOUNTER);
    }

    public void showGameOver() {
        saveService.deleteSave();
        switchScreen(new GameOverScreen(this));
    }

    public void saveGame() {
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

    /** Starts hosting. Throws so the caller (MultiplayerMenuScreen) can show a message instead of crashing. */
    public void hostPvPMatch() throws IOException {
        stopPvPNetworking();
        PvPServer server = new PvPServer(PVP_DEFAULT_PORT); // may throw -- leave state untouched until this succeeds
        pvpHosting = true;
        pvpServer = server;
        pvpServer.setListener(new PvPServer.EventListener() {
            @Override
            public void onGuestConnected(boolean isReconnect) {
                if (!isReconnect) {
                    showPvPClassSelect();
                }
                // A reconnect that happens to land here (before combat ever
                // started) needs no special handling -- there is nothing to
                // resume yet.
            }

            @Override
            public void onGuestDisconnected() {
                // Pre-match disconnect: nothing to pause, just keep waiting.
                Gdx.app.log("ProjectEnigmaGame", "Guest disconnected before the match started.");
            }

            @Override
            public void onClassSelected(HeroClass guestClass) {
                pvpRemoteClassPending = guestClass;
                tryBeginPvPMatchAsHost();
            }

            @Override
            public void onActionReceived(com.projectenigma.model.BattleAction action) {
                // Actions before the match exists are ignored.
            }

            @Override
            public void onAbandon() {
                Gdx.app.log("ProjectEnigmaGame", "Guest abandoned before the match started.");
            }
        });
    }

    /** Starts joining. Non-blocking; connection progress is reported via the client's listener. */
    public void joinPvPMatch(String hostAddress) {
        stopPvPNetworking();
        pvpHosting = false;
        pvpClient = new PvPClient();
        pvpClient.setListener(new PvPClient.EventListener() {
            @Override
            public void onConnected(boolean isReconnect) {
                if (!isReconnect) {
                    showPvPClassSelect();
                }
            }

            @Override
            public void onDisconnected() {
                Gdx.app.log("ProjectEnigmaGame", "Disconnected from host; retrying...");
            }

            @Override
            public void onStateReceived(PvPBattleState state) {
                // The very first state broadcast after both players picked a
                // class is what moves the guest into the combat screen.
                enterPvPCombatAsGuest(state);
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
        sounds.update(Gdx.graphics.getDeltaTime());
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
