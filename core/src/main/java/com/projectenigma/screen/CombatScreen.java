package com.projectenigma.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.projectenigma.ProjectEnigmaGame;
import com.projectenigma.Palette;
import com.projectenigma.UiRenderer;
import com.projectenigma.UtopiaAssets;
import com.projectenigma.model.BattleAction;
import com.projectenigma.audio.SoundCue;
import com.projectenigma.audio.CombatAudio;
import com.projectenigma.model.ActionAvailability;
import com.projectenigma.model.BattleEngine;
import com.projectenigma.model.BattleOutcome;
import com.projectenigma.model.DungeonEnemy;
import com.projectenigma.model.GameSession;
import com.projectenigma.model.Item;
import com.projectenigma.model.TurnResult;

import java.util.ArrayList;
import java.util.List;

public final class CombatScreen extends AbstractGameScreen {
    private static final int MAX_LOG_LINES = 7;
    private static final float HERO_ACTION_PHASE = 0.86f;
    private static final float TURN_ANIMATION_DURATION = 1.58f;

    private final GameSession session;
    private final DungeonEnemy enemy;
    private final BattleEngine engine;
    private final BattleAction[] actions = { BattleAction.ATTACK, BattleAction.SKILL, BattleAction.GUARD, BattleAction.RUN };
    private final List<String> logLines = new ArrayList<>();
    private final OrthographicCamera camera;
    private final FitViewport viewport;

    private int selected;
    private BattleOutcome outcome = BattleOutcome.ONGOING;
    private float time;
    private float turnAnimationTime = TURN_ANIMATION_DURATION;
    private BattleAction animatedHeroAction = BattleAction.ATTACK;
    private boolean animatedEnemyReply;
    private boolean itemsVisible;
    private int itemSelection;

    public CombatScreen(ProjectEnigmaGame game, GameSession session, DungeonEnemy enemy) {
        super(game);
        this.session = session;
        this.enemy = enemy;
        engine = new BattleEngine(enemy.id ^ session.stepsTaken ^ session.floorSeed);
        camera = new OrthographicCamera();
        viewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, camera);
        addLog("A " + enemy.type.displayName() + " blocks the passage.");
        addLog("Choose an action.");

        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (turnAnimationTime < TURN_ANIMATION_DURATION) {
                    return true;
                }
                if (itemsVisible) {
                    return handleItemsInput(keycode);
                }
                if (outcome != BattleOutcome.ONGOING) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
                        leaveBattle();
                        return true;
                    }
                    return false;
                }
                if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
                    selected = Math.floorMod(selected - 1, actions.length);
                    return true;
                }
                if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
                    selected = (selected + 1) % actions.length;
                    return true;
                }
                if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_4) {
                    selected = keycode - Input.Keys.NUM_1;
                    performSelectedAction();
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                    performSelectedAction();
                    return true;
                }
                if (keycode == Input.Keys.I) {
                    itemsVisible = true;
                    itemSelection = 0;
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    selected = BattleAction.RUN.ordinal();
                    performSelectedAction();
                    return true;
                }
                return false;
            }
        });
        useMouse(viewport).modal(() -> itemsVisible);
        for (int i = 0; i < actions.length; i++) {
            final int index = i;
            mouseUi.add((i + 1) + " " + actions[i].label(), 37 + (i % 3) * 155, i < 3 ? 105 : 42, 145, 48,
                    () -> { selected = index; performSelectedAction(); })
                    .when(() -> outcome == BattleOutcome.ONGOING && !itemsVisible).hover(() -> selected = index)
                    .selected(() -> selected == index).disabled(() -> actionReason(actions[index]));
        }
        mouseUi.add("Items [I]", 347, 42, 145, 48, () -> { itemsVisible = true; itemSelection = 0; })
                .when(() -> outcome == BattleOutcome.ONGOING && !itemsVisible && turnAnimationTime >= TURN_ANIMATION_DURATION);
        for (int i = 0; i < 8; i++) {
            final int index = i;
            mouseUi.add(() -> itemLabel(index), 550, 525 - i * 52, 600, 44, () -> { itemSelection = index; useSelectedCombatItem(); })
                    .when(() -> itemsVisible && index < combatItemChoices().size())
                    .hover(() -> itemSelection = index)
                    .selected(() -> itemSelection == index)
                    .disabled(() -> itemReason(index));
        }
        mouseUi.add("Close Items", 550, 90, 240, 44, () -> itemsVisible = false)
                .when(() -> itemsVisible);
        mouseUi.add("Continue", 130, 80, 270, 52, this::leaveBattle).when(() -> outcome != BattleOutcome.ONGOING && !itemsVisible)
                .disabled(() -> turnAnimationTime < TURN_ANIMATION_DURATION ? "Finishing animation..." : "");
    }

    private List<Item> combatItemChoices() {
        List<Item> choices = new ArrayList<>();
        if (session.hero.inventory != null) {
            for (Item item : session.hero.inventory) {
                if (item != null && item.isConsumable() && item.quantity > 0) choices.add(item);
            }
        }
        return choices;
    }

    private String itemLabel(int index) {
        List<Item> choices = combatItemChoices();
        if (index >= choices.size()) return "";
        Item item = choices.get(index);
        return (index == itemSelection ? "> " : "  ") + item.name + " x" + item.quantity;
    }

    private String itemReason(int index) {
        if (!itemsVisible) return "";
        List<Item> choices = combatItemChoices();
        if (index >= choices.size()) return "";
        if (turnAnimationTime < TURN_ANIMATION_DURATION) return "Finishing animation...";
        return session.hero.canUseItem(choices.get(index)) ? "" : "Cannot use this item right now.";
    }

    private boolean handleItemsInput(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.I) {
            itemsVisible = false;
            return true;
        }
        List<Item> choices = combatItemChoices();
        if (choices.isEmpty()) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
                addLog("No picked-up items available.");
                return true;
            }
            return keycode != Input.Keys.ESCAPE;
        }
        if (keycode == Input.Keys.UP || keycode == Input.Keys.W) {
            itemSelection = Math.floorMod(itemSelection - 1, choices.size());
            return true;
        }
        if (keycode == Input.Keys.DOWN || keycode == Input.Keys.S) {
            itemSelection = (itemSelection + 1) % choices.size();
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            useSelectedCombatItem();
            return true;
        }
        return true;
    }

    private void useSelectedCombatItem() {
        List<Item> choices = combatItemChoices();
        if (choices.isEmpty()) { addLog("No picked-up items available."); return; }
        if (itemSelection >= choices.size()) itemSelection = 0;
        Item item = choices.get(itemSelection);
        if (!session.hero.canUseItem(item)) {
            addLog(itemReason(itemSelection));
            return;
        }
        int restored = session.hero.useItem(item);
        if (restored <= 0) {
            addLog("That item could not be used.");
            return;
        }
        itemsVisible = false;
        addLog(session.hero.displayName() + " uses " + item.name + " and restores " + restored
                + (item.type == Item.Type.HEALTH ? " HP." : " EN."));
        game.sounds().schedule(this, item.type == Item.Type.HEALTH ? SoundCue.HEAL : SoundCue.POWER_UP, 0);
        animatedHeroAction = BattleAction.SKILL;
        animatedEnemyReply = false;
        turnAnimationTime = 0f;

        int healthBeforeReply = session.hero.health;
        TurnResult enemyTurn = engine.resolve(enemy, session.hero, BattleAction.ATTACK);
        CombatAudio.queue(game.sounds(), this, CombatAudio.changes(false, session.hero.health < healthBeforeReply, false), HERO_ACTION_PHASE);
        animatedEnemyReply = true;
        for (String message : enemyTurn.messages()) addLog(message);
        if (enemyTurn.outcome() == BattleOutcome.VICTORY) {
            outcome = BattleOutcome.DEFEAT;
            addLog("You collapse in the dungeon.");
            game.saves().deleteSave();
            addLog("Press Enter to continue.");
        }
    }

    private String actionReason(BattleAction action) {
        if (outcome != BattleOutcome.ONGOING) return "Battle finished.";
        if (turnAnimationTime < TURN_ANIMATION_DURATION) return "Finishing animation...";
        return ActionAvailability.reason(session.hero, action);
    }

    /**
     * Resolves the hero's chosen action, then -- unless that action already
     * ended the fight -- resolves one automatic enemy ATTACK turn. This two-
     * call shape (rather than one combined call) is exactly what lets
     * {@code BattleEngine} also serve PvP, where the second call would
     * instead come from the network as the other player's own turn -- see
     * {@code BattleEngine}'s class Javadoc and {@code network.PvPMatch}.
     */
    private void performSelectedAction() {
        BattleAction action = actions[selected];
        String unavailable = actionReason(action);
        if (!unavailable.isEmpty()) { addLog(unavailable); return; }
        int heroHealthBefore = session.hero.health, enemyHealthBefore = enemy.health, manaBefore = session.hero.mana;
        TurnResult playerTurn = engine.resolve(session.hero, enemy, action);
        for (String message : playerTurn.messages()) {
            addLog(message);
        }
        if (!playerTurn.actionAccepted()) {
            return;
        }
        CombatAudio.queue(game.sounds(), this, CombatAudio.changes(session.hero.mana < manaBefore,
                enemy.health < enemyHealthBefore, session.hero.health > heroHealthBefore), 0);
        animatedHeroAction = action;
        animatedEnemyReply = false;
        turnAnimationTime = 0f;

        if (playerTurn.outcome() == BattleOutcome.VICTORY) {
            outcome = BattleOutcome.VICTORY;
            int levelBefore = session.hero.level;
            int healthBeforeReward = session.hero.health;
            for (String message : session.defeatEnemy(enemy)) {
                addLog(message);
            }
            if (session.hero.level > levelBefore) game.sounds().schedule(this, SoundCue.POWER_UP, .85f);
            if (session.hero.health > healthBeforeReward) game.sounds().schedule(this, SoundCue.HEAL, .60f);
            game.saveGame();
            addLog("Press Enter to return to the dungeon.");
            return;
        }
        if (playerTurn.outcome() == BattleOutcome.ESCAPED) {
            outcome = BattleOutcome.ESCAPED;
            game.saveGame();
            addLog("Press Enter to return to the dungeon.");
            return;
        }

        // ONGOING: covers a completed ATTACK/SKILL, a GUARD, an item use,
        // or a failed RUN attempt -- in every one of those cases the enemy still replies.
        int healthBeforeReply = session.hero.health;
        TurnResult enemyTurn = engine.resolve(enemy, session.hero, BattleAction.ATTACK);
        CombatAudio.queue(game.sounds(), this, CombatAudio.changes(false,
                session.hero.health < healthBeforeReply, false), HERO_ACTION_PHASE);
        animatedEnemyReply = true;
        for (String message : enemyTurn.messages()) {
            addLog(message);
        }
        if (enemyTurn.outcome() == BattleOutcome.VICTORY) {
            // "VICTORY" here is from the enemy's (attacker's) point of view: the hero was just defeated.
            outcome = BattleOutcome.DEFEAT;
            addLog("You collapse in the dungeon.");
            game.saves().deleteSave();
            addLog("Press Enter to continue.");
        } else {
            outcome = BattleOutcome.ONGOING;
        }
    }

    private void leaveBattle() {
        if (outcome == BattleOutcome.DEFEAT) {
            game.showGameOver();
        } else {
            game.showDungeon();
        }
    }

    private void addLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        logLines.add(message);
        while (logLines.size() > MAX_LOG_LINES) {
            logLines.remove(0);
        }
    }

    @Override
    public void render(float delta) {
        float safeDelta = Math.min(delta, 0.1f);
        time += safeDelta;
        turnAnimationTime = Math.min(TURN_ANIMATION_DURATION, turnAnimationTime + safeDelta);
        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();
        drawArena();
        drawCombatants();
        drawInterface();
        if (itemsVisible) drawItemsMenu();
        drawMouse();
    }

    private void drawArena() {
        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(1f, 1f, 1f, 1f);
        game.batch().begin();
        game.batch().draw(game.assets().battleBackground(), 0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        game.batch().end();
    }

    private void drawCombatants() {
        UtopiaAssets.BattlePose heroPose = UtopiaAssets.BattlePose.IDLE;
        UtopiaAssets.BattlePose enemyPose = UtopiaAssets.BattlePose.IDLE;
        float heroFrameTime = time;
        float enemyFrameTime = time;

        if (outcome == BattleOutcome.VICTORY) {
            enemyPose = UtopiaAssets.BattlePose.DEFEAT;
            enemyFrameTime = turnAnimationTime;
        } else if (outcome == BattleOutcome.DEFEAT && turnAnimationTime >= HERO_ACTION_PHASE) {
            heroPose = UtopiaAssets.BattlePose.DEFEAT;
            heroFrameTime = turnAnimationTime - HERO_ACTION_PHASE;
        }

        if (turnAnimationTime < HERO_ACTION_PHASE) {
            heroPose = poseFor(animatedHeroAction);
            heroFrameTime = turnAnimationTime;
            if (animatedHeroAction == BattleAction.ATTACK || animatedHeroAction == BattleAction.SKILL) {
                enemyPose = outcome == BattleOutcome.VICTORY
                        ? UtopiaAssets.BattlePose.DEFEAT
                        : UtopiaAssets.BattlePose.HURT;
                enemyFrameTime = turnAnimationTime;
            }
        } else if (turnAnimationTime < TURN_ANIMATION_DURATION && animatedEnemyReply) {
            enemyPose = UtopiaAssets.BattlePose.ATTACK;
            enemyFrameTime = turnAnimationTime - HERO_ACTION_PHASE;
            heroPose = outcome == BattleOutcome.DEFEAT
                    ? UtopiaAssets.BattlePose.DEFEAT
                    : UtopiaAssets.BattlePose.HURT;
            heroFrameTime = turnAnimationTime - HERO_ACTION_PHASE;
        }

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(1f, 1f, 1f, 1f);
        game.batch().begin();
        game.batch().draw(game.assets().battleHeroFrame(session.hero.heroClass, heroPose, heroFrameTime, false),
                184f, 205f, 192f, 288f);
        game.batch().draw(game.assets().battleEnemyFrame(enemy.type, enemyPose, enemyFrameTime, true),
                869f, 205f, 192f, 288f);
        game.batch().end();

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiRenderer.panel(shapes, 100f, 470f, 390f, 125f, Palette.PANEL);
        UiRenderer.panel(shapes, 790f, 470f, 390f, 125f, Palette.PANEL);
        shapes.setColor(Palette.BLUE);
        shapes.rect(100f, 585f, 390f, 10f);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(790f, 585f, 390f, 10f);
        UiRenderer.bar(shapes, 125f, 520f, 340f, 24f, session.hero.health, session.hero.maxHealth, Palette.HEALTH);
        UiRenderer.bar(shapes, 125f, 486f, 340f, 18f, session.hero.mana, session.hero.maxMana, Palette.MANA);
        UiRenderer.bar(shapes, 815f, 520f, 340f, 24f, enemy.health, enemy.maxHealth, Palette.DANGER);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().begin();
        UiRenderer.text(game.batch(), game.mediumFont(), session.hero.heroClass.displayName() + "  Lv " + session.hero.level,
                125f, 575f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "HP " + session.hero.health + "/" + session.hero.maxHealth
                + "   EN " + session.hero.mana + "/" + session.hero.maxMana, 125f, 558f, Palette.MUTED);
        UiRenderer.text(game.batch(), game.mediumFont(), enemy.type.displayName(), 815f, 575f, Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "HP " + enemy.health + "/" + enemy.maxHealth, 815f, 558f, Palette.MUTED);
        game.batch().end();
    }

    private static UtopiaAssets.BattlePose poseFor(BattleAction action) {
        return switch (action) {
            case ATTACK -> UtopiaAssets.BattlePose.ATTACK;
            case SKILL, POTION -> UtopiaAssets.BattlePose.SKILL;
            case GUARD, RUN -> UtopiaAssets.BattlePose.GUARD;
        };
    }

    private void drawInterface() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiRenderer.panel(shapes, 22f, 18f, 490f, 170f, Palette.PANEL);
        UiRenderer.panel(shapes, 530f, 18f, 728f, 170f, Palette.PANEL);
        shapes.end();

        game.batch().begin();
        UiRenderer.text(game.batch(), game.font(), actions[selected].description(), 550f, 174f, Palette.MUTED);
        float logY = 145f;
        for (String line : logLines) {
            UiRenderer.text(game.batch(), game.font(), line, 550f, logY, Palette.TEXT);
            logY -= 19f;
        }
        if (outcome == BattleOutcome.ONGOING) {
            UiRenderer.text(game.batch(), game.font(), "Click action | W/S: select | Enter / 1-5: act | Esc: run",
                    550f, 34f, Palette.MUTED);
        } else {
            UiRenderer.text(game.batch(), game.mediumFont(), "Click Continue or press Enter", 760f, 38f, Palette.GOLD);
        }
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawItemsMenu() {
        ShapeRenderer shapes = game.shapes();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.78f);
        shapes.rect(0f, 0f, UiRenderer.WIDTH, UiRenderer.HEIGHT);
        UiRenderer.panel(shapes, 470f, 105f, 680f, 510f, Palette.PANEL_LIGHT);
        shapes.setColor(Palette.ACCENT);
        shapes.rect(470f, 605f, 680f, 10f);
        shapes.end();

        List<Item> choices = combatItemChoices();
        if (!choices.isEmpty() && itemSelection >= choices.size()) itemSelection = 0;
        game.batch().begin();
        UiRenderer.centeredText(game.batch(), game.titleFont(), "COMBAT ITEMS", 810f, 565f, Palette.TEXT);
        if (choices.isEmpty()) {
            UiRenderer.centeredText(game.batch(), game.mediumFont(), "No picked-up items.", 810f, 410f, Palette.MUTED);
        } else {
            for (int i = 0; i < choices.size(); i++) {
                Item item = choices.get(i);
                UiRenderer.text(game.batch(), game.mediumFont(), (i == itemSelection ? "> " : "  ") + item.name + " x" + item.quantity,
                        505f, 530f - i * 52f, i == itemSelection ? Palette.TEXT : Palette.MUTED);
                UiRenderer.text(game.batch(), game.font(), item.description, 790f, 530f - i * 52f, Palette.MUTED);
            }
        }
        UiRenderer.centeredText(game.batch(), game.font(), "W/S: select | Enter: use | I / Esc: close (uses your turn)",
                810f, 125f, Palette.MUTED);
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
