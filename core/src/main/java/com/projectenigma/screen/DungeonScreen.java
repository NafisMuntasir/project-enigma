package com.projectenigma.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.projectenigma.model.DungeonMap;
import com.projectenigma.model.GameSession;
import com.projectenigma.model.TileType;

import java.util.List;
import java.util.ArrayDeque;
import com.badlogic.gdx.math.Vector2;
import com.projectenigma.input.MouseUi;
import com.projectenigma.model.ActionAvailability;
import com.projectenigma.model.BattleAction;
import com.projectenigma.model.DungeonPathfinder;
import com.projectenigma.model.GridPoint;
import com.projectenigma.audio.SoundCue;

public final class DungeonScreen extends AbstractGameScreen {
    private static final float WORLD_WIDTH = 20f;
    private static final float WORLD_HEIGHT = 11.25f;
    private static final float HELD_MOVE_DELAY = 0.095f;
    private static final String[] PAUSE_OPTIONS = {"Resume", "Save Game", "Main Menu", "Quit"};

    private final GameSession session;
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
    private boolean pauseVisible;
    private int pauseSelection;
    private String notice = "";
    private float noticeTime;
    private float worldAnimationTime;
    private UtopiaAssets.Direction facing = UtopiaAssets.Direction.DOWN;

    public DungeonScreen(ProjectEnigmaGame game, GameSession session) {
        super(game);
        this.session = session;
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
                if (pauseVisible) {
                    return handlePauseInput(keycode);
                }
                if (inventoryVisible) {
                    return handleInventoryInput(keycode);
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
                    return true;
                }
                if (keycode == Input.Keys.P) {
                    drinkPotion();
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
                if (pointer != 0 || !MouseUi.containsScreen(worldViewport, x, y, Gdx.graphics.getHeight())) return false;
                if (button == Input.Buttons.RIGHT) { cancelRoute(); return true; }
                worldPressed = button == Input.Buttons.LEFT;
                pressX = x; pressY = y;
                return worldPressed;
            }
            @Override public boolean touchUp(int x, int y, int pointer, int button) {
                if (pointer != 0 || button != Input.Buttons.LEFT) return false;
                boolean clicked = worldPressed && Math.abs(x - pressX) <= 6 && Math.abs(y - pressY) <= 6;
                worldPressed = false;
                if (clicked && !pauseVisible && !inventoryVisible
                        && MouseUi.containsScreen(worldViewport, x, y, Gdx.graphics.getHeight())) {
                    worldViewport.unproject(worldPointer.set(x, y));
                    requestRoute(pointerGoal(worldPointer.x, worldPointer.y));
                }
                return true;
            }
        });
        useMouse(uiViewport).modal(() -> pauseVisible || inventoryVisible);
        mouseUi.worldHand(() -> !worldHint().isEmpty());
        mouseUi.add("Inventory [I]", 22, 18, 165, 44, () -> { cancelRoute(); inventoryVisible = true; })
                .when(() -> !pauseVisible && !inventoryVisible);
        mouseUi.add("Use Potion [P]", 197, 18, 175, 44, this::drinkPotion)
                .when(() -> !pauseVisible && !inventoryVisible).disabled(this::potionReason);
        mouseUi.add("Pause [Esc]", 382, 18, 160, 44, () -> { cancelRoute(); pauseVisible = true; pauseSelection = 0; })
                .when(() -> !pauseVisible && !inventoryVisible);
        mouseUi.add("Descend [E]", 552, 18, 160, 44, this::useCurrentTile)
                .when(() -> !pauseVisible && !inventoryVisible && session.isAtExit());
        mouseUi.add("Use Potion", 420, 160, 210, 44, this::drinkPotion).when(() -> inventoryVisible && !pauseVisible)
                .disabled(this::potionReason);
        mouseUi.add("Close", 650, 160, 210, 44, () -> inventoryVisible = false).when(() -> inventoryVisible && !pauseVisible);
        for (int i = 0; i < PAUSE_OPTIONS.length; i++) {
            final int index = i;
            mouseUi.add(PAUSE_OPTIONS[i], 500, 375 - i * 60, 280, 44, () -> { pauseSelection = index; activatePause(); })
                    .when(() -> pauseVisible).hover(() -> pauseSelection = index).selected(() -> pauseSelection == index);
        }
        // HUD panels must not send movement clicks into the world below them.
        mouseUi.add("", 20, 575, 390, 125, () -> {}).block()
                .when(() -> !pauseVisible && !inventoryVisible);
        mouseUi.add("", 930, 620, 330, 80, () -> {}).block()
                .when(() -> !pauseVisible && !inventoryVisible);
    }

    private String potionReason() { return ActionAvailability.reason(session.hero, BattleAction.POTION); }

    private void activatePause() {
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
        if (pauseVisible || inventoryVisible || route.isEmpty()) return;
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
        if (pauseVisible || inventoryVisible || !MouseUi.containsScreen(worldViewport, Gdx.input.getX(), Gdx.input.getY(), Gdx.graphics.getHeight())) return "";
        worldViewport.unproject(worldPointer.set(Gdx.input.getX(), Gdx.input.getY()));
        GridPoint target = pointerGoal(worldPointer.x, worldPointer.y);
        int x = target.x(), y = target.y();
        if (!session.dungeon().isInside(x, y) || !explored[x][y]) return "";
        DungeonEnemy enemy = session.enemyAt(x, y);
        if (enemy != null && isVisible(x, y)) return "Engage " + enemy.displayName();
        DungeonChest chest = session.chestAt(x, y);
        if (chest != null && !chest.opened) return "Open chest";
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
            pauseSelection = Math.floorMod(pauseSelection - 1, PAUSE_OPTIONS.length);
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            pauseSelection = (pauseSelection + 1) % PAUSE_OPTIONS.length;
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
        if (keycode == Input.Keys.P || keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            drinkPotion();
            return true;
        }
        return false;
    }

    private void drinkPotion() {
        int before = session.hero.health;
        if (session.hero.usePotion()) {
            game.sounds().schedule(this, SoundCue.HEAL, .07f);
            setNotice("Potion restores " + (session.hero.health - before) + " HP.");
            game.saveGame();
        } else if (session.hero.potions <= 0) {
            setNotice("No potions remain.");
        } else {
            setNotice("Health is already full.");
        }
    }

    private void useCurrentTile() {
        if (!session.isAtExit()) {
            setNotice("There is nothing to use here.");
            return;
        }
        cancelRoute();
        int healthBefore = session.hero.health, manaBefore = session.hero.mana;
        session.beginNextFloor();
        game.saveGame();
        game.showDungeon();
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
        DungeonChest chest = session.chestAt(targetX, targetY);
        if (chest != null && !chest.opened) {
            int manaBefore = session.hero.mana;
            List<String> loot = session.openChest(chest);
            game.sounds().play(SoundCue.CHEST);
            if (session.hero.mana > manaBefore) game.sounds().schedule(this, SoundCue.POWER_UP, .32f);
            setNotice(String.join("\n", loot));
            game.saveGame();
        } else if (session.isAtExit()) {
            setNotice("Stairs found. Click Descend or press E / Enter.");
        } else if (session.stepsTaken % 25 == 0) {
            game.saveGame();
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
        if (pauseVisible || inventoryVisible) {
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
        if (inventoryVisible) {
            drawInventory();
        }
        if (pauseVisible) {
            drawPauseMenu();
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
        drawEnemies();
        drawPlayer();
        game.batch().end();
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
            game.batch().draw(game.assets().chestTile(chest.opened), chest.x, chest.y, 1f, 1f);
            game.batch().setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawEnemies() {
        for (DungeonEnemy enemy : session.enemies) {
            if (!enemy.isAlive() || !isVisible(enemy.x, enemy.y)) {
                continue;
            }
            game.batch().draw(game.assets().worldEnemyFrame(enemy.type, worldAnimationTime),
                    enemy.x, enemy.y, 1f, 1.5f);
        }
    }

    private void drawPlayer() {
        boolean moving = Math.abs(renderedPlayerX - (session.playerX + 0.5f)) > 0.015f
                || Math.abs(renderedPlayerY - (session.playerY + 0.5f)) > 0.015f;
        game.batch().draw(game.assets().worldHeroFrame(session.hero.heroClass, facing, moving, worldAnimationTime),
                renderedPlayerX - 0.5f, renderedPlayerY - 0.5f, 1f, 1.5f);
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
                + "    Potions " + session.hero.potions, 954f, 646f, Palette.MUTED);
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
        UiRenderer.panel(shapes, 330f, 145f, 620f, 430f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(330f, 565f, 620f, 10f);
        UiRenderer.bar(shapes, 510f, 352f, 330f, 22f, session.hero.health, session.hero.maxHealth, Palette.HEALTH);
        UiRenderer.bar(shapes, 510f, 305f, 330f, 22f, session.hero.mana, session.hero.maxMana, Palette.MANA);
        shapes.end();

        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "INVENTORY", 640f, 525f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.mediumFont(), session.hero.heroClass.displayName() + " - Level " + session.hero.level,
                415f, 455f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "Attack: " + session.hero.attack + "    Defense: " + session.hero.defense,
                415f, 416f, Palette.MUTED);
        UiRenderer.text(game.batch(), game.font(), "Health", 415f, 370f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "Energy", 415f, 323f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.mediumFont(), "Potions: " + session.hero.potions, 415f, 260f, Palette.GOLD);
        UiRenderer.text(game.batch(), game.mediumFont(), "Gold: " + session.hero.gold, 690f, 260f, Palette.GOLD);
        UiRenderer.centeredText(game.batch(), game.font(), "P / Enter: use potion    I / Tab / Esc: close",
                640f, 223f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
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
