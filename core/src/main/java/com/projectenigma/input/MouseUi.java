package com.projectenigma.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.projectenigma.Palette;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.UiRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import com.projectenigma.audio.SoundCue;

/** Screen-local controls: the same bounds are used for rendering and input. */
public final class MouseUi extends InputAdapter {
    public static final class Button {
        final Rectangle bounds;
        final Supplier<String> label;
        final Runnable action;
        BooleanSupplier visible = () -> true;
        BooleanSupplier selected = () -> false;
        Supplier<String> reason = () -> "";
        Runnable hover = () -> {};
        boolean outline;
        boolean passive;
        boolean large;
        Button(Supplier<String> label, float x, float y, float w, float h, Runnable action) {
            this.label = label; bounds = new Rectangle(x, y, w, h); this.action = action;
        }
        public Button when(BooleanSupplier value) { visible = value; return this; }
        public Button disabled(Supplier<String> value) { reason = value; return this; }
        public Button selected(BooleanSupplier value) { selected = value; return this; }
        public Button hover(Runnable value) { hover = value; return this; }
        public Button outline() { outline = true; return this; }
        public Button block() { passive = true; return this; }
        public Button large() { large = true; return this; }
    }
    private final Viewport viewport;
    private final List<Button> buttons = new ArrayList<>();
    private final ClickLatch<Button> latch = new ClickLatch<>();
    private final Vector2 pointer = new Vector2();
    private Button lastHovered;
    private Consumer<SoundCue> sound = cue -> {};
    public void onSound(Consumer<SoundCue> sound) { this.sound = sound; }
    private BooleanSupplier modal = () -> false;
    private BooleanSupplier worldHand = () -> false;

    public MouseUi(Viewport viewport) { this.viewport = viewport; }
    public Button add(String label, float x, float y, float w, float h, Runnable action) {
        return add(() -> label, x, y, w, h, action);
    }
    public Button add(Supplier<String> label, float x, float y, float w, float h, Runnable action) {
        Button button = new Button(label, x, y, w, h, action); buttons.add(button); return button;
    }
    public void modal(BooleanSupplier value) { modal = value; }
    public void worldHand(BooleanSupplier value) { worldHand = value; }
    public void cancel() { latch.cancel(); }

    public static boolean containsScreen(Viewport viewport, int x, int y, int height) {
        int bottomY = height - 1 - y;
        return x >= viewport.getScreenX() && x < viewport.getScreenX() + viewport.getScreenWidth()
                && bottomY >= viewport.getScreenY() && bottomY < viewport.getScreenY() + viewport.getScreenHeight();
    }
    private Button hit(int x, int y) {
        if (!containsScreen(viewport, x, y, Gdx.graphics.getHeight())) return null;
        viewport.unproject(pointer.set(x, y));
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.visible.getAsBoolean() && b.bounds.contains(pointer)) return b;
        }
        return null;
    }
    @Override public boolean keyDown(int keycode) { cancel(); return false; }
    @Override public boolean mouseMoved(int x, int y) {
        Button b = hit(x, y);
        if (b != lastHovered && b != null && !b.passive && b.reason.get().isEmpty()) sound.accept(SoundCue.HOVER);
        lastHovered = b;
        if (b != null) b.hover.run();
        return b != null || modal.getAsBoolean();
    }
    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        Button b = hit(x, y);
        if (pointer == 0 && button == Input.Buttons.LEFT) latch.press(b, b != null && !b.passive && b.reason.get().isEmpty());
        return b != null || modal.getAsBoolean();
    }
    @Override public boolean touchUp(int x, int y, int pointer, int button) {
        if (pointer != 0 || button != Input.Buttons.LEFT) return modal.getAsBoolean();
        Button b = hit(x, y);
        boolean activate = latch.release(b, b != null && !b.passive && b.reason.get().isEmpty());
        if (activate) { sound.accept(SoundCue.CLICK); b.action.run(); }
        return activate || b != null || modal.getAsBoolean();
    }
    @Override public boolean touchDragged(int x, int y, int pointer) { return modal.getAsBoolean(); }

    public void draw(ProjectEnigmaGame game) {
        viewport.apply(); viewport.getCamera().update();
        Button hovered = hit(Gdx.input.getX(), Gdx.input.getY());
        Gdx.graphics.setSystemCursor((hovered != null && !hovered.passive && hovered.reason.get().isEmpty())
                || (hovered == null && !modal.getAsBoolean() && worldHand.getAsBoolean())
                ? Cursor.SystemCursor.Hand : Cursor.SystemCursor.Arrow);
        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Button b : buttons) {
            if (!b.visible.getAsBoolean() || b.passive) continue;
            Rectangle r = b.bounds;
            boolean enabled = b.reason.get().isEmpty();
            boolean active = b == hovered || b.selected.getAsBoolean();
            if (!b.outline) {
                shapes.setColor(enabled && latch.isPressed(b) && b == hovered ? Palette.BLUE
                        : enabled && active ? Palette.PANEL_LIGHT : Palette.WALL);
                shapes.rect(r.x, r.y, r.width, r.height);
            }
            if (active || (latch.isPressed(b) && b == hovered)) {
                shapes.setColor(enabled ? Palette.BLUE_LIGHT : Palette.MUTED);
                shapes.rect(r.x, r.y, r.width, 2); shapes.rect(r.x, r.y + r.height - 2, r.width, 2);
                shapes.rect(r.x, r.y, 2, r.height); shapes.rect(r.x + r.width - 2, r.y, 2, r.height);
            }
        }
        String tip = hovered == null ? "" : hovered.reason.get();
        float tipX = Math.min(pointer.x, UiRenderer.WIDTH - 390), tipY = Math.min(pointer.y + 24, UiRenderer.HEIGHT - 44);
        if (!tip.isEmpty()) { shapes.setColor(Palette.PANEL); shapes.rect(tipX, tipY, 380, 34); }
        shapes.end();
        game.batch().setProjectionMatrix(viewport.getCamera().combined);
        game.batch().begin();
        for (Button b : buttons) if (b.visible.getAsBoolean() && !b.label.get().isEmpty()) {
            UiRenderer.centeredText(game.batch(), b.large ? game.mediumFont() : game.font(), b.label.get(), b.bounds.x + b.bounds.width / 2,
                    b.bounds.y + b.bounds.height / 2 + (b.large ? 9 : 7), b.reason.get().isEmpty() ? Palette.TEXT : Palette.MUTED);
        }
        if (!tip.isEmpty()) UiRenderer.text(game.batch(), game.font(), tip, tipX + 10, tipY + 23, Palette.TEXT);
        game.batch().end();
    }
}
