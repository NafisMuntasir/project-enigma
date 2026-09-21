package com.projectenigma.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.projectenigma.Palette;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.UiRenderer;
import com.projectenigma.input.MouseUi;
import com.projectenigma.model.*;
import java.util.List;
import java.util.function.*;

/** The same action layout, key bindings and item menu in solo and multiplayer.
 * Screens supply turn locks and submit requests; no combat rules live here. */
public final class CombatMenu {
    private static final BattleAction[] ACTIONS = {BattleAction.ATTACK, BattleAction.SKILL, BattleAction.GUARD, BattleAction.RUN};
    private static final int PAGE_SIZE = 6;
    private final ProjectEnigmaGame game;
    private final MouseUi mouse;
    private final Supplier<Hero> hero;
    private final Function<BattleAction, String> reason;
    private final Consumer<BattleAction> act;
    private final Consumer<Item> useItem;
    private final Runnable skills;
    private int selected, itemSelection, page;
    private boolean itemsVisible;

    public CombatMenu(ProjectEnigmaGame game, MouseUi mouse, Supplier<Hero> hero, BooleanSupplier active,
                      Function<BattleAction, String> reason, Consumer<BattleAction> act,
                      Consumer<Item> useItem, Runnable skills) {
        this.game = game; this.mouse = mouse; this.hero = hero; this.reason = reason;
        this.act = act; this.useItem = useItem; this.skills = skills;
        mouse.modal(() -> itemsVisible);
        for (int i = 0; i < ACTIONS.length; i++) {
            final int index = i;
            mouse.add((i + 1) + " " + ACTIONS[i].label(), 37 + (i % 3) * 155, i < 3 ? 105 : 42, 145, 48,
                    () -> { selected = index; act.accept(ACTIONS[index]); })
                    .when(() -> active.getAsBoolean() && !itemsVisible).hover(() -> selected = index)
                    .selected(() -> selected == index).disabled(() -> reason.apply(ACTIONS[index]));
        }
        mouse.add("Skills [L]", 192, 42, 145, 48, skills)
                .when(() -> active.getAsBoolean() && !itemsVisible);
        mouse.add("Items [I]", 347, 42, 145, 48, this::openItems)
                .when(() -> active.getAsBoolean() && !itemsVisible);
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int row = i;
            mouse.add(() -> label(row), 505, 480 - i * 50, 620, 42,
                    () -> { itemSelection = page * PAGE_SIZE + row; useSelected(); })
                    .when(() -> itemsVisible && index(row) < choices().size())
                    .hover(() -> itemSelection = index(row)).selected(() -> itemSelection == index(row))
                    .disabled(() -> itemReason(index(row)));
        }
        mouse.add("Close Items", 505, 110, 220, 44, this::closeItems).when(() -> itemsVisible);
        mouse.add("Previous", 800, 110, 150, 44, () -> { page--; itemSelection = page * PAGE_SIZE; })
                .when(() -> itemsVisible).disabled(() -> page == 0 ? "First page." : "");
        mouse.add("Next", 975, 110, 150, 44, () -> { page++; itemSelection = page * PAGE_SIZE; })
                .when(() -> itemsVisible).disabled(() -> (page + 1) * PAGE_SIZE >= choices().size() ? "Last page." : "");
    }
    public boolean itemsVisible() { return itemsVisible; }
    public void closeItems() { itemsVisible = false; mouse.cancel(); }
    private void openItems() { mouse.cancel(); itemsVisible = true; itemSelection = page = 0; }
    public String description() { return ACTIONS[selected].description(); }
    private List<Item> choices() {
        Hero h = hero.get();
        return h.inventory == null ? List.of() : h.inventory.stream()
                .filter(i -> i != null && i.isConsumable() && i.quantity > 0).toList();
    }
    private int index(int row) { return page * PAGE_SIZE + row; }
    private String label(int row) {
        List<Item> items = choices(); int i = index(row);
        return i >= items.size() ? "" : items.get(i).name + " x" + items.get(i).quantity;
    }
    private String itemReason(int index) {
        String lock = reason.apply(BattleAction.ATTACK);
        if (!lock.isEmpty()) return lock;
        List<Item> items = choices();
        if (index >= items.size()) return "Item unavailable.";
        return hero.get().canUseItem(items.get(index)) ? "" : "Cannot use this item right now.";
    }
    private void useSelected() {
        if (!itemReason(itemSelection).isEmpty()) return;
        Item item = choices().get(itemSelection);
        closeItems(); useItem.accept(item);
    }
    public boolean keyDown(int key) {
        if (itemsVisible) {
            List<Item> items = choices();
            if (key == Input.Keys.ESCAPE || key == Input.Keys.I) closeItems();
            else if (!items.isEmpty()) {
                if (key == Input.Keys.W || key == Input.Keys.UP) itemSelection = Math.floorMod(itemSelection - 1, items.size());
                if (key == Input.Keys.S || key == Input.Keys.DOWN) itemSelection = (itemSelection + 1) % items.size();
                if (key == Input.Keys.RIGHT) itemSelection = Math.min(items.size() - 1, itemSelection + PAGE_SIZE);
                if (key == Input.Keys.LEFT) itemSelection = Math.max(0, itemSelection - PAGE_SIZE);
                page = itemSelection / PAGE_SIZE;
                if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) useSelected();
            }
            return true;
        }
        if (key == Input.Keys.I) { openItems(); return true; }
        if (key == Input.Keys.L) { skills.run(); return true; }
        if (key == Input.Keys.W || key == Input.Keys.UP) selected = Math.floorMod(selected - 1, ACTIONS.length);
        else if (key == Input.Keys.S || key == Input.Keys.DOWN) selected = (selected + 1) % ACTIONS.length;
        else if (key >= Input.Keys.NUM_1 && key <= Input.Keys.NUM_4) { selected = key - Input.Keys.NUM_1; act.accept(ACTIONS[selected]); }
        else if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) act.accept(ACTIONS[selected]);
        else if (key == Input.Keys.ESCAPE) act.accept(BattleAction.RUN);
        else return false;
        return true;
    }
    public void drawItems() {
        if (!itemsVisible) return;
        List<Item> items = choices();
        itemSelection = Math.min(itemSelection, Math.max(0, items.size() - 1)); page = itemSelection / PAGE_SIZE;
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, .8f); shapes.rect(0, 0, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 470, 90, 680, 530, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT); shapes.rect(470, 610, 680, 10); shapes.end();
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "COMBAT ITEMS", 810, 578, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "Using an item spends your turn. | W/S: select | Enter: use | I/Esc: close", 495, 545, Palette.MUTED);
        if (items.isEmpty()) UiRenderer.centeredText(game.batch(), game.mediumFont(), "No consumable items.", 810, 400, Palette.MUTED);
        else UiRenderer.wrappedText(game.batch(), game.font(), items.get(itemSelection).description, 505, 207, 620, Palette.TEXT);
        game.batch().end(); Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
