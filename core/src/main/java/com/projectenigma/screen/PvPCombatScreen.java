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
import com.projectenigma.model.HeroClass;
import com.projectenigma.model.Skill;
import com.projectenigma.network.HeroSnapshot;
import com.projectenigma.network.MatchStatus;
import com.projectenigma.network.PvPBattleState;
import com.projectenigma.network.PvPClient;
import com.projectenigma.network.PvPMatch;
import com.projectenigma.network.PvPOutcome;
import com.projectenigma.network.PvPServer;

import java.util.ArrayList;
import java.util.List;

/**
 * LAN PvP screen with host-authoritative turn handling and a shared utopian
 * pixel-art presentation. Rendering is deliberately kept separate from the
 * network branches so art and animation never change battle authority.
 *
 * <p>Created two ways:
 * <ul>
 *   <li>{@link #forHost} -- the machine running {@code PvPServer} owns the
 *       authoritative {@code PvPMatch} and drives it both from local input
 *       and from action packets relayed by {@code PvPServer}.</li>
 *   <li>{@link #forGuest} -- the joining machine owns no match state at
 *       all. It renders whatever {@code PvPBattleState} it last received
 *       and, on its turn, only ever sends a {@code PvPActionPacket} and
 *       waits.</li>
 * </ul>
 * Both roles share one class because the rendering, turn indicator, and
 * input handling are otherwise identical -- only "where does the next
 * state come from" differs, which is exactly what {@link #submitAction}
 * and the two listener implementations below isolate.
 */
public final class PvPCombatScreen extends AbstractGameScreen
        implements PvPServer.EventListener, PvPClient.EventListener {

    private static final int MAX_LOG_LINES = 6;
    private static final float ACTION_ANIMATION_DURATION = 1.05f;

    private final boolean isHost;
    private final int localPlayerIndex; // 0 = host, 1 = guest
    private final PvPMatch match; // non-null only for the host
    private final CombatMenu combatMenu;
    private final List<String> logLines = new ArrayList<>();
    private final OrthographicCamera camera;
    private final FitViewport viewport;

    private PvPBattleState state;
    private boolean suppressSnapshotAudio;
    private boolean awaitingHost;
    private BattleAction pendingAction;
    private boolean disconnectedLocally; // guest-only: true between onDisconnected() and the next onStateReceived()
    private float time;
    private float actionAnimationTime = ACTION_ANIMATION_DURATION;
    private int animatedActor = -1;
    private BattleAction animatedAction = BattleAction.ATTACK;

    private PvPCombatScreen(ProjectEnigmaGame game, boolean isHost, PvPMatch match, PvPBattleState initialState) {
        super(game);
        this.isHost = isHost;
        this.localPlayerIndex = isHost ? 0 : 1;
        this.match = match;
        this.state = initialState;
        game.sounds().play(SoundCue.ENCOUNTER);
        addLog("The match begins. " + (isHost ? "You are Player 1." : "You are Player 2."));

        camera = new OrthographicCamera();
        viewport = new FitViewport(UiRenderer.WIDTH, UiRenderer.HEIGHT, camera);

        useInput(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (actionAnimationTime < ACTION_ANIMATION_DURATION) {
                    return true;
                }
                if (state.status() == MatchStatus.FINISHED) {
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
                        game.leavePvPMatch();
                    }
                    return true;
                }
                if (state.status() == MatchStatus.WAITING_FOR_RECONNECT || disconnectedLocally) {
                    if (keycode == Input.Keys.ESCAPE) {
                        abandon();
                    }
                    return true;
                }
                return combatMenu.keyDown(keycode);
            }
        });
        useMouse(viewport);
        combatMenu = new CombatMenu(game, mouseUi, () -> localSnapshot().displayHero(),
                () -> state.status() == MatchStatus.IN_PROGRESS && !reconnecting(),
                this::actionReason, this::submitAction, item -> submitItem(item.id), this::openSkills);
        mouseUi.add("Main Menu", 130, 80, 270, 52, game::leavePvPMatch)
                .when(() -> state.status() == MatchStatus.FINISHED)
                .disabled(() -> actionAnimationTime < ACTION_ANIMATION_DURATION ? "Finishing animation..." : "");
        mouseUi.add("Abandon", 130, 80, 270, 52, this::abandon)
                .when(() -> state.status() != MatchStatus.FINISHED && reconnecting());
        useProgression(new ProgressionOverlay(game, viewport, () -> localSnapshot().displayHero(), () -> false,
                this::skillReason, this::submitSkill, () -> submitAction(BattleAction.SKILL),
                () -> actionReason(BattleAction.SKILL), () -> {}));
    }

    private HeroSnapshot localSnapshot() { return localPlayerIndex == 0 ? state.player0() : state.player1(); }
    private void openSkills() {
        if (state.status() != MatchStatus.IN_PROGRESS || reconnecting() || combatMenu.itemsVisible()) return;
        mouseUi.cancel(); progressionUi.openSkills();
    }
    private String skillReason(Skill skill) {
        String blocked = actionReason(BattleAction.ATTACK);
        if (!blocked.isEmpty()) return blocked;
        var skills = localSnapshot().skills();
        return skills == null || skills.reasons() == null ? "Unlock skills in a progression run." : skills.reasons().getOrDefault(skill.name(), "Skill unavailable.");
    }
    private void submitSkill(Skill skill) {
        String blocked = skillReason(skill);
        if (!blocked.isEmpty()) { addLog(blocked); return; }
        startActionAnimation(localPlayerIndex, BattleAction.SKILL);
        if (isHost) { applyState(match.applySkill(localPlayerIndex, skill)); game.pvpServer().broadcast(state); }
        else { awaitingHost = true; pendingAction = BattleAction.SKILL; game.pvpClient().sendSkill(skill); }
    }

    private boolean reconnecting() { return disconnectedLocally || state.status() == MatchStatus.WAITING_FOR_RECONNECT; }
    private String actionReason(BattleAction action) {
        if (state.status() == MatchStatus.FINISHED) return "Match finished.";
        if (reconnecting()) return "Waiting for reconnection...";
        if (awaitingHost) return "Waiting for the host's response...";
        if (state.currentTurn() != localPlayerIndex) return "Waiting for opponent...";
        if (actionAnimationTime < ACTION_ANIMATION_DURATION) return "Finishing animation...";
        HeroSnapshot me = localPlayerIndex == 0 ? state.player0() : state.player1();
        if (isHost) return match.actionUnavailableReason(localPlayerIndex, action);
        return ActionAvailability.reason(me.displayHero(), action);
    }

    public static PvPCombatScreen forHost(ProjectEnigmaGame game, PvPMatch match) {
        return new PvPCombatScreen(game, true, match, match.currentState());
    }

    public static PvPCombatScreen forGuest(ProjectEnigmaGame game, PvPBattleState initialState) {
        return new PvPCombatScreen(game, false, null, initialState);
    }

    // ---- action submission -------------------------------------------------

    private void submitAction(BattleAction action) {
        String unavailable = actionReason(action);
        if (!unavailable.isEmpty()) { addLog(unavailable); return; }
        startActionAnimation(localPlayerIndex, action);
        if (isHost) {
            applyState(match.applyAction(localPlayerIndex, action));
            game.pvpServer().broadcast(state);
        } else {
            awaitingHost = true;
            pendingAction = action;
            game.pvpClient().sendAction(action);
            // No local mutation: wait for the host's broadcast via onStateReceived.
        }
    }

    private void submitItem(String itemId) {
        String unavailable = actionReason(BattleAction.ATTACK);
        if (!unavailable.isEmpty()) { addLog(unavailable); return; }
        combatMenu.closeItems();
        startActionAnimation(localPlayerIndex, BattleAction.POTION);
        if (isHost) { applyState(match.applyItem(localPlayerIndex, itemId)); game.pvpServer().broadcast(state); }
        else { awaitingHost = true; pendingAction = BattleAction.POTION; game.pvpClient().sendItem(itemId); }
    }

    private void abandon() {
        if (isHost) {
            applyState(match.abandon());
            game.pvpServer().broadcast(state);
        } else {
            game.pvpClient().sendAbandon();
        }
        game.leavePvPMatch();
    }

    private void applyState(PvPBattleState newState) {
        if (!suppressSnapshotAudio) CombatAudio.queue(game.sounds(), this, CombatAudio.between(state, newState), 0);
        suppressSnapshotAudio = false;
        this.state = newState;
        if (newState.status() != MatchStatus.IN_PROGRESS || disconnectedLocally) combatMenu.closeItems();
        if (progressionUi != null && (newState.status() != MatchStatus.IN_PROGRESS || disconnectedLocally)) progressionUi.close();
        for (String line : newState.log()) {
            addLog(line);
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

    // ---- PvPServer.EventListener (host only ever receives these) ----------

    @Override
    public void onGuestConnected(boolean isReconnect) {
        if (isReconnect) {
            PvPBattleState resumed = match.resume();
            applyState(resumed);
            game.pvpServer().broadcast(resumed);
        }
    }

    @Override
    public void onGuestDisconnected() {
        game.sounds().cancel(this);
        applyState(match.pauseForDisconnect());
        // No one to broadcast to; the host's own screen reflects the pause immediately.
    }

    @Override
    public void onClassSelected(HeroClass guestClass) {
        // Late/duplicate class-select packets after the match has already
        // started are simply ignored.
    }

    @Override
    public void onActionReceived(BattleAction action) {
        startActionAnimation(1, action);
        applyState(match.applyAction(1, action)); // guest is always player 1
        game.pvpServer().broadcast(state);
    }

    @Override public void onItemReceived(String itemId) {
        PvPBattleState before = state;
        applyState(match.applyItem(1, itemId));
        if (state.currentTurn() != before.currentTurn() || state.outcome() != before.outcome())
            startActionAnimation(1, BattleAction.POTION);
        game.pvpServer().broadcast(state);
    }

    @Override public void onSkillReceived(Skill skill) {
        applyState(match.applySkill(1, skill));
        startActionAnimation(1, BattleAction.SKILL);
        game.pvpServer().broadcast(state);
    }

    @Override
    public void onAbandon() {
        applyState(match.abandon());
        game.pvpServer().broadcast(state);
    }

    // ---- PvPClient.EventListener (guest only ever receives these) ---------

    @Override
    public void onConnected(boolean isReconnect) {
        if (isReconnect) suppressSnapshotAudio = true;
        disconnectedLocally = false;
        // The host immediately re-broadcasts state on reconnect (see
        // onGuestConnected above); our view updates via onStateReceived.
    }

    @Override
    public void onDisconnected() {
        progressionUi.close();
        combatMenu.closeItems();
        game.sounds().cancel(this);
        suppressSnapshotAudio = true;
        awaitingHost = false;
        disconnectedLocally = true;
        addLog("Connection to host lost. Reconnecting...");
    }

    @Override
    public void onStateReceived(PvPBattleState newState) {
        awaitingHost = false;
        pendingAction = null;
        disconnectedLocally = false;
        if (state != null) {
            int actor = state.currentTurn();
            if ((actor == 0 || actor == 1)
                    && (actor != animatedActor || actionAnimationTime >= ACTION_ANIMATION_DURATION)) {
                startActionAnimation(actor, inferAction(state, newState, actor));
            }
        }
        applyState(newState);
    }

    private void startActionAnimation(int actor, BattleAction action) {
        animatedActor = actor;
        animatedAction = action == null ? BattleAction.ATTACK : action;
        actionAnimationTime = 0f;
    }

    private static BattleAction inferAction(PvPBattleState before, PvPBattleState after, int actor) {
        HeroSnapshot oldActor = actor == 0 ? before.player0() : before.player1();
        HeroSnapshot newActor = actor == 0 ? after.player0() : after.player1();
        HeroSnapshot oldDefender = actor == 0 ? before.player1() : before.player0();
        HeroSnapshot newDefender = actor == 0 ? after.player1() : after.player0();

        if (newActor.mana() < oldActor.mana()) {
            return BattleAction.SKILL;
        }
        for (String line : after.log()) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("potion")) {
                return BattleAction.POTION;
            }
            if (lower.contains("brace")) {
                return BattleAction.GUARD;
            }
            if (lower.contains("escape")) {
                return BattleAction.RUN;
            }
        }
        if (newDefender.health() < oldDefender.health()) {
            return BattleAction.ATTACK;
        }
        return BattleAction.GUARD;
    }

    // ---- rendering ----------------------------------------------------------

    @Override
    public void render(float delta) {
        float safeDelta = Math.min(delta, 0.1f);
        time += safeDelta;
        actionAnimationTime = Math.min(ACTION_ANIMATION_DURATION, actionAnimationTime + safeDelta);
        // Belt-and-suspenders: postRunnable already guarantees delivery before
        // the next frame, but draining here too is harmless and cheap.
        if (isHost && game.pvpServer() != null) {
            game.pvpServer().drainIncoming();
        } else if (!isHost && game.pvpClient() != null) {
            game.pvpClient().drainIncoming();
        }

        ScreenUtils.clear(Palette.VOID);
        viewport.apply();
        camera.update();

        drawArena();
        drawCombatants();
        drawInterface();
        combatMenu.drawItems();
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
        HeroSnapshot me = isHost ? state.player0() : state.player1();
        HeroSnapshot opponent = isHost ? state.player1() : state.player0();
        boolean myTurn = state.currentTurn() == localPlayerIndex;

        UtopiaAssets.BattlePose myPose = playerDefeated(localPlayerIndex)
                ? UtopiaAssets.BattlePose.DEFEAT : UtopiaAssets.BattlePose.IDLE;
        UtopiaAssets.BattlePose opponentPose = playerDefeated(1 - localPlayerIndex)
                ? UtopiaAssets.BattlePose.DEFEAT : UtopiaAssets.BattlePose.IDLE;
        float myFrameTime = time;
        float opponentFrameTime = time;

        if (actionAnimationTime < ACTION_ANIMATION_DURATION && animatedActor >= 0) {
            boolean localActs = animatedActor == localPlayerIndex;
            UtopiaAssets.BattlePose actionPose = poseFor(animatedAction);
            UtopiaAssets.BattlePose reactionPose = (animatedAction == BattleAction.ATTACK
                    || animatedAction == BattleAction.SKILL)
                    ? UtopiaAssets.BattlePose.HURT : UtopiaAssets.BattlePose.IDLE;
            if (localActs) {
                myPose = actionPose;
                myFrameTime = actionAnimationTime;
                opponentPose = playerDefeated(1 - localPlayerIndex)
                        ? UtopiaAssets.BattlePose.DEFEAT : reactionPose;
                opponentFrameTime = actionAnimationTime;
            } else {
                opponentPose = actionPose;
                opponentFrameTime = actionAnimationTime;
                myPose = playerDefeated(localPlayerIndex)
                        ? UtopiaAssets.BattlePose.DEFEAT : reactionPose;
                myFrameTime = actionAnimationTime;
            }
        }

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().setColor(1f, 1f, 1f, 1f);
        game.batch().begin();
        game.assets().drawBattleHero(game.batch(), me.heroClass(), myPose, myFrameTime, false,
                280f, 205f, 288f);
        game.assets().drawBattleHero(game.batch(), opponent.heroClass(), opponentPose, opponentFrameTime, true,
                1000f, 205f, 288f);
        game.batch().end();

        ShapeRenderer shapes = game.shapes();
        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiRenderer.panel(shapes, 100f, 470f, 390f, 125f, Palette.PANEL);
        UiRenderer.panel(shapes, 790f, 470f, 390f, 125f, Palette.PANEL);
        shapes.setColor(myTurn ? Palette.BLUE_LIGHT : Palette.BLUE);
        shapes.rect(100f, 585f, 390f, 10f);
        shapes.setColor(!myTurn ? Palette.ACCENT : Palette.WALL_EDGE);
        shapes.rect(790f, 585f, 390f, 10f);
        shapes.setColor(myTurn ? Palette.BLUE_LIGHT : Palette.WALL_EDGE);
        shapes.rect(205f, 200f, 150f, 7f);
        shapes.setColor(!myTurn ? Palette.ACCENT : Palette.WALL_EDGE);
        shapes.rect(925f, 200f, 150f, 7f);
        UiRenderer.bar(shapes, 125f, 520f, 340f, 24f, me.health(), me.maxHealth(), Palette.HEALTH);
        UiRenderer.bar(shapes, 125f, 486f, 340f, 18f, me.mana(), me.maxMana(), Palette.MANA);
        UiRenderer.bar(shapes, 815f, 520f, 340f, 24f, opponent.health(), opponent.maxHealth(), Palette.HEALTH);
        UiRenderer.bar(shapes, 815f, 486f, 340f, 18f, opponent.mana(), opponent.maxMana(), Palette.MANA);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        game.batch().setProjectionMatrix(camera.combined);
        game.batch().begin();
        UiRenderer.text(game.batch(), game.mediumFont(), "You (" + me.heroClass().displayName() + ")  Lv " + me.level(),
                125f, 575f, myTurn ? Palette.ACCENT : Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "HP " + me.health() + "/" + me.maxHealth()
                + "   EN " + me.mana() + "/" + me.maxMana(), 125f, 558f, Palette.MUTED);
        UiRenderer.text(game.batch(), game.mediumFont(), "Opponent (" + opponent.heroClass().displayName() + ")  Lv " + opponent.level(),
                815f, 575f, !myTurn ? Palette.ACCENT : Palette.TEXT);
        UiRenderer.text(game.batch(), game.font(), "HP " + opponent.health() + "/" + opponent.maxHealth()
                + "   EN " + opponent.mana() + "/" + opponent.maxMana(), 815f, 558f, Palette.MUTED);
        if (me.skills() != null) UiRenderer.wrappedText(game.batch(), game.font(), me.skills().status(), 100, 456, 440, Palette.BLUE_LIGHT);
        if (opponent.skills() != null) UiRenderer.wrappedText(game.batch(), game.font(), opponent.skills().status(), 790, 456, 440, Palette.GOLD);
        game.batch().end();
    }

    private boolean playerDefeated(int playerIndex) {
        return (playerIndex == 0 && state.outcome() == PvPOutcome.GUEST_WINS)
                || (playerIndex == 1 && state.outcome() == PvPOutcome.HOST_WINS);
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
        String header = state.status() == MatchStatus.IN_PROGRESS && state.currentTurn() == localPlayerIndex && !awaitingHost
                ? combatMenu.description() : statusHeadline();
        UiRenderer.text(game.batch(), game.font(), header, 550, 174, Palette.MUTED);
        float logY = 145f;
        for (String line : logLines) {
            UiRenderer.text(game.batch(), game.font(), line, 550f, logY, Palette.TEXT);
            logY -= 19f;
        }

        if (state.status() == MatchStatus.FINISHED) {
            UiRenderer.text(game.batch(), game.mediumFont(), outcomeHeadline(), 877f, 38f, Palette.GOLD);
        } else if (state.status() == MatchStatus.WAITING_FOR_RECONNECT || disconnectedLocally) {
            UiRenderer.text(game.batch(), game.font(), "Waiting for reconnection...    Esc: abandon", 550f, 34f, Palette.DANGER);
        } else {
            UiRenderer.text(game.batch(), game.font(), "Click action | W/S: select | 1-4: act | I: items | L: skills",
                    550f, 34f, Palette.MUTED);
        }
        game.batch().end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private String statusHeadline() {
        if (awaitingHost) return "Waiting for host response...";
        if (state.status() == MatchStatus.WAITING_FOR_RECONNECT || disconnectedLocally) {
            return "Connection paused...";
        }
        return "Waiting for opponent...";
    }

    private String outcomeHeadline() {
        boolean iWon = (localPlayerIndex == 0 && state.outcome() == PvPOutcome.HOST_WINS)
                || (localPlayerIndex == 1 && state.outcome() == PvPOutcome.GUEST_WINS);
        if (state.outcome() == PvPOutcome.ABANDONED) {
            return "Match abandoned - press Enter";
        }
        return (iWon ? "Victory! " : "Defeat. ") + "Press Enter to continue";
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }
}
