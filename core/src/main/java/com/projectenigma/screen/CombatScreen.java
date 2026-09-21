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
import com.projectenigma.model.Skill;

import java.util.ArrayList;
import java.util.List;

public final class CombatScreen extends AbstractGameScreen
{
    private static final int MAX_LOG_LINES = 7;
    private static final float HERO_ACTION_PHASE = 0.86f;
    private static final float TURN_ANIMATION_DURATION = 1.58f;

    private final GameSession session;
    private final DungeonEnemy enemy;
    private final BattleEngine engine;
    private final CombatMenu combatMenu;
    private final List<String> logLines = new ArrayList<>();
    private final OrthographicCamera camera;
    private final FitViewport viewport;

    private BattleOutcome outcome = BattleOutcome.ONGOING;
    private float time;
    private float turnAnimationTime = TURN_ANIMATION_DURATION;
    private BattleAction animatedHeroAction = BattleAction.ATTACK;
    private boolean animatedEnemyReply;

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
                if (outcome != BattleOutcome.ONGOING) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
                        leaveBattle();
                        return true;
                    }
                    return false;
                }
                return combatMenu.keyDown(keycode);
            }
        });
        useMouse(viewport);
        combatMenu = new CombatMenu(game, mouseUi, () -> session.hero, () -> outcome == BattleOutcome.ONGOING,
                this::actionReason, action -> performAction(action, null, null),
                item -> performAction(BattleAction.SKILL, null, item), this::openSkills);
        mouseUi.add("Continue", 130, 80, 270, 52, this::leaveBattle).when(() -> outcome != BattleOutcome.ONGOING)
                .disabled(() -> turnAnimationTime < TURN_ANIMATION_DURATION ? "Finishing animation..." : "");
        useProgression(new ProgressionOverlay(game, viewport, () -> session.hero, () -> false,
                skill -> actionReason(BattleAction.ATTACK).isEmpty() ? engine.skillReason(session.hero, enemy, skill) : actionReason(BattleAction.ATTACK),
                skill -> performAction(BattleAction.SKILL, skill, null),
                () -> performAction(BattleAction.SKILL, null, null), () -> actionReason(BattleAction.SKILL),
                () -> { game.saveGame(); game.progressionSelectionCompleted(); }));
    }

    private void openSkills() {
        if (outcome != BattleOutcome.ONGOING || combatMenu.itemsVisible() || turnAnimationTime < TURN_ANIMATION_DURATION) return;
        mouseUi.cancel(); progressionUi.openSkills();
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
    private void performAction(BattleAction action, Skill skill, Item item) {
        String unavailable = actionReason(skill == null && item == null ? action : BattleAction.ATTACK);
        if (!unavailable.isEmpty()) { addLog(unavailable); return; }
        int heroHealthBefore = session.hero.health, enemyHealthBefore = enemy.health, manaBefore = session.hero.mana;
        TurnResult playerTurn = skill != null ? engine.resolveSkill(session.hero, enemy, skill)
                : item != null ? engine.resolveItem(session.hero, enemy, item) : engine.resolve(session.hero, enemy, action);
        for (String message : playerTurn.messages()) {
            addLog(message);
        }
        if (!playerTurn.actionAccepted()) {
            return;
        }
        CombatAudio.queue(game.sounds(), this, CombatAudio.changes(session.hero.mana < manaBefore,
                enemy.health < enemyHealthBefore || session.hero.health < heroHealthBefore,
                session.hero.health > heroHealthBefore || enemy.health > enemyHealthBefore), 0);
        combatMenu.closeItems();
        animatedHeroAction = action;
        animatedEnemyReply = false;
        turnAnimationTime = 0f;

        if (playerTurn.outcome() == BattleOutcome.VICTORY) { winBattle(); return; }
        if (playerTurn.outcome() == BattleOutcome.DEFEAT) { loseBattle(); return; }
        if (playerTurn.outcome() == BattleOutcome.ESCAPED) {
            outcome = BattleOutcome.ESCAPED;
            game.saveGame();
            addLog("Press Enter to return to the dungeon.");
            return;
        }

        // ONGOING: covers a completed ATTACK/SKILL, a GUARD, an item use,
        // or a failed RUN attempt -- in every one of those cases the enemy still replies.
        int healthBeforeReply = session.hero.health;
        int enemyHealthBeforeReply = enemy.health;
        TurnResult enemyTurn = engine.resolve(enemy, session.hero, BattleAction.ATTACK);
        CombatAudio.queue(game.sounds(), this, CombatAudio.changes(false,
                session.hero.health < healthBeforeReply || enemy.health < enemyHealthBeforeReply,
                session.hero.health > healthBeforeReply || enemy.health > enemyHealthBeforeReply), HERO_ACTION_PHASE);
        animatedEnemyReply = enemyTurn.messages().stream().anyMatch(m -> m.contains("'s attack deals"));
        for (String message : enemyTurn.messages()) {
            addLog(message);
        }
        if (enemyTurn.outcome() == BattleOutcome.VICTORY) loseBattle();
        else if (enemyTurn.outcome() == BattleOutcome.DEFEAT) winBattle();
        else outcome = BattleOutcome.ONGOING;
    }

    private void winBattle() {
        outcome = BattleOutcome.VICTORY;
        int levelBefore = session.hero.level, healthBefore = session.hero.health;
        for (String message : session.defeatEnemy(enemy)) addLog(message);
        if (session.hero.level > levelBefore) game.sounds().schedule(this, SoundCue.POWER_UP, .85f);
        if (session.hero.health > healthBefore) game.sounds().schedule(this, SoundCue.HEAL, .60f);
        game.saveGame();
        addLog(session.hero.progression().pendingChoices > 0 ? "Level up: choose an augmentation after the animation." : "Press Enter to return to the dungeon.");
    }

    private void loseBattle() {
        outcome = BattleOutcome.DEFEAT;
        addLog("You collapse in the dungeon.");
        game.deleteCurrentRunSave();
        addLog("Press Enter to continue.");
    }

    private void leaveBattle() {
        if (turnAnimationTime < TURN_ANIMATION_DURATION || outcome == BattleOutcome.ONGOING) return;
        if (outcome == BattleOutcome.VICTORY && session.hero.progression().pendingChoices > 0) {
            mouseUi.cancel(); progressionUi.openUpgrades(); return;
        }
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
        updateUpgrades();
        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();
        drawArena();
        drawCombatants();
        drawInterface();
        combatMenu.drawItems();
        drawMouse();
    }

    private void updateUpgrades() {
        if (outcome == BattleOutcome.VICTORY && session.hero.progression().pendingChoices > 0
                && turnAnimationTime >= TURN_ANIMATION_DURATION && !progressionUi.upgrading()) {
            mouseUi.cancel(); progressionUi.openUpgrades();
        }
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
        game.assets().drawBattleHero(game.batch(), session.hero.heroClass, heroPose, heroFrameTime, false,
                280f, 205f, 288f);
        game.assets().drawBattleEnemy(game.batch(), enemy.type, enemyPose, enemyFrameTime, true,
                965f, 205f, 288f);
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
        UiRenderer.wrappedText(game.batch(), game.font(), engine.effects(session.hero).summary(), 100, 456, 440, Palette.BLUE_LIGHT);
        UiRenderer.wrappedText(game.batch(), game.font(), engine.effects(enemy).summary(), 790, 456, 440, Palette.GOLD);
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
        UiRenderer.text(game.batch(), game.font(), combatMenu.description(), 550f, 174f, Palette.MUTED);
        float logY = 145f;
        for (String line : logLines) {
            UiRenderer.text(game.batch(), game.font(), line, 550f, logY, Palette.TEXT);
            logY -= 19f;
        }
        if (outcome == BattleOutcome.ONGOING) {
            UiRenderer.text(game.batch(), game.font(), "Click action | W/S: select | 1-4: act | L: skills | Esc: run",
                    550f, 34f, Palette.MUTED);
        } else {
            UiRenderer.text(game.batch(), game.mediumFont(), "Click Continue or press Enter", 760f, 38f, Palette.GOLD);
        }
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
