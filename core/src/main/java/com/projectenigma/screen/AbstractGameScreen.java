package com.projectenigma.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.projectenigma.input.MouseUi;
import com.badlogic.gdx.Screen;
import com.projectenigma.ProjectEnigmaGame;

public abstract class AbstractGameScreen implements Screen {
    protected final ProjectEnigmaGame game;
    private InputProcessor inputProcessor;
    protected MouseUi mouseUi;
    protected ProgressionOverlay progressionUi;

    protected final void useProgression(ProgressionOverlay overlay) {
        progressionUi = overlay;
        inputProcessor = new InputMultiplexer(overlay, inputProcessor);
    }

    protected final boolean progressionVisible() { return progressionUi != null && progressionUi.visible(); }

    protected final MouseUi useMouse(Viewport viewport) {
        mouseUi = new MouseUi(viewport);
        mouseUi.onSound(game.sounds()::play);
        inputProcessor = new InputMultiplexer(mouseUi, inputProcessor);
        return mouseUi;
    }

    protected final void drawMouse() {
        if (progressionVisible()) progressionUi.draw(); else mouseUi.draw(game);
    }

    protected AbstractGameScreen(ProjectEnigmaGame game) {
        this.game = game;
    }

    protected final void useInput(InputProcessor processor) {
        inputProcessor = processor;
    }

    @Override
    public void show() {
        if (inputProcessor != null) {
            Gdx.input.setInputProcessor(inputProcessor);
        }
    }

    @Override
    public void hide() {
        if (progressionUi != null) progressionUi.cancelPointer();
        game.sounds().cancel(this);
        if (mouseUi != null) mouseUi.cancel();
        if (Gdx.graphics != null) Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        if (Gdx.input != null && Gdx.input.getInputProcessor() == inputProcessor) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void pause() {
        if (progressionUi != null) progressionUi.cancelPointer();
        game.sounds().cancel(this);
        if (mouseUi != null) mouseUi.cancel();
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        game.sounds().cancel(this);
    }
}
