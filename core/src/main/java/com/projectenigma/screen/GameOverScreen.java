package com.projectenigma.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.Palette;
import com.projectenigma.UiRenderer;

public final class GameOverScreen extends AbstractGameScreen {
    private static final String[] OPTIONS = {"New Run", "Main Menu", "Quit"};
    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private int selected;

    public GameOverScreen(ProjectEnigmaGame game) {
        super(game);
        camera = new OrthographicCamera();
        viewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, camera);
        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
                    selected = Math.floorMod(selected - 1, OPTIONS.length);
                    return true;
                }
                if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
                    selected = (selected + 1) % OPTIONS.length;
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    activateSelection();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    game.showMenu();
                    return true;
                }
                return false;
            }
        });
        useMouse(viewport);
        for (int i = 0; i < OPTIONS.length; i++) {
            final int index = i;
            mouseUi.add(OPTIONS[i], 480, 330 - i * 66, 320, 48, () -> { selected = index; activateSelection(); })
                    .large().hover(() -> selected = index).selected(() -> selected == index);
        }
    }

    private void activateSelection() {
        if (selected == 0) game.showClassSelect();
        else if (selected == 1) game.showMenu();
        else game.quit();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(0.55f, 0.58f, 0.62f, 1f);
        game.batch().begin();
        game.batch().draw(game.assets().menuBackground(), 0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        game.batch().end();
        game.batch().setColor(1f, 1f, 1f, 1f);

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.07f, 0.04f, 0.05f, 0.46f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 390f, 125f, 500f, 485f, Palette.PANEL);
        shapes.setColor(Palette.DANGER);
        shapes.rect(390f, 600f, 500f, 10f);
        shapes.setColor(Palette.BLUE);
        shapes.rect(390f, 125f, 7f, 475f);
        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "THE DUNGEON CLAIMS YOU", 640f, 535f, Palette.DANGER);
        UiRenderer.centeredText(game.batch(), game.mediumFont(), "The run has ended, but the layout will never repeat.",
                640f, 475f, Palette.MUTED);

        UiRenderer.centeredText(game.batch(), game.font(), "W/S or arrows: select    Enter: confirm",
                640f, 155f, Palette.MUTED);
        game.batch().end();
        drawMouse();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
