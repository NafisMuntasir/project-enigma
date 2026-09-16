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
import com.projectenigma.UtopiaAssets;
import com.projectenigma.model.HeroClass;

import java.util.function.Consumer;

public final class ClassSelectScreen extends AbstractGameScreen {
    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private final HeroClass[] classes = HeroClass.values();
    private final Consumer<HeroClass> onConfirm;
    private final Runnable onCancel;
    private final String title;
    private int selected;
    private float time;
    private boolean submitted;

    /** Single-player: confirming starts a new dungeon run immediately, as before. */
    public ClassSelectScreen(ProjectEnigmaGame game) {
        this(game, game::startNewGame, game::showMenu, "CHOOSE YOUR OPERATIVE");
    }

    /**
     * Reused for PvP class selection (Phase 1). {@code onConfirm} is called
     * once with the chosen class instead of starting a dungeon run --
     * {@code ProjectEnigmaGame} wires this to its PvP class-selection handler.
     * Esc calls {@code game::leavePvPMatch} (not plain {@code showMenu}) so
     * an already-open {@code PvPServer}/{@code PvPClient} is closed instead
     * of leaked in the background.
     */
    public ClassSelectScreen(ProjectEnigmaGame game, Consumer<HeroClass> onConfirm) {
        this(game, onConfirm, "CHOOSE YOUR PVP OPERATIVE");
    }

    /**
     * Same as {@link #ClassSelectScreen(ProjectEnigmaGame, Consumer)} with a
     * caller-supplied title. Used by Race-to-PvP ({@code
     * ProjectEnigmaGame.showRaceClassSelect()}) so the screen reads "...FOR
     * THE PVP RACE" instead of the classic PvP wording, while still being
     * treated as a PvP-flavored selection (the confirm button reads "Ready"
     * whenever the title contains "PVP" -- see {@link #isPvP()}) and still
     * routing Esc through {@code game::leavePvPMatch} for symmetric
     * network cleanup.
     */
    public ClassSelectScreen(ProjectEnigmaGame game, Consumer<HeroClass> onConfirm, String title) {
        this(game, onConfirm, game::leavePvPMatch, title);
    }

    private ClassSelectScreen(ProjectEnigmaGame game, Consumer<HeroClass> onConfirm, Runnable onCancel, String title) {
        super(game);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.title = title;
        camera = new OrthographicCamera();
        viewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, camera);
        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (submitted && keycode != Input.Keys.ESCAPE) return true;
                if (keycode == Input.Keys.LEFT || keycode == Input.Keys.A
                        || keycode == Input.Keys.UP || keycode == Input.Keys.W) {
                    selected = Math.floorMod(selected - 1, classes.length);
                    return true;
                }
                if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.D
                        || keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
                    selected = (selected + 1) % classes.length;
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    confirmSelection();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    ClassSelectScreen.this.onCancel.run();
                    return true;
                }
                return false;
            }
        });
        useMouse(viewport);
        for (int i = 0; i < classes.length; i++) {
            final int index = i;
            mouseUi.add("", 45 + i * 246, 160, 206, 410, () -> selected = index).outline()
                    .selected(() -> selected == index).disabled(() -> submitted ? "Waiting for the other player." : "");
        }
        mouseUi.add("Back", 380, 55, 200, 48, onCancel);
        mouseUi.add(() -> submitted ? "Waiting for opponent..." : (isPvP() ? "Ready" : "Begin"),
                620, 55, 280, 48, this::confirmSelection)
                .disabled(() -> submitted ? "Waiting for the other player." : "");
    }

    private boolean isPvP() { return title.contains("PVP"); }
    private void confirmSelection() {
        if (submitted) return;
        submitted = true;
        onConfirm.accept(classes[selected]);
    }

    @Override
    public void render(float delta) {
        time += Math.min(delta, 0.1f);
        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(1f, 1f, 1f, 1f);
        game.batch().begin();
        game.batch().draw(game.assets().menuBackground(), 0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        game.batch().end();

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.04f, 0.07f, 0.10f, 0.46f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        for (int i = 0; i < classes.length; i++) {
            float x = 45f + i * 246f;
            shapes.setColor(i == selected ? Palette.PANEL_LIGHT : Palette.PANEL);
            shapes.rect(x, 160f, 206f, 410f);
            shapes.setColor(i == selected ? Palette.ACCENT : Palette.BLUE);
            shapes.rect(x, 560f, 206f, 10f);
            if (i == selected) {
                shapes.setColor(Palette.ACCENT);
                shapes.rect(x, 160f, 6f, 400f);
            }
        }
        shapes.end();
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), title, 640f, 664f, Palette.TEXT);
        UiRenderer.centeredText(game.batch(), game.font(), "Each operative has a different combat rhythm.", 640f, 615f, Palette.MUTED);
        for (int i = 0; i < classes.length; i++) {
            HeroClass heroClass = classes[i];
            float centerX = 148f + i * 246f;
            game.batch().setColor(i == selected ? 1f : 0.72f,
                    i == selected ? 1f : 0.76f,
                    i == selected ? 1f : 0.80f, 1f);
            game.batch().draw(game.assets().battleHeroFrame(heroClass, UtopiaAssets.BattlePose.IDLE, time, false),
                    centerX - 64f, 356f, 128f, 192f);
            game.batch().setColor(1f, 1f, 1f, 1f);
            UiRenderer.centeredText(game.batch(), game.mediumFont(), heroClass.displayName(), centerX, 335f, Palette.TEXT);
            UiRenderer.centeredText(game.batch(), game.font(), heroClass.description(), centerX, 302f, Palette.MUTED);
            UiRenderer.centeredText(game.batch(), game.font(), "HP " + heroClass.health() + "   EN " + heroClass.mana(), centerX, 244f, Palette.TEXT);
            UiRenderer.centeredText(game.batch(), game.font(), "ATK " + heroClass.attack() + "   DEF " + heroClass.defense(), centerX, 212f, Palette.TEXT);
        }
        UiRenderer.centeredText(game.batch(), game.font(), "Click a card, then Begin / Ready. Keyboard: A/D choose, Enter confirm, Esc back.",
                640f, 32f, Palette.TEXT);
        game.batch().end();
        drawMouse();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
