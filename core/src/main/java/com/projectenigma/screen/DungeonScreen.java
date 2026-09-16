package com.projectenigma.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.Palette;
import com.projectenigma.UiRenderer;
import com.projectenigma.UtopiaAssets;
import com.projectenigma.model.DungeonChest;
import com.projectenigma.model.DungeonEnemy;
import com.projectenigma.model.DungeonPickup;
import com.projectenigma.model.Item;
import com.projectenigma.model.DungeonMap;
import com.projectenigma.model.GameSession;
import com.projectenigma.model.TileType;

import java.util.List;
import java.util.ArrayDeque;
import com.badlogic.gdx.math.Vector2;
import com.projectenigma.input.MouseUi;
import com.projectenigma.model.BattleAction;
import com.projectenigma.model.DungeonPathfinder;
import com.projectenigma.model.GridPoint;
import com.projectenigma.audio.SoundCue;

public final class DungeonScreen extends AbstractGameScreen {
    private static final float WORLD_WIDTH = 20f;
    private static final float WORLD_HEIGHT = 11.25f;
    private static final float HELD_MOVE_DELAY = 0.095f;
    private static final String[] PAUSE_OPTIONS = {"Resume", "Save Game", "Main Menu", "Quit"};
    /** Used instead of PAUSE_OPTIONS in Race-to-PvP mode: no Save/Main-Menu path that could touch the real save file mid-race. */
    private static final String[] RACE_PAUSE_OPTIONS = {"Resume", "Abandon Race"};

    private final GameSession session;
    /** True only for a Race-to-PvP exploration-phase session; see {@link #DungeonScreen(ProjectEnigmaGame, GameSession, boolean)}. */
    private final boolean raceMode;
    private final String[] pauseOptions;
    private final OrthographicCamera worldCamera;
    private final OrthographicCamera uiCamera;
    private final FitViewport worldViewport;
    private final FitViewport uiViewport;
    private final boolean[][] explored;

    private final ArrayDeque<GridPoint> route = new ArrayDeque<>();
    private GridPoint destination;
    private DungeonEnemy engagedEnemy;
    private float routeTimer;
    private boolean worldPressed;
    private int pressX, pressY;
    private final Vector2 worldPointer = new Vector2();

    private float renderedPlayerX;
    private float renderedPlayerY;
    private float moveRepeatTimer;
    private boolean inventoryVisible;
    private boolean equipmentVisible;
    private int inventorySelection;
    private int equipmentSelection;
    private boolean pauseVisible;
    private int pauseSelection;
    private String notice = "";
    private float noticeTime;
    private float worldAnimationTime;
    private final java.util.Map<DungeonChest, Float> chestOpenedAt = new java.util.IdentityHashMap<>();
    private UtopiaAssets.Direction facing = UtopiaAssets.Direction.DOWN;

    public DungeonScreen(ProjectEnigmaGame game, GameSession session) {
        this(game, session, false);
    }

    /**
     * @param raceMode true for a Race-to-PvP exploration-phase session:
     *                 disables every {@code SaveService} call (this session
     *                 is never the player's real save and must never
     *                 overwrite it), swaps the pause menu for {@link
     *                 #RACE_PAUSE_OPTIONS}, and adds the countdown
     *                 time-bar and "waiting for opponent" overlay driven by
     *                 {@code game.raceSecondsRemaining()}/{@code
     *                 game.isRaceWaitingForOpponent()}.
     */
    public DungeonScreen(ProjectEnigmaGame game, GameSession session, boolean raceMode) {
        super(game);
        this.session = session;
        this.raceMode = raceMode;
        this.pauseOptions = raceMode ? RACE_PAUSE_OPTIONS : PAUSE_OPTIONS;
        DungeonMap map = session.dungeon();
        explored = new boolean[map.width()][map.height()];
        renderedPlayerX = session.playerX + 0.5f;
        renderedPlayerY = session.playerY + 0.5f;

        worldCamera = new OrthographicCamera();
        worldViewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, worldCamera);
        uiCamera = new OrthographicCamera();
        uiViewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, uiCamera);

        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (isWaitingForOpponent()) {
                    if (keycode == Input.Keys.ESCAPE) {
                        game.abandonRace();
                    }
                    return true;
                }
                if (pauseVisible) {
                    return handlePauseInput(keycode);
                }
                if (inventoryVisible) {
                    return handleInventoryInput(keycode);
                }
                if (equipmentVisible) {
                    return handleEquipmentInput(keycode);
                }
                if (keycode == Input.Keys.ESCAPE) {
                    cancelRoute();
                    pauseVisible = true;
                    pauseSelection = 0;
                    return true;
                }
                if (keycode == Input.Keys.I || keycode == Input.Keys.TAB) {
                    cancelRoute();
                    inventoryVisible = true;
                    equipmentVisible = false;
                    inventorySelection = 0;
                    return true;
                }
                if (keycode == Input.Keys.K) {
                    cancelRoute();
                    equipmentVisible = true;
                    inventoryVisible = false;
                    equipmentSelection = 0;
                    return true;
                }
                if (keycode == Input.Keys.E || keycode == Input.Keys.ENTER) {
                    useCurrentTile();
                    return true;
                }

                int[] direction = directionForKey(keycode);
                if (direction != null) {
                    cancelRoute();
                    tryMove(direction[0], direction[1]);
                    moveRepeatTimer = 0.23f;
                    return true;
                }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) {
                if (pointer != 0 || isWaitingForOpponent() || !MouseUi.containsScreen(worldViewport, x, y, Gdx.graphics.getHeight())) return false;
                if (button == Input.Buttons.RIGHT) { cancelRoute(); return true; }
                worldPressed = button == Input.Buttons.LEFT;
                pressX = x; pressY = y;
                return worldPressed;
            }
            @Override public boolean touchUp(int x, int y, int pointer, int button) {
                if (pointer != 0 || button != Input.Buttons.LEFT) return false;
                boolean clicked = worldPressed && Math.abs(x - pressX) <= 6 && Math.abs(y - pressY) <= 6;
                worldPressed = false;
                if (clicked && !uiLocked()
                        && MouseUi.containsScreen(worldViewport, x, y, Gdx.graphics.getHeight())) {
                    worldViewport.unproject(worldPointer.set(x, y));
                    requestRoute(pointerGoal(worldPointer.x, worldPointer.y));
                }
                return true;
            }
        });
        useMouse(uiViewport).modal(this::uiLocked);
        mouseUi.worldHand(() -> !worldHint().isEmpty());
        mouseUi.add("Items [I]", 22, 18, 165, 44, () -> { cancelRoute(); inventoryVisible = true; equipmentVisible = false; inventorySelection = 0; })
                .when(() -> !uiLocked());
        mouseUi.add("Equipment [K]", 197, 18, 175, 44, () -> { cancelRoute(); equipmentVisible = true; inventoryVisible = false; equipmentSelection = 0; })
                .when(() -> !uiLocked());
        mouseUi.add("Pause [Esc]", 382, 18, 160, 44, () -> { cancelRoute(); pauseVisible = true; pauseSelection = 0; })
                .when(() -> !uiLocked());
        mouseUi.add("Descend [E]", 552, 18, 160, 44, this::useCurrentTile)
                .when(() -> !uiLocked() && session.isAtExit());
        mouseUi.add("Use Selected", 590, 160, 160, 44, this::useSelectedInventoryItem)
                .when(() -> inventoryVisible && !pauseVisible).disabled(this::selectedItemReason);
        mouseUi.add("Close", 760, 160, 160, 44, () -> inventoryVisible = false).when(() -> inventoryVisible && !pauseVisible);
        mouseUi.add("Equip Selected", 420, 160, 190, 44, this::equipSelectedItem)
                .when(() -> equipmentVisible && !pauseVisible).disabled(this::selectedEquipmentReason);
        mouseUi.add("Close", 620, 160, 190, 44, () -> equipmentVisible = false).when(() -> equipmentVisible && !pauseVisible);
        for (int i = 0; i < pauseOptions.length; i++) {
            final int index = i;
            mouseUi.add(pauseOptions[i], 500, 375 - i * 60, 280, 44, () -> { pauseSelection = index; activatePause(); })
                    .when(() -> pauseVisible).hover(() -> pauseSelection = index).selected(() -> pauseSelection == index);
        }
        // HUD panels must not send movement clicks into the world below them.
        mouseUi.add("", 20, 575, 390, 125, () -> {}).block()
                .when(() -> !uiLocked());
        mouseUi.add("", 930, 620, 330, 80, () -> {}).block()
                .when(() -> !uiLocked());
        // Race Mode only: a mouse-reachable way to leave once exploration
        // has ended and this screen is fully frozen waiting on the other
        // player (Esc already does the same thing -- see keyDown above).
        mouseUi.add("Abandon Race", 540, 275, 200, 44, game::abandonRace).when(this::isWaitingForOpponent);
    }

    /** True whenever normal exploration input (movement, HUD buttons, world clicks) should be suppressed. */
    private boolean uiLocked() { return pauseVisible || inventoryVisible || equipmentVisible || isWaitingForOpponent(); }

    private boolean isWaitingForOpponent() { return raceMode && game.isRaceWaitingForOpponent(); }

    private void activatePause() {
        if (raceMode) {
            if (pauseSelection == 0) {
                pauseVisible = false;
            } else if (pauseSelection == 1) {
                game.abandonRace();
            }
            return;
        }
        switch (pauseSelection) {
            case 0 -> pauseVisible = false;
            case 1 -> { game.saveGame(); setNotice("Game saved."); pauseVisible = false; }
            case 2 -> game.abandonToMenu();
            case 3 -> game.quit();
            default -> throw new IllegalStateException("Unknown pause item");
        }
    }

    private void cancelRoute() {
        route.clear(); destination = null; engagedEnemy = null; routeTimer = 0; worldPressed = false;
    }

    private void requestRoute(GridPoint goal) {
        cancelRoute();
        DungeonMap map = session.dungeon();
        if (!map.isWalkable(goal.x(), goal.y()) || !explored[goal.x()][goal.y()]) {
            setNotice("Choose a reachable, explored floor tile."); return;
        }
        DungeonEnemy target = session.enemyAt(goal.x(), goal.y());
        if (target != null && !isVisible(goal.x(), goal.y())) { setNotice("That destination is not reachable."); return; }
        GridPoint start = new GridPoint(session.playerX, session.playerY);
        if (goal.equals(start)) return;
        List<GridPoint> path = DungeonPathfinder.find(map, start, goal, point -> explored[point.x()][point.y()]
                && (session.enemyAt(point.x(), point.y()) == null
                    || (target != null && point.equals(goal))));
        if (path.isEmpty()) { setNotice("No route through explored terrain."); return; }
        route.addAll(path); destination = goal; engagedEnemy = target;
    }

    private void updateRoute(float delta) {
        if (uiLocked() || route.isEmpty()) return;
        routeTimer -= delta;
        if (routeTimer > 0) return;
        GridPoint next = route.removeFirst();
        DungeonEnemy enemy = session.enemyAt(next.x(), next.y());
        if (!session.canMoveTo(next.x(), next.y()) || !explored[next.x()][next.y()]
                || Math.abs(next.x() - session.playerX) + Math.abs(next.y() - session.playerY) != 1
                || (enemy != null && enemy != engagedEnemy)) { cancelRoute(); return; }
        tryMove(next.x() - session.playerX, next.y() - session.playerY);
        if (game.getScreen() != this) return;
        if (route.isEmpty()) { destination = null; engagedEnemy = null; }
        routeTimer = HELD_MOVE_DELAY;
    }

    private GridPoint pointerGoal(float x, float y) {
        // Enemy sheets are 1.5 tiles tall: their visible upper half also counts.
        for (int i = session.enemies.size() - 1; i >= 0; i--) {
            DungeonEnemy enemy = session.enemies.get(i);
            if (enemy.isAlive() && isVisible(enemy.x, enemy.y)
                    && x >= enemy.x && x < enemy.x + 1 && y >= enemy.y && y < enemy.y + 1.5f)
                return new GridPoint(enemy.x, enemy.y);
        }
        return new GridPoint((int)Math.floor(x), (int)Math.floor(y));
    }

    private String worldHint() {
        if (uiLocked() || !MouseUi.containsScreen(worldViewport, Gdx.input.getX(), Gdx.input.getY(), Gdx.graphics.getHeight())) return "";
        worldViewport.unproject(worldPointer.set(Gdx.input.getX(), Gdx.input.getY()));
        GridPoint target = pointerGoal(worldPointer.x, worldPointer.y);
        int x = target.x(), y = target.y();
        if (!session.dungeon().isInside(x, y) || !explored[x][y]) return "";
        DungeonEnemy enemy = session.enemyAt(x, y);
        if (enemy != null && isVisible(x, y)) return "Engage " + enemy.displayName();
        DungeonChest chest = session.chestAt(x, y);
        if (chest != null && !chest.opened) return "Open chest";
        DungeonPickup pickup = session.pickupAt(x, y);
        if (pickup != null) return "Pick up " + pickup.item.name;
        if (session.dungeon().exit().equals(new GridPoint(x, y))) return "Walk to stairs; use Descend on arrival";
        return "";
    }

    @Override public void pause() { super.pause(); cancelRoute(); }
    @Override public void hide() { cancelRoute(); super.hide(); }

    private boolean handlePauseInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            pauseVisible = false;
            return true;
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            pauseSelection = Math.floorMod(pauseSelection - 1, pauseOptions.length);
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            pauseSelection = (pauseSelection + 1) % pauseOptions.length;
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activatePause();
            return true;
        }
        return false;
    }

    private boolean handleInventoryInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.I || keycode == Input.Keys.TAB) {
            inventoryVisible = false;
            return true;
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            inventorySelection = Math.max(0, inventorySelection - 1);
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            inventorySelection = Math.min(Math.max(0, inventoryChoices().size() - 1), inventorySelection + 1);
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            useSelectedInventoryItem();
            return true;
        }
        if (keycode == Input.Keys.K) {
            inventoryVisible = false;
            equipmentVisible = true;
            equipmentSelection = 0;
            return true;
        }
        return false;
    }

    private boolean handleEquipmentInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.K) {
            equipmentVisible = false;
            return true;
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            equipmentSelection = Math.max(0, equipmentSelection - 1);
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            equipmentSelection = Math.min(Math.max(0, equipmentChoices().size() - 1), equipmentSelection + 1);
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            equipSelectedItem();
            return true;
        }
        return false;
    }

    private List<Item> inventoryChoices() {
        List<Item> choices = new java.util.ArrayList<>();
        if (session.hero.inventory != null) {
            for (Item item : session.hero.inventory) {
                if (item != null && item.isConsumable() && item.quantity > 0) choices.add(item);
            }
        }
        return choices;
    }

    private List<Item> equipmentChoices() {
        List<Item> choices = new java.util.ArrayList<>();
        if (session.hero.inventory != null) {
            for (Item item : session.hero.inventory) {
                if (item != null && item.isEquipment()) choices.add(item);
            }
        }
        return choices;
    }

    private String selectedItemReason() {
        List<Item> choices = inventoryChoices();
        if (choices.isEmpty()) return "No picked-up consumables.";
        if (inventorySelection >= choices.size()) inventorySelection = 0;
        Item item = choices.get(inventorySelection);
        return session.hero.canUseItem(item) ? "" : "Cannot use this item right now.";
    }

    private String selectedEquipmentReason() {
        List<Item> choices = equipmentChoices();
        if (choices.isEmpty()) return "No spare equipment.";
        if (equipmentSelection >= choices.size()) equipmentSelection = 0;
        return "";
    }

    private void useSelectedInventoryItem() {
        List<Item> choices = inventoryChoices();
        if (choices.isEmpty()) { setNotice("No picked-up consumables."); return; }
        if (inventorySelection >= choices.size()) inventorySelection = 0;
        Item item = choices.get(inventorySelection);
        int restored = session.hero.useItem(item);
        if (restored <= 0) { setNotice("Cannot use that item right now."); return; }
        game.sounds().play(item.type == Item.Type.HEALTH ? SoundCue.HEAL : SoundCue.POWER_UP);
        setNotice(item.name + " restores " + restored + (item.type == Item.Type.HEALTH ? " HP." : " EN."));
        if (!raceMode) game.saveGame();
    }

    private void equipSelectedItem() {
        List<Item> choices = equipmentChoices();
        if (choices.isEmpty()) { setNotice("No spare equipment."); return; }
        if (equipmentSelection >= choices.size()) equipmentSelection = 0;
        Item item = choices.get(equipmentSelection);
        session.hero.equip(item);
        setNotice("Equipped " + item.name + ". ATK/DEF updated.");
        equipmentVisible = false;
        if (!raceMode) game.saveGame();
    }

    private void useCurrentTile() {
        if (!session.isAtExit()) {
            setNotice("There is nothing to use here.");
            return;
        }
        cancelRoute();
        int healthBefore = session.hero.health, manaBefore = session.hero.mana;
        session.beginNextFloor();
        if (raceMode) {
            // Race Mode has no per-screen "next floor" navigation of its own
            // yet -- the exploration phase stays on this same DungeonScreen
            // instance for its whole duration, so just continue in place
            // rather than calling game.showDungeon() (which would rebuild a
            // *classic* single-player DungeonScreen and drop race context).
            setNotice("Stairs descended. Floor " + session.floorNumber + ".");
        } else {
            game.saveGame();
            game.showDungeon();
        }
        if (session.hero.health > healthBefore) game.sounds().play(SoundCue.HEAL);
        if (session.hero.mana > manaBefore) game.sounds().schedule(game.getScreen(), SoundCue.POWER_UP, .18f);
    }

    private void tryMove(int dx, int dy) {
        facing = directionFromDelta(dx, dy);
        int targetX = session.playerX + dx;
        int targetY = session.playerY + dy;
        if (!session.canMoveTo(targetX, targetY)) {
            return;
        }

        DungeonEnemy enemy = session.enemyAt(targetX, targetY);
        if (enemy != null) {
            cancelRoute();
            game.startCombat(enemy);
            return;
        }

        session.movePlayerTo(targetX, targetY);
        DungeonPickup pickup = session.pickupAt(targetX, targetY);
        if (pickup != null) {
            List<String> loot = session.collectPickup(pickup);
            if (pickup.item != null && pickup.item.type != Item.Type.WEAPON && pickup.item.type != Item.Type.ARMOR) {
                game.sounds().play(pickup.item.type == Item.Type.HEALTH ? SoundCue.HEAL : SoundCue.POWER_UP);
            } else {
                game.sounds().play(SoundCue.POWER_UP);
            }
            setNotice(String.join("\n", loot));
            if (!raceMode) game.saveGame();
        } else {
        DungeonChest chest = session.chestAt(targetX, targetY);
        if (chest != null && !chest.opened) {
            int manaBefore = session.hero.mana;
            List<String> loot = session.openChest(chest);
            chestOpenedAt.put(chest, worldAnimationTime);
            game.sounds().play(SoundCue.CHEST);
            if (session.hero.mana > manaBefore) game.sounds().schedule(this, SoundCue.POWER_UP, .32f);
            setNotice(String.join("\n", loot));
            if (!raceMode) game.saveGame();
        } else if (session.isAtExit()) {
            setNotice("Stairs found. Click Descend or press E / Enter.");
        } else if (!raceMode && session.stepsTaken % 25 == 0) {
            game.saveGame();
        }
        }
    }

    private static int[] directionForKey(int keycode) {
        if (keycode == Input.Keys.LEFT || keycode == Input.Keys.A) {
            return new int[]{-1, 0};
        }
        if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D) {
            return new int[]{1, 0};
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            return new int[]{0, 1};
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            return new int[]{0, -1};
        }
        return null;
    }

    private static UtopiaAssets.Direction directionFromDelta(int dx, int dy) {
        if (dx < 0) {
            return UtopiaAssets.Direction.LEFT;
        }
        if (dx > 0) {
            return UtopiaAssets.Direction.RIGHT;
        }
        if (dy > 0) {
            return UtopiaAssets.Direction.UP;
        }
        return UtopiaAssets.Direction.DOWN;
    }

    private void updateHeldMovement(float delta) {
        if (uiLocked()) {
            return;
        }
        moveRepeatTimer -= delta;
        if (moveRepeatTimer > 0f) {
            return;
        }

        int key = -1;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            key = Input.Keys.LEFT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            key = Input.Keys.RIGHT;
        } else if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            key = Input.Keys.UP;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            key = Input.Keys.DOWN;
        }
        int[] direction = directionForKey(key);
        if (direction != null) {
            cancelRoute();
            tryMove(direction[0], direction[1]);
            moveRepeatTimer = HELD_MOVE_DELAY;
        }
    }

    private void setNotice(String text) {
        notice = text == null ? "" : text;
        noticeTime = 3.4f;
    }

    @Override
    public void render(float delta) {
        float safeDelta = Math.min(delta, 0.1f);
        worldAnimationTime += safeDelta;
        updateHeldMovement(safeDelta);
        if (game.getScreen() != this) return;
        updateRoute(safeDelta);
        if (game.getScreen() != this) return;
        renderedPlayerX = MathUtils.lerp(renderedPlayerX, session.playerX + 0.5f, Math.min(1f, safeDelta * 14f));
        renderedPlayerY = MathUtils.lerp(renderedPlayerY, session.playerY + 0.5f, Math.min(1f, safeDelta * 14f));
        if (noticeTime > 0f) {
            noticeTime -= safeDelta;
        } else {
            notice = "";
        }

        ScreenUtils.clear(Palette.VOID);
        updateWorldCamera();
        drawDungeon();
        drawRoute();
        drawHud();
        if (raceMode) {
            drawRaceTimer();
        }
        if (inventoryVisible) {
            drawInventory();
        }
        if (equipmentVisible) {
            drawEquipment();
        }
        if (pauseVisible) {
            drawPauseMenu();
        }
        if (isWaitingForOpponent()) {
            drawWaitingOverlay();
        }
        drawMouse();
    }

    private void drawRoute() {
        if (destination == null) return;
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.BLUE_LIGHT);
        float x = renderedPlayerX, y = renderedPlayerY;
        for (GridPoint point : route) {
            shapes.rectLine(x, y, point.x() + .5f, point.y() + .5f, .045f);
            x = point.x() + .5f; y = point.y() + .5f;
        }
        float dx = destination.x(), dy = destination.y();
        shapes.rect(dx + .1f, dy + .1f, .8f, .045f); shapes.rect(dx + .1f, dy + .855f, .8f, .045f);
        shapes.rect(dx + .1f, dy + .1f, .045f, .8f); shapes.rect(dx + .855f, dy + .1f, .045f, .8f);
        shapes.end();
    }

    private void updateWorldCamera() {
        DungeonMap map = session.dungeon();
        float halfWidth = worldViewport.getWorldWidth() / 2f;
        float halfHeight = worldViewport.getWorldHeight() / 2f;
        float cameraX = MathUtils.clamp(renderedPlayerX, halfWidth, map.width() - halfWidth);
        float cameraY = MathUtils.clamp(renderedPlayerY, halfHeight, map.height() - halfHeight);
        worldCamera.position.set(cameraX, cameraY, 0f);
        worldCamera.update();
        worldViewport.apply();
    }

    private void drawDungeon() {
        DungeonMap map = session.dungeon();
        revealNearbyTiles(map);
        int minX = Math.max(0, (int) (worldCamera.position.x - worldViewport.getWorldWidth() / 2f) - 1);
        int maxX = Math.min(map.width() - 1, (int) (worldCamera.position.x + worldViewport.getWorldWidth() / 2f) + 1);
        int minY = Math.max(0, (int) (worldCamera.position.y - worldViewport.getWorldHeight() / 2f) - 1);
        int maxY = Math.min(map.height() - 1, (int) (worldCamera.position.y + worldViewport.getWorldHeight() / 2f) + 1);

        game.batch().setProjectionMatrix(worldCamera.combined);
        game.batch().begin();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (!explored[x][y]) {
                    continue;
                }
                boolean visible = isVisible(x, y);
                if (visible) {
                    game.batch().setColor(1f, 1f, 1f, 1f);
                } else {
                    game.batch().setColor(0.30f, 0.35f, 0.40f, 1f);
                }
                if (map.tileAt(x, y) == TileType.FLOOR) {
                    game.batch().draw(game.assets().floorTile(x, y), x, y, 1f, 1f);
                } else {
                    game.batch().draw(game.assets().wallTile(x, y,
                                    map.tileAt(x, y + 1) == TileType.FLOOR,
                                    map.tileAt(x, y - 1) == TileType.FLOOR,
                                    map.tileAt(x + 1, y) == TileType.FLOOR,
                                    map.tileAt(x - 1, y) == TileType.FLOOR),
                            x, y, 1f, 1f);
                }
            }
        }
        game.batch().setColor(1f, 1f, 1f, 1f);
        drawExit(map);
        drawChests();
        game.batch().end();
        drawPickups();
        game.batch().setProjectionMatrix(worldCamera.combined);
        game.batch().begin();
        game.batch().setColor(1f, 1f, 1f, 1f);
        drawEnemies();
        drawPlayer();
        game.batch().end();
    }

    private void drawPickups() {
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(worldCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (DungeonPickup pickup : session.pickups) {
            if (pickup.collected || !explored[pickup.x][pickup.y] || !isVisible(pickup.x, pickup.y) || pickup.item == null) continue;
            switch (pickup.item.type) {
                case HEALTH -> shapes.setColor(Palette.HEALTH);
                case ENERGY, MAX_ENERGY -> shapes.setColor(Palette.MANA);
                case MAX_HEALTH -> shapes.setColor(Palette.XP);
                case WEAPON -> shapes.setColor(Palette.ACCENT);
                case ARMOR -> shapes.setColor(Palette.BLUE_LIGHT);
            }
            shapes.circle(pickup.x + .5f, pickup.y + .55f, .18f);
            shapes.circle(pickup.x + .5f, pickup.y + .55f, .07f);
        }
        shapes.end();
    }

    private void revealNearbyTiles(DungeonMap map) {
        int radius = 8;
        for (int x = Math.max(0, session.playerX - radius); x <= Math.min(map.width() - 1, session.playerX + radius); x++) {
            for (int y = Math.max(0, session.playerY - radius); y <= Math.min(map.height() - 1, session.playerY + radius); y++) {
                int dx = x - session.playerX;
                int dy = y - session.playerY;
                if (dx * dx + dy * dy <= radius * radius) {
                    explored[x][y] = true;
                }
            }
        }
    }

    private boolean isVisible(int x, int y) {
        int dx = x - session.playerX;
        int dy = y - session.playerY;
        return dx * dx + dy * dy <= 64;
    }

    private void drawExit(DungeonMap map) {
        int x = map.exit().x();
        int y = map.exit().y();
        if (!explored[x][y]) {
            return;
        }
        if (!isVisible(x, y)) {
            game.batch().setColor(0.30f, 0.35f, 0.40f, 1f);
        }
        game.batch().draw(game.assets().stairsDownTile(), x, y, 1f, 1f);
        game.batch().setColor(1f, 1f, 1f, 1f);
    }

    private void drawChests() {
        for (DungeonChest chest : session.chests) {
            if (!explored[chest.x][chest.y]) {
                continue;
            }
            if (!isVisible(chest.x, chest.y)) {
                game.batch().setColor(0.30f, 0.35f, 0.40f, 1f);
            }
            Float openedAt = chestOpenedAt.get(chest);
            float elapsed = openedAt == null ? Float.POSITIVE_INFINITY : worldAnimationTime - openedAt;
            game.batch().draw(game.assets().chestFrame(chest.opened, elapsed), chest.x, chest.y, 1f, 1.5f);
            game.batch().setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawEnemies() {
        for (DungeonEnemy enemy : session.enemies) {
            if (!enemy.isAlive() || !isVisible(enemy.x, enemy.y)) {
                continue;
            }
            game.assets().drawWorldEnemy(game.batch(), enemy.type, UtopiaAssets.Direction.DOWN, worldAnimationTime,
                    enemy.x + .5f, enemy.y, 1.5f);
        }
    }

    private void drawPlayer() {
        boolean moving = Math.abs(renderedPlayerX - (session.playerX + 0.5f)) > 0.015f
                || Math.abs(renderedPlayerY - (session.playerY + 0.5f)) > 0.015f;
        game.assets().drawWorldHero(game.batch(), session.hero.heroClass, facing, moving, worldAnimationTime,
                renderedPlayerX, renderedPlayerY - 0.5f, 1.5f);
    }

    private void drawHud() {
        uiViewport.apply();
        uiCamera.update();
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(uiCamera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiRenderer.panel(shapes, 20f, 575f, 390f, 125f, Palette.PANEL);
        UiRenderer.panel(shapes, 930f, 620f, 330f, 80f, Palette.PANEL);
        UiRenderer.bar(shapes, 142f, 654f, 240f, 18f, session.hero.health, session.hero.maxHealth, Palette.HEALTH);
        UiRenderer.bar(shapes, 142f, 622f, 240f, 18f, session.hero.mana, session.hero.maxMana, Palette.MANA);
        UiRenderer.bar(shapes, 142f, 590f, 240f, 14f, session.hero.experience,
                session.hero.experienceForNextLevel(), Palette.XP);
        shapes.end();

        game.batch().setProjectionMatrix(uiCamera.combined);
        game.batch().begin();
        UiRenderer.text(game.batch(), game.mediumFont(), session.hero.heroClass.displayName() + "  Lv " + session.hero.level,
                36f, 687f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "HP  " + session.hero.health + "/" + session.hero.maxHealth,
                36f, 670f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "EN  " + session.hero.mana + "/" + session.hero.maxMana,
                36f, 638f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "XP  " + session.hero.experience + "/" + session.hero.experienceForNextLevel(),
                36f, 605f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.mediumFont(), "Dungeon Floor " + session.floorNumber, 954f, 682f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "Enemies " + session.enemies.size() + "    Gold " + session.hero.gold
                + "    Items " + inventoryChoices().size(), 954f, 646f, Palette.MUTED);
        UiRenderer.centeredText(game.batch(), game.font(), "Click: move | Right-click: stop | WASD",
                980f, 36f, Palette.MUTED);
        String hint = worldHint();
        if (!hint.isEmpty() && notice.isEmpty()) UiRenderer.centeredText(game.batch(), game.font(), hint, 640, 100, Palette.BLUE_LIGHT);
        if (!notice.isEmpty()) {
            UiRenderer.centeredText(game.batch(), game.mediumFont(), notice, 640f, 140f, Palette.TEXT);
        }
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawInventory() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 280f, 115f, 720f, 490f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(280f, 595f, 720f, 10f);
        shapes.end();

        List<Item> choices = inventoryChoices();
        if (!choices.isEmpty() && inventorySelection >= choices.size()) inventorySelection = 0;
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "ITEMS", 640f, 555f, Palette.TEXT);
        if (choices.isEmpty()) {
            UiRenderer.centeredText(game.batch(), game.font(), "No picked-up consumables yet.", 640f, 430f, Palette.MUTED);
        } else {
            for (int i = 0; i < choices.size(); i++) {
                Item item = choices.get(i);
                Color color = i == inventorySelection ? Palette.TEXT : Palette.MUTED;
                UiRenderer.text(game.batch(), game.mediumFont(), (i == inventorySelection ? "> " : "  ") + item.name + " x" + item.quantity, 355f, 455f - i * 48f, color);
                UiRenderer.text(game.batch(), game.font(), item.description, 620f, 455f - i * 48f, Palette.MUTED);
            }
        }
        UiRenderer.centeredText(game.batch(), game.font(), "W/S: select | Enter: use | I / Esc: close | K: equipment", 640f, 205f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawEquipment() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 280f, 105f, 720f, 510f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.BLUE);
        shapes.rect(280f, 605f, 720f, 10f);
        shapes.end();

        List<Item> choices = equipmentChoices();
        if (!choices.isEmpty() && equipmentSelection >= choices.size()) equipmentSelection = 0;
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "EQUIPMENT", 640f, 560f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.mediumFont(), "Weapon: " + session.hero.equippedWeaponName() + "  (ATK +" + session.hero.equipmentAttackBonus() + ")", 330f, 505f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.mediumFont(), "Armor:  " + session.hero.equippedArmorName() + "  (DEF +" + session.hero.equipmentDefenseBonus() + ")", 330f, 470f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "Current ATK " + session.hero.attack() + "    Current DEF " + session.hero.defense(), 330f, 430f, Palette.MUTED);
        if (choices.isEmpty()) {
            UiRenderer.centeredText(game.batch(), game.font(), "No spare equipment yet.", 640f, 365f, Palette.MUTED);
        } else {
            for (int i = 0; i < choices.size(); i++) {
                Item item = choices.get(i);
                Color color = i == equipmentSelection ? Palette.TEXT : Palette.MUTED;
                UiRenderer.text(game.batch(), game.mediumFont(), (i == equipmentSelection ? "> " : "  ") + item.name, 355f, 380f - i * 46f, color);
                UiRenderer.text(game.batch(), game.font(), item.description, 650f, 380f - i * 46f, Palette.MUTED);
            }
        }
        UiRenderer.centeredText(game.batch(), game.font(), "W/S: select | Enter: equip | K / Esc: close", 640f, 205f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Reused every frame by drawRaceTimer() to avoid allocating a Color object per frame just to flash the low-time warning. */
    private final Color raceTimerColor = new Color();

    private void drawRaceTimer() {
        float remaining = Math.max(0f, game.raceSecondsRemaining());
        float duration = Math.max(1, game.raceDurationSeconds());
        float ratio = MathUtils.clamp(remaining / duration, 0f, 1f);

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(uiCamera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiRenderer.panel(shapes, 440f, 648f, 400f, 52f, Palette.PANEL);
        Color barColor = ratio > .5f ? Palette.BLUE : ratio > .2f ? Palette.GOLD : Palette.DANGER;
        UiRenderer.bar(shapes, 456f, 656f, 368f, 14f, Math.round(remaining * 10f), Math.round(duration * 10f), barColor);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        raceTimerColor.set(Palette.TEXT);
        if (remaining <= 10f) {
            float pulse = (MathUtils.sin(worldAnimationTime * 10f) + 1f) / 2f;
            raceTimerColor.lerp(Palette.DANGER, pulse);
        }
        game.batch().setProjectionMatrix(uiCamera.combined);
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.mediumFont(), "EXPLORE - " + formatClock(remaining), 640f, 691f, raceTimerColor);
        game.batch().end();
    }

    private void drawWaitingOverlay() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 390f, 245f, 500f, 235f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(390f, 460f, 500f, 8f);
        shapes.end();

        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "EXPLORATION COMPLETE", 640f, 425f, Palette.TEXT);
        int dots = 1 + (int) (worldAnimationTime * 2f) % 3;
        UiRenderer.centeredText(game.batch(), game.mediumFont(), "Waiting for opponent" + ".".repeat(dots),
                640f, 368f, Palette.MUTED);
        UiRenderer.centeredText(game.batch(), game.font(), "Esc or Abandon Race: leave the match", 640f, 295f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private static String formatClock(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private void drawPauseMenu() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.75f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 450f, 150f, 380f, 440f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(450f, 580f, 380f, 10f);
        shapes.end();

        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "PAUSED", 640f, 535f, Palette.TEXT);
        UiRenderer.centeredText(game.batch(), game.font(), "Esc: resume", 640f, 178f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, false);
        uiViewport.update(width, height, true);
    }
}
