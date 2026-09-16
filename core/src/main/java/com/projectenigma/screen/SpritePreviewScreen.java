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
import com.projectenigma.model.HeroClass;

/** Isolated animation inspection; opening this screen never creates or saves a run. */
public final class SpritePreviewScreen extends AbstractGameScreen {
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(1280, 720, camera);
    private final UtopiaAssets.BattlePose[] poses = UtopiaAssets.BattlePose.values();
    private final int[] counts = {4, 6, 8, 4, 3, 6};
    private HeroClass heroClass = HeroClass.WARRIOR;
    private int selected;
    private float time;
    private boolean playing = true;
    private boolean walking = true;
    private boolean light;

    public SpritePreviewScreen(ProjectEnigmaGame game) {
        super(game);
        useInput(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key >= Input.Keys.NUM_1 && key <= Input.Keys.NUM_6) { select(key - Input.Keys.NUM_1); return true; }
                if (key == Input.Keys.SPACE) { playing = !playing; return true; }
                if (key == Input.Keys.RIGHT) { step(); return true; }
                if (key == Input.Keys.ESCAPE) { game.showMenu(); return true; }
                return false;
            }
        });
        useMouse(viewport);
        for (int i = 0; i < poses.length; i++) {
            final int index = i;
            mouseUi.add(poses[i].name(), 40 + i * 202, 608, 190, 42, () -> select(index)).selected(() -> selected == index);
        }
        for (HeroClass role : HeroClass.values()) {
            mouseUi.add(role.displayName(), 40 + role.ordinal() * 242, 554, 230, 36,
                    () -> { heroClass = role; time = 0; }).selected(() -> heroClass == role);
        }
        mouseUi.add(() -> playing ? "Pause" : "Play", 40, 20, 180, 42, () -> playing = !playing);
        mouseUi.add("Next Frame", 238, 20, 180, 42, this::step);
        mouseUi.add(() -> walking ? "World: Walk" : "World: Idle", 436, 20, 220, 42, () -> walking = !walking);
        mouseUi.add("Change Background", 674, 20, 260, 42, () -> light = !light);
        mouseUi.add("Main Menu", 952, 20, 240, 42, game::showMenu);
    }

    private void select(int index) { selected = index; time = 0; }
    private float frameDuration() { return selected == 5 ? .16f : .12f; }
    private void step() {
        playing = false;
        int frame = ((int)(time / frameDuration()) + 1) % counts[selected];
        time = (frame + .01f) * frameDuration();
    }

    @Override public void render(float delta) {
        if (playing) time = (time + Math.min(delta, .1f)) % (counts[selected] * frameDuration() + (selected == 0 ? 0 : .65f));
        ScreenUtils.clear(light ? .75f : .055f, light ? .80f : .075f, light ? .85f : .10f, 1);
        viewport.apply(); camera.update();
        Color ink = light ? Color.BLACK : Color.WHITE;
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(light ? Color.GRAY : Color.DARK_GRAY);
        shapes.line(140, 264, 1140, 264);
        shapes.line(90, 107, 1170, 107);
        shapes.end();
        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(Color.WHITE);
        game.batch().begin();
        UiRenderer.text(game.batch(), game.titleFont(), heroClass.displayName().toUpperCase(java.util.Locale.ROOT)
                + " / ANIMATION VIEWER", 40, 694, ink);
        game.assets().drawBattleHero(game.batch(), heroClass, poses[selected], time, false, 365, 258, 330);
        game.assets().drawBattleHero(game.batch(), heroClass, poses[selected], time, true, 900, 258, 330);
        UiRenderer.text(game.batch(), game.font(), "PLAYER", 320, 240, ink);
        UiRenderer.text(game.batch(), game.font(), "OPPONENT (MIRRORED)", 805, 240, ink);
        UtopiaAssets.Direction[] directions = UtopiaAssets.Direction.values();
        for (int i = 0; i < directions.length; i++) {
            float x = 150 + i * 300;
            game.assets().drawWorldHero(game.batch(), heroClass, directions[i], walking, time, x, 103, 96);
            UiRenderer.text(game.batch(), game.font(), directions[i].name(), x - 25, 89, ink);
        }
        UiRenderer.text(game.batch(), game.font(), poses[selected] + "  |  frame "
                + (Math.min(counts[selected] - 1, (int)(time / frameDuration())) + 1) + "/" + counts[selected]
                + "  |  1-6 select, Space pause, Right step", 40, 666, ink);
        game.batch().end();
        drawMouse();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
