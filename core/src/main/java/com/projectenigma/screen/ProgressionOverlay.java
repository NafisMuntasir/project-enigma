package com.projectenigma.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.projectenigma.*;
import com.projectenigma.audio.SoundCue;
import com.projectenigma.input.MouseUi;
import com.projectenigma.model.*;
import java.util.List;
import java.util.function.*;

/** Shared modal for dungeon, solo combat and authoritative PvP display data. */
public final class ProgressionOverlay extends InputAdapter {
    private final ProjectEnigmaGame game;
    private final Viewport viewport;
    private final MouseUi ui;
    private final Supplier<Hero> hero;
    private final BooleanSupplier manage;
    private final Function<Skill, String> reason;
    private final Consumer<Skill> use;
    private final Runnable technical;
    private final Supplier<String> technicalReason;
    private final Runnable changed;
    private boolean visible, upgrading, passives;
    private int selected, page;
    private static final int PAGE_SIZE = 6;

    public ProgressionOverlay(ProjectEnigmaGame game, Viewport viewport, Supplier<Hero> hero,
                              BooleanSupplier manage, Function<Skill, String> reason, Consumer<Skill> use,
                              Runnable technical, Supplier<String> technicalReason, Runnable changed) {
        this.game = game; this.viewport = viewport; this.hero = hero; this.manage = manage;
        this.reason = reason; this.use = use; this.technical = technical;
        this.technicalReason = technicalReason; this.changed = changed;
        ui = new MouseUi(viewport); ui.modal(() -> visible); ui.onSound(game.sounds()::play);
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int row = i;
            ui.add(() -> rowTitle(row), 90, 488 - i * 55, 495, 46, () -> selected = page * PAGE_SIZE + row)
                    .when(() -> visible && page * PAGE_SIZE + row < count())
                    .selected(() -> selected == page * PAGE_SIZE + row);
        }
        ui.add("Previous", 90, 120, 155, 42, () -> turnPage(-1)).when(() -> visible)
                .disabled(() -> page == 0 ? "First page." : "");
        ui.add("Next", 430, 120, 155, 42, () -> turnPage(1)).when(() -> visible)
                .disabled(() -> (page + 1) * PAGE_SIZE >= count() ? "Last page." : "");
        ui.add("General", 90, 560, 155, 42, () -> { passives = false; selected = page = 0; }).when(() -> visible && !upgrading)
                .selected(() -> !passives);
        ui.add("Passives", 260, 560, 155, 42, () -> { passives = true; selected = page = 0; }).when(() -> visible && !upgrading)
                .selected(() -> passives);
        ui.add(() -> "Technical: " + hero.get().skillName(), 660, 560, 530, 42,
                () -> { if (technicalReason.get().isEmpty()) { close(); technical.run(); } })
                .when(() -> visible && !upgrading).disabled(technicalReason);
        ui.add("Select Upgrade", 660, 130, 270, 48, this::choose).when(() -> visible && upgrading);
        ui.add("Use Skill", 660, 130, 230, 48, this::activate).when(() -> visible && !upgrading && !passives)
                .disabled(() -> reason.apply(selectedSkill()));
        for (int i = 0; i < 3; i++) {
            final int slot = i;
            ui.add("Assign slot " + (i + 1), 660 + i * 178, 200, 166, 42, () -> equip(slot))
                    .when(() -> visible && !upgrading && !passives && manage.getAsBoolean())
                    .disabled(() -> hero.get().progression().unlocked(selectedSkill()) ? "" : "Unlock this skill first.");
        }
        ui.add("Close [Esc]", 970, 62, 220, 42, this::close).when(() -> visible && !upgrading);
    }
    public boolean visible() { return visible; }
    public boolean upgrading() { return visible && upgrading; }
    public void openSkills() { visible = true; upgrading = passives = false; selected = page = 0; ui.cancel(); }
    public void openUpgrades() {
        if (hero.get().progression().pendingChoices <= 0) return;
        visible = upgrading = true; passives = false; selected = page = 0; ui.cancel();
    }
    public void close() { if (!upgrading) { visible = false; ui.cancel(); } }
    public void cancelPointer() { ui.cancel(); }
    private List<Progression.Choice> choices() { return hero.get().progression().choices(hero.get()); }
    private int count() { return upgrading ? choices().size() : passives ? PassiveUpgrade.values().length : Skill.values().length; }
    private Skill selectedSkill() { return Skill.values()[Math.min(selected, Skill.values().length - 1)]; }
    private void turnPage(int direction) { page = Math.max(0, Math.min((count() - 1) / PAGE_SIZE, page + direction)); selected = page * PAGE_SIZE; }
    private String rowTitle(int row) {
        int index = page * PAGE_SIZE + row;
        if (index >= count()) return "";
        if (upgrading) { var c = choices().get(index); return (c.skill() == null ? "PASSIVE  /  " : "UNLOCK  /  ") + c.title(); }
        if (passives) { var p = PassiveUpgrade.values()[index]; return p.title + "   " + hero.get().progression().rank(p) + "/" + p.maxRank; }
        Skill s = Skill.values()[index];
        var progress = hero.get().progression();
        return s.title + (progress.equipped(s) ? "  [EQUIPPED]" : progress.unlocked(s) ? "  [UNLOCKED]" : "  [LOCKED]");
    }
    private void choose() {
        if (!upgrading || !visible) return;
        String id = choices().get(Math.min(selected, count() - 1)).id();
        if (hero.get().progression().choose(hero.get(), id)) {
            game.sounds().play(SoundCue.POWER_UP);
            selected = page = 0;
            upgrading = hero.get().progression().pendingChoices > 0;
            visible = upgrading;
            changed.run();
        }
    }
    private void activate() {
        if (!visible || upgrading || passives) return;
        Skill skill = selectedSkill();
        if (reason.apply(skill).isEmpty()) { close(); use.accept(skill); }
    }
    private void equip(int slot) {
        if (manage.getAsBoolean() && hero.get().progression().equip(selectedSkill(), slot)) changed.run();
    }
    @Override public boolean keyDown(int key) {
        if (!visible) return false;
        ui.cancel();
        if (key == Input.Keys.ESCAPE || key == Input.Keys.L) close();
        else if (key == Input.Keys.TAB && !upgrading) { passives = !passives; selected = page = 0; }
        else if (key == Input.Keys.UP || key == Input.Keys.W) { selected = Math.floorMod(selected - 1, count()); page = selected / PAGE_SIZE; }
        else if (key == Input.Keys.DOWN || key == Input.Keys.S) { selected = (selected + 1) % count(); page = selected / PAGE_SIZE; }
        else if (key == Input.Keys.LEFT) turnPage(-1);
        else if (key == Input.Keys.RIGHT) turnPage(1);
        else if (key == Input.Keys.ENTER || key == Input.Keys.SPACE) { if (upgrading) choose(); else activate(); }
        else if (!upgrading && !passives && key >= Input.Keys.NUM_1 && key <= Input.Keys.NUM_3) equip(key - Input.Keys.NUM_1);
        else if (!upgrading && key == Input.Keys.T && technicalReason.get().isEmpty()) { close(); technical.run(); }
        return true;
    }
    @Override public boolean keyUp(int key) { return visible; }
    @Override public boolean keyTyped(char c) { return visible; }
    @Override public boolean mouseMoved(int x, int y) { if (!visible) return false; ui.mouseMoved(x, y); return true; }
    @Override public boolean touchDown(int x, int y, int p, int b) { if (!visible) return false; ui.touchDown(x, y, p, b); return true; }
    @Override public boolean touchUp(int x, int y, int p, int b) { if (!visible) return false; ui.touchUp(x, y, p, b); return true; }
    @Override public boolean touchDragged(int x, int y, int p) { return visible; }
    @Override public boolean scrolled(float x, float y) { if (!visible) return false; turnPage(y > 0 ? 1 : -1); return true; }

    public void draw() {
        if (!visible) return;
        Hero h = hero.get();
        viewport.apply(); viewport.getCamera().update();
        var shapes = game.shapes(); shapes.setProjectionMatrix(viewport.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.VOID); shapes.rect(40, 30, 1200, 660);
        shapes.setColor(Palette.PANEL); shapes.rect(60, 50, 1160, 620);
        shapes.setColor(Palette.BLUE); shapes.rect(60, 662, 1160, 8);
        shapes.setColor(Palette.PANEL_LIGHT); shapes.rect(630, 260, 560, 280);
        shapes.end();
        game.batch().setProjectionMatrix(viewport.getCamera().combined); game.batch().begin();
        UiRenderer.text(game.batch(), game.titleFont(), upgrading ? "LEVEL UP - SELECT AN UPGRADE" : "SKILLS / AUGMENTATIONS", 90, 645, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "LEVEL " + h.level + "   |   HP " + h.health + "/" + h.maxHealth + "   |   EN " + h.mana + "/" + h.maxMana
                + (upgrading ? "   |   Choices remaining: " + h.progression().pendingChoices : "   |   3 general skill slots"), 90, 616, Palette.BLUE_LIGHT);
        if (upgrading) UiRenderer.text(game.batch(), game.font(), "Choose any one option. Your normal level bonuses are already applied.", 90, 579, Palette.GOLD);
        else UiRenderer.text(game.batch(), game.font(), "CLASS TECH / 3 EN / " + h.skillMultiplier() + "x ATK / no cooldown", 660, 548, Palette.MUTED);
        String title, description, meta;
        if (upgrading) {
            var c = choices().get(Math.min(selected, count() - 1)); title = c.title(); description = c.description();
            meta = c.skill() == null ? "PASSIVE UPGRADE / APPLIES IMMEDIATELY" : skillMeta(c.skill(), h);
        } else if (passives) {
            var p = PassiveUpgrade.values()[selected]; title = p.title; description = p.description;
            meta = "PASSIVE / RANK " + h.progression().rank(p) + " OF " + p.maxRank;
        } else {
            Skill s = selectedSkill(); title = s.title; description = s.description; meta = skillMeta(s, h);
        }
        UiRenderer.text(game.batch(), game.mediumFont(), title, 655, 512, Palette.TEXT);
        UiRenderer.wrappedText(game.batch(), game.font(), meta, 655, 477, 505, Palette.BLUE_LIGHT);
        UiRenderer.wrappedText(game.batch(), game.font(), description, 655, 419, 505, Palette.TEXT);
        if (!upgrading && !passives) {
            String state = h.progression().unlocked(selectedSkill()) ? reason.apply(selectedSkill()) : "LOCKED / Available as a level-up choice from level " + selectedSkill().level;
            UiRenderer.wrappedText(game.batch(), game.font(), state.isEmpty() ? "Ready to use." : state, 655, 302, 505, Palette.GOLD);
        }
        UiRenderer.text(game.batch(), game.font(), "Page " + (page + 1) + " / " + ((count() + PAGE_SIZE - 1) / PAGE_SIZE), 277, 146, Palette.MUTED);
        if (!upgrading) {
            String slots = "LOADOUT: ";
            for (int i = 0; i < 3; i++) { Skill s = Progression.skill(h.progression().equipped.get(i)); slots += (i == 0 ? "" : " / ") + (i + 1) + ": " + (s == null ? "empty" : s.title); }
            UiRenderer.wrappedText(game.batch(), game.font(), slots, 655, 115, 525, Palette.MUTED);
        }
        UiRenderer.text(game.batch(), game.font(), upgrading ? "W/S: select | Left/Right: page | Enter: choose" : "W/S: select | Enter: use | Tab: category | 1-3: equip in field | T: tech", 90, 78, Palette.MUTED);
        game.batch().end(); ui.draw(game);
    }
    private String skillMeta(Skill s, Hero hero) {
        return "ACTIVE / " + s.category + "\n" + s.cost + " EN  |  " + (s.combatUsable() ? hero.progression().cooldown(s) + " action cooldown" : "30 tile cooldown") + "  |  Unlock level " + s.level;
    }
}
