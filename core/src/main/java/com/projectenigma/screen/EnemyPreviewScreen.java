package com.projectenigma.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.UiRenderer;
import com.projectenigma.UtopiaAssets;
import com.projectenigma.graphics.EnemyArt;
import com.projectenigma.model.EnemyType;
import com.projectenigma.model.HeroClass;

/** Asset inspection without starting a run, changing loot, or writing a save. */
public final class EnemyPreviewScreen extends AbstractGameScreen {
    private static final UtopiaAssets.BattlePose[] POSES = {
            UtopiaAssets.BattlePose.IDLE, UtopiaAssets.BattlePose.ATTACK,
            UtopiaAssets.BattlePose.HURT, UtopiaAssets.BattlePose.DEFEAT};
    private static final int[] COUNTS = {4, 6, 3, 6};
    private static final String[] CHEST_STATES = {"Closed", "Unlatch", "Opening", "Open"};
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(1280, 720, camera);
    private EnemyType enemy = EnemyType.CAVE_SLIME;
    private int selected;
    private boolean chest, playing = true, light;
    private float time;

    public EnemyPreviewScreen(ProjectEnigmaGame game) {
        super(game);
        useInput(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key >= Input.Keys.NUM_1 && key <= Input.Keys.NUM_4) { select(key - Input.Keys.NUM_1); return true; }
                if (key == Input.Keys.SPACE) { playing = !playing; return true; }
                if (key == Input.Keys.RIGHT) { step(); return true; }
                if (key == Input.Keys.ESCAPE) { game.showMenu(); return true; }
                return false;
            }
        });
        useMouse(viewport);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            mouseUi.add(() -> chest ? CHEST_STATES[index] : POSES[index].name(),
                    40 + i * 302, 608, 290, 42, () -> select(index))
                    .selected(() -> chest ? EnemyArt.chestFrame(true, time) == index : selected == index);
        }
        for (EnemyType type : EnemyType.values()) {
            mouseUi.add(type.displayName(), 40 + type.ordinal() * 242, 554, 230, 36,
                    () -> { enemy = type; chest = false; time = 0; })
                    .selected(() -> !chest && enemy == type);
        }
        mouseUi.add("Supply Chest", 1008, 554, 230, 36, () -> { chest = true; time = 0; })
                .selected(() -> chest);
        mouseUi.add(() -> playing ? "Pause" : "Play", 40, 20, 180, 42, () -> playing = !playing);
        mouseUi.add("Next Frame", 238, 20, 180, 42, this::step);
        mouseUi.add("Change Background", 436, 20, 240, 42, () -> light = !light);
        mouseUi.add("Operative Viewer", 694, 20, 240, 42, game::showOperativePreview);
        mouseUi.add("Main Menu", 952, 20, 240, 42, game::showMenu);
    }

    private void select(int index) {
        if (chest) { time = (index + .01f) * EnemyArt.CHEST_FRAME_DURATION; playing = false; }
        else { selected = index; time = 0; }
    }

    private float duration() { return !chest && selected == 3 ? .16f : .12f; }
    private int count() { return chest ? 4 : COUNTS[selected]; }
    private void step() {
        playing = false;
        int frame = (Math.min(count() - 1, (int) (time / duration())) + 1) % count();
        time = (frame + .01f) * duration();
    }

    @Override public void render(float delta) {
        if (playing) time = (time + Math.min(delta, .1f)) %
                (count() * duration() + (chest ? 1.2f : selected == 0 ? 0 : .65f));
        ScreenUtils.clear(light ? .75f : .055f, light ? .80f : .075f, light ? .85f : .10f, 1);
        viewport.apply(); camera.update();
        Color ink = light ? Color.BLACK : Color.WHITE;
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(light ? Color.GRAY : Color.DARK_GRAY);
        shapes.line(90, 264, 1190, 264);
        shapes.line(90, 107, 1190, 107);
        shapes.end();
        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(Color.WHITE);
        game.batch().begin();
        String name = chest ? "SUPPLY CHEST" : enemy.displayName().toUpperCase(java.util.Locale.ROOT);
        UiRenderer.text(game.batch(), game.titleFont(), name + " / ANIMATION VIEWER", 40, 694, ink);
        UiRenderer.text(game.batch(), game.font(), "1-4 select, Space pause, Right step | Preview only: no save changes",
                40, 666, ink);
        if (chest) drawChest(ink); else drawEnemy(ink);
        game.batch().end();
        drawMouse();
    }

    private void drawEnemy(Color ink) {
        game.assets().drawBattleHero(game.batch(), HeroClass.WARRIOR, UtopiaAssets.BattlePose.IDLE,
                time, false, 340, 258, 300);
        game.assets().drawBattleEnemy(game.batch(), enemy, POSES[selected], time, true, 920, 258, 300);
        UiRenderer.text(game.batch(), game.font(), "SENTINEL / SIZE REFERENCE", 210, 240, ink);
        UiRenderer.text(game.batch(), game.font(), POSES[selected] + " / " +
                (Math.min(count() - 1, (int)(time / duration())) + 1) + "/" + count(), 810, 240, ink);
        for (UtopiaAssets.Direction direction : UtopiaAssets.Direction.values()) {
            float x = 150 + direction.ordinal() * 300;
            game.assets().drawWorldEnemy(game.batch(), enemy, direction, time, x, 103, 96);
            UiRenderer.text(game.batch(), game.font(), direction.name(), x - 25, 89, ink);
        }
    }

    private void drawChest(Color ink) {
        for (int i = 0; i < 4; i++) {
            float x = 60 + i * 300;
            game.batch().draw(game.assets().chestFrame(true, (i + .01f) * EnemyArt.CHEST_FRAME_DURATION),
                    x, 252, 160, 240);
            UiRenderer.text(game.batch(), game.font(), CHEST_STATES[i], x + 25, 240, ink);
        }
        game.batch().draw(game.assets().chestFrame(true, time), 608, 103, 64, 96);
        UiRenderer.text(game.batch(), game.font(), "OPENING PLAYBACK / GAME FRAME SIZE", 433, 89, ink);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
