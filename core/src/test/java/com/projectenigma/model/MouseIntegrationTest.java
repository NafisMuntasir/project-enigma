package com.projectenigma.model;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.Sound;
import com.projectenigma.audio.SoundCue;
import java.util.ArrayList;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.SaveService;
import com.projectenigma.network.*;
import com.projectenigma.screen.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real screen InputProcessors and model transitions without opening a GL window. */
class MouseIntegrationTest {
    @TempDir Path files;
    private ProjectEnigmaGame game;
    private final AtomicReference<InputProcessor> input = new AtomicReference<>();
    private int width = 1280, height = 720;
    private final List<SoundCue> playedSounds = new ArrayList<>();
    private String clipboard = "";
    private final ConcurrentLinkedQueue<Runnable> networkTasks = new ConcurrentLinkedQueue<>();

    @BeforeEach void setup() throws Exception {
        GdxNativesLoader.load();
        Gdx.graphics = proxy(Graphics.class, (o, m, a) -> switch (m.getName()) {
            case "getWidth", "getBackBufferWidth" -> width;
            case "getHeight", "getBackBufferHeight" -> height;
            default -> zero(m.getReturnType());
        });
        Gdx.input = proxy(Input.class, (o, m, a) -> {
            if (m.getName().equals("setInputProcessor")) input.set((InputProcessor)a[0]);
            if (m.getName().equals("getInputProcessor")) return input.get();
            return zero(m.getReturnType());
        });
        Gdx.gl = Gdx.gl20 = proxy(GL20.class, (o, m, a) -> zero(m.getReturnType()));
        Gdx.files = proxy(Files.class, (o, m, a) -> m.getName().equals("local")
                ? new FileHandle(files.resolve((String)a[0]).toFile()) : zero(m.getReturnType()));
        Clipboard clip = new Clipboard() {
            public String getContents() { return clipboard; }
            public void setContents(String text) { clipboard = text; }
            public boolean hasContents() { return !clipboard.isEmpty(); }
        };
        Gdx.app = proxy(Application.class, (o, m, a) -> {
            if (m.getName().equals("getClipboard")) return clip;
            if (m.getName().equals("postRunnable")) networkTasks.add((Runnable)a[0]);
            return zero(m.getReturnType());
        });
        game = new ProjectEnigmaGame();
        game.sounds().load(cue -> proxy(Sound.class, (o, m, a) -> {
            if (m.getName().equals("play")) playedSounds.add(cue);
            return zero(m.getReturnType());
        }));
        set(game, "saveService", new SaveService());
    }
    @AfterEach void cleanup() {
        game.sounds().dispose();
        if (game.pvpClient() != null) game.pvpClient().close();
        if (game.pvpServer() != null) game.pvpServer().close();
        Gdx.app = null; Gdx.input = null; Gdx.graphics = null; Gdx.files = null; Gdx.gl = null; Gdx.gl20 = null;
    }
    @Test void menusRequireReleaseAndOverlayBlocksUnderlyingButtons() {
        game.showMenu(); Screen menu = game.getScreen();
        down(200, 430); assertSame(menu, game.getScreen());
        up(700, 430); assertSame(menu, game.getScreen());
        click(200, 364); assertSame(menu, game.getScreen()); // Continue has no save.
        click(200, 238); // Controls modal.
        click(200, 430); assertSame(menu, game.getScreen());
        click(640, 150); // Close is under neither old menu option.
        up(200, 430); assertSame(menu, game.getScreen()); // No matching press.
        click(200, 430); assertInstanceOf(ClassSelectScreen.class, game.getScreen());
    }
    @Test void classCardOnlySelectsAndBeginStartsChosenClass() {
        game.showClassSelect();
        click(394, 300); assertNull(game.session());
        click(750, 78);
        assertInstanceOf(DungeonScreen.class, game.getScreen());
        assertEquals(HeroClass.MAGE, game.session().hero.heroClass);
        assertTrue(game.saves().hasSave());
    }
    @Test void readyIsSubmittedOnlyOnceForMouseAndKeyboard() {
        int[] submissions = {0};
        ClassSelectScreen screen = new ClassSelectScreen(game, hero -> submissions[0]++);
        game.setScreen(screen);
        click(750, 78); click(750, 78); input.get().keyDown(Input.Keys.ENTER);
        assertEquals(1, submissions[0]);
    }
    @Test void resizingAndLetterboxMarginsDoNotOffsetHitboxes() {
        width = 1600; height = 720;
        game.showMenu(); Screen menu = game.getScreen();
        input.get().touchDown(100, 290, 0, Input.Buttons.LEFT);
        input.get().touchUp(100, 290, 0, Input.Buttons.LEFT);
        assertSame(menu, game.getScreen());
        input.get().touchDown(160 + 200, 720 - 430, 0, Input.Buttons.LEFT);
        input.get().touchUp(160 + 200, 720 - 430, 0, Input.Buttons.LEFT);
        assertInstanceOf(ClassSelectScreen.class, game.getScreen());
        width = 960; height = 540; game.getScreen().resize(width, height);
        input.get().touchDown(295, 315, 0, Input.Buttons.LEFT);
        input.get().touchUp(295, 315, 0, Input.Buttons.LEFT);
        input.get().touchDown(562, 482, 0, Input.Buttons.LEFT);
        input.get().touchUp(562, 482, 0, Input.Buttons.LEFT);
        assertEquals(HeroClass.MAGE, game.session().hero.heroClass);
    }
    @Test void dungeonRetargetCancelFocusAndKeyboardOverride() throws Exception {
        DungeonScreen screen = dungeon();
        worldClick(screen, 34, 20); assertFalse(route(screen).isEmpty());
        worldClick(screen, 31, 20); assertEquals(new GridPoint(31, 20), route(screen).getLast());
        input.get().touchDown(640, 360, 0, Input.Buttons.RIGHT); assertTrue(route(screen).isEmpty());
        worldClick(screen, 34, 20); screen.pause(); assertTrue(route(screen).isEmpty());
        worldClick(screen, 34, 20); input.get().keyDown(Input.Keys.W); assertTrue(route(screen).isEmpty());
        assertEquals(21, game.session().playerY);
        worldClick(screen, 34, 20); worldClick(screen, 39, 20); assertTrue(route(screen).isEmpty()); // Unknown terrain.
        worldClick(screen, 34, 20); click(90, 40); assertTrue(route(screen).isEmpty());
        worldClick(screen, 34, 20); assertTrue(route(screen).isEmpty()); // Inventory captures world clicks.
        click(750, 180); assertEquals(30, game.session().playerX);
    }
    @Test void chestStairsAndDeliberateEnemyEncounterUseExistingGameRules() throws Exception {
        DungeonScreen screen = dungeon(); GameSession session = game.session();
        session.chests.add(new DungeonChest(31, 20));
        worldClick(screen, 31, 20); tick(screen);
        assertTrue(session.chests.get(0).opened); assertTrue(session.hero.gold > 0);
        worldClick(screen, 32, 20); tick(screen);
        assertTrue(session.isAtExit()); assertEquals(1, session.floorNumber);
        click(630, 40); assertEquals(2, session.floorNumber);
        screen = dungeon(); session = game.session();
        DungeonEnemy enemy = new DungeonEnemy(1, EnemyType.CAVE_SLIME, 32, 20, 1); session.enemies.add(enemy);
        worldClick(screen, 34, 20); assertFalse(route(screen).contains(new GridPoint(32, 20)));
        worldClick(screen, 32, 20);
        tick(screen); tick(screen);
        assertInstanceOf(CombatScreen.class, game.getScreen());
    }
    @Test void combatRepeatedClicksAndInvalidActionsDoNotSpendExtraTurns() throws Exception {
        DungeonScreen dungeon = dungeon(); GameSession session = game.session();
        DungeonEnemy enemy = new DungeonEnemy(1, EnemyType.CAVE_SLIME, 32, 20, 4);
        game.startCombat(enemy); CombatScreen screen = (CombatScreen)game.getScreen();
        assertEquals(1, session.hero.inventory.size());
        assertEquals(3, session.hero.inventory.get(0).quantity); // Starting Med Gels are unified under Items.
        click(100, 130); int after = enemy.health;
        click(100, 130); input.get().keyDown(Input.Keys.NUM_1); assertEquals(after, enemy.health);
        set(screen, "turnAnimationTime", 2f); input.get().keyDown(Input.Keys.NUM_1); assertTrue(enemy.health < after);
    }
    @Test void guestWaitsForHostEvenAfterAnimationAndLearnsPotionRejection() throws Exception {
        PvPClient client = new PvPClient(); set(game, "pvpClient", client);
        Hero host = new Hero(HeroClass.WARRIOR), guest = new Hero(HeroClass.MAGE); guest.health -= 20;
        PvPBattleState state = new PvPBattleState(HeroSnapshot.of(host), HeroSnapshot.of(guest), 1,
                PvPOutcome.ONGOING, MatchStatus.IN_PROGRESS, List.of());
        PvPCombatScreen screen = PvPCombatScreen.forGuest(game, state); game.setScreen(screen);
        set(screen, "actionAnimationTime", 10f); click(100, 65);
        assertTrue((boolean)get(screen, "awaitingHost"));
        set(screen, "actionAnimationTime", 10f); click(100, 130);
        assertEquals(BattleAction.POTION, get(screen, "pendingAction"));
        screen.onStateReceived(new PvPBattleState(state.player0(), state.player1(), 1, state.outcome(), state.status(), List.of("No potions remain.")));
        assertFalse((boolean)get(screen, "awaitingHost"));
        set(screen, "actionAnimationTime", 10f); click(100, 65);
        assertFalse((boolean)get(screen, "awaitingHost"));
        click(100, 130); assertTrue((boolean)get(screen, "awaitingHost"));
        screen.onDisconnected(); assertFalse((boolean)get(screen, "awaitingHost"));
        click(100, 130); assertFalse((boolean)get(screen, "awaitingHost"));
    }
    @Test void pasteAndAddressEditingAreClickable() throws Exception {
        game.showMultiplayerMenu(); click(640, 358);
        clipboard = "192.168.1.10"; click(490, 250);
        assertEquals(clipboard, get(game.getScreen(), "ipInput").toString());
        input.get().keyDown(Input.Keys.BACKSPACE); input.get().keyTyped('5');
        assertEquals("192.168.1.15", get(game.getScreen(), "ipInput").toString());
        clipboard = "not an address"; click(490, 250);
        assertEquals("192.168.1.15", get(game.getScreen(), "ipInput").toString());
        click(780, 250); click(640, 290); assertInstanceOf(MenuScreen.class, game.getScreen());
    }
    @Test void bothMouseControlledPvpScreensFinishMatchOverSocketsAfterReconnect() throws Exception {
        ProjectEnigmaGame guestGame = new ProjectEnigmaGame();
        set(guestGame, "saveService", new SaveService());
        PvPServer server = new PvPServer(0); set(game, "pvpServer", server);
        PvPMatch match = new PvPMatch(new Hero(HeroClass.WARRIOR), new Hero(HeroClass.MAGE), 42);
        PvPCombatScreen hostScreen = PvPCombatScreen.forHost(game, match); game.setScreen(hostScreen);
        server.setListener(hostScreen);
        PvPCombatScreen guestScreen = PvPCombatScreen.forGuest(guestGame, match.currentState()); guestGame.setScreen(guestScreen);
        PvPClient client = new PvPClient(); set(guestGame, "pvpClient", client); client.setListener(guestScreen);
        try {
            client.connect("127.0.0.1", server.port());
            PvPClient firstClient = client;
            pumpUntil(() -> server.hasGuest() && firstClient.isConnected());
            guestScreen.show(); set(guestScreen, "actionAnimationTime", 10f);
            int originalHealth = match.currentState().player0().health();
            click(100, 130); assertEquals(originalHealth, match.currentState().player0().health()); // Not guest's turn.
            client.close();
            pumpUntil(() -> match.status() == MatchStatus.WAITING_FOR_RECONNECT);
            hostScreen.show(); set(hostScreen, "actionAnimationTime", 10f);
            click(100, 130); assertEquals(MatchStatus.WAITING_FOR_RECONNECT, match.status());
            client = new PvPClient(); set(guestGame, "pvpClient", client); client.setListener(guestScreen);
            client.connect("127.0.0.1", server.port());
            pumpUntil(() -> match.status() == MatchStatus.IN_PROGRESS && sameBattle(guestScreen, match.currentState()));
            for (int turn = 0; turn < 50 && match.outcome() == PvPOutcome.ONGOING; turn++) {
                int actor = match.currentState().currentTurn();
                PvPCombatScreen active = actor == 0 ? hostScreen : guestScreen;
                active.show(); set(active, "actionAnimationTime", 10f);
                click(100, 130); click(100, 130); // Second release cannot resolve a second turn.
                pumpUntil(() -> (match.currentState().currentTurn() != actor || match.status() == MatchStatus.FINISHED)
                        && sameBattle(guestScreen, match.currentState()));
            }
            assertEquals(MatchStatus.FINISHED, match.status());
            assertEquals(PvPOutcome.HOST_WINS, match.outcome());
            guestScreen.show(); set(guestScreen, "actionAnimationTime", 10f); click(260, 105);
            assertInstanceOf(MenuScreen.class, guestGame.getScreen());
            hostScreen.show(); set(hostScreen, "actionAnimationTime", 10f); click(260, 105);
            assertInstanceOf(MenuScreen.class, game.getScreen());
        } finally { client.close(); server.close(); }
    }

    @Test void saveContinueAndGameOverRemainMouseAccessible() throws Exception {
        GameSession session = new GameSession(42, HeroClass.WARRIOR);
        set(game, "session", session); game.showDungeon();
        DungeonScreen screen = (DungeonScreen)game.getScreen();
        invoke(screen, "updateWorldCamera", new Class<?>[0]);
        invoke(screen, "revealNearbyTiles", new Class<?>[]{DungeonMap.class}, session.dungeon());
        GridPoint next = session.dungeon().walkableTiles().stream()
                .filter(p -> p.manhattanDistance(new GridPoint(session.playerX, session.playerY)) == 1).findFirst().orElseThrow();
        worldClick(screen, next.x(), next.y()); tick(screen);
        click(460, 40); click(640, 332); // Pause -> Save.
        click(460, 40); click(640, 272); // Pause -> Main Menu.
        assertInstanceOf(MenuScreen.class, game.getScreen());
        click(200, 365); assertInstanceOf(DungeonScreen.class, game.getScreen());
        assertEquals(next.x(), game.session().playerX);
        assertEquals(next.y(), game.session().playerY);
        game.setScreen(new GameOverScreen(game)); click(640, 350);
        assertInstanceOf(ClassSelectScreen.class, game.getScreen());
    }

    private boolean sameBattle(PvPCombatScreen screen, PvPBattleState expected) {
        try {
            PvPBattleState actual = (PvPBattleState)get(screen, "state");
            return actual.player0().equals(expected.player0()) && actual.player1().equals(expected.player1())
                    && actual.currentTurn() == expected.currentTurn() && actual.status() == expected.status();
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private void pumpUntil(BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        do {
            Runnable task;
            while ((task = networkTasks.poll()) != null) task.run();
            if (done.getAsBoolean()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("Timed out waiting for network-driven screen state");
    }

    @Test void hoveringAndClickingOptionsProduceOneCuePerInteraction() {
        game.showMenu();
        input.get().mouseMoved(200, 290);
        game.sounds().update(.1f);
        input.get().mouseMoved(210, 290);
        assertEquals(List.of(SoundCue.HOVER), playedSounds);
        input.get().mouseMoved(700, 290); input.get().mouseMoved(210, 290);
        assertEquals(2, playedSounds.size());
        click(200, 365); // Disabled Continue.
        assertEquals(2, playedSounds.size());
        down(200, 430); up(700, 430); assertEquals(2, playedSounds.size());
        click(200, 430); assertEquals(SoundCue.CLICK, playedSounds.get(2));
    }

    @Test void skillPlayerHitAndEnemyReplyHaveSeparateAudioTiming() throws Exception {
        dungeon(); GameSession session = game.session();
        DungeonEnemy enemy = new DungeonEnemy(1, EnemyType.CAVE_SLIME, 32, 20, 8);
        game.startCombat(enemy);
        assertEquals(List.of(SoundCue.ENCOUNTER), playedSounds);
        click(255, 130);
        assertEquals(List.of(SoundCue.ENCOUNTER, SoundCue.CLICK, SoundCue.SKILL), playedSounds);
        advanceAudio(.2f); assertFalse(playedSounds.contains(SoundCue.DAMAGE));
        advanceAudio(.1f); assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.DAMAGE).count());
        advanceAudio(.8f); assertEquals(2, playedSounds.stream().filter(c -> c == SoundCue.DAMAGE).count());
        int count = playedSounds.size(); advanceAudio(1); assertEquals(count, playedSounds.size());
    }

    @Test void chestsHealingAndLevelUpsProduceTheirEffectsOnlyOnSuccess() throws Exception {
        DungeonScreen screen = dungeon(); GameSession session = game.session();
        session.chests.add(new DungeonChest(31, 20));
        worldClick(screen, 31, 20); tick(screen);
        assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.CHEST).count());
        worldClick(screen, 30, 20); tick(screen); worldClick(screen, 31, 20); tick(screen);
        assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.CHEST).count());
        session.hero.health -= 20; click(270, 40); advanceAudio(.1f);
        assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.HEAL).count());
        click(270, 40); advanceAudio(.1f);
        assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.HEAL).count());
        session.hero.experience = 44; session.hero.attack = 999; session.hero.health -= 20;
        game.startCombat(new DungeonEnemy(2, EnemyType.CAVE_SLIME, 32, 20, 1));
        click(100, 130); advanceAudio(1.2f);
        assertEquals(2, session.hero.level);
        assertEquals(1, playedSounds.stream().filter(c -> c == SoundCue.POWER_UP).count());
        assertEquals(2, playedSounds.stream().filter(c -> c == SoundCue.HEAL).count());
    }

    private void advanceAudio(float seconds) {
        for (int i = 0; i < Math.round(seconds * 10); i++) game.sounds().update(.1f);
    }

    private DungeonScreen dungeon() throws Exception {
        GameSession session = new GameSession(); session.playerX = 30; session.playerY = 20;
        DungeonMap map = new DungeonMap(61, 41);
        for (int x = 20; x <= 40; x++) for (int y = 12; y <= 28; y++) map.carve(x, y);
        map.setStart(new GridPoint(30, 20)); map.setExit(new GridPoint(32, 20));
        set(session, "dungeon", map); set(game, "session", session); game.showDungeon();
        DungeonScreen screen = (DungeonScreen)game.getScreen();
        invoke(screen, "updateWorldCamera", new Class<?>[0]);
        invoke(screen, "revealNearbyTiles", new Class<?>[]{DungeonMap.class}, map);
        return screen;
    }
    private void worldClick(DungeonScreen screen, int x, int y) throws Exception {
        Viewport viewport = (Viewport)get(screen, "worldViewport");
        Vector2 point = viewport.project(new Vector2(x + .5f, y + .5f));
        input.get().touchDown((int)point.x, height - (int)point.y, 0, Input.Buttons.LEFT);
        input.get().touchUp((int)point.x, height - (int)point.y, 0, Input.Buttons.LEFT);
    }
    @SuppressWarnings("unchecked") private ArrayDeque<GridPoint> route(DungeonScreen screen) throws Exception { return (ArrayDeque<GridPoint>)get(screen, "route"); }
    private void tick(DungeonScreen screen) throws Exception { invoke(screen, "updateRoute", new Class<?>[]{float.class}, .1f); }
    private void down(int x, int y) { input.get().touchDown(x, height - y, 0, Input.Buttons.LEFT); }
    private void up(int x, int y) { input.get().touchUp(x, height - y, 0, Input.Buttons.LEFT); }
    private void click(int x, int y) { down(x, y); up(x, y); }
    private static void set(Object object, String name, Object value) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private static Object get(Object object, String name) throws Exception { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(object, args);
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, InvocationHandler handler) { return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler); }
    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false; if (type == int.class) return 0;
        if (type == float.class) return 0f; if (type == long.class) return 0L;
        if (type == double.class) return 0d; return null;
    }
}
