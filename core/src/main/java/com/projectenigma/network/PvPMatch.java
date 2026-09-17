package com.projectenigma.network;

import com.projectenigma.model.BattleAction;
import com.projectenigma.model.BattleEngine;
import com.projectenigma.model.BattleOutcome;
import com.projectenigma.model.Hero;
import com.projectenigma.model.TurnResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Host-authoritative PvP battle state and rules. This class never touches
 * the network or libGDX -- it is exercised the same way {@code BattleEngine}
 * itself is: called directly, with plain objects, from a test or from a
 * screen. {@code PvPServer} is the only thing that talks to this class over
 * a connection.
 *
 * <p>Player 0 is always the host, player 1 is always the guest. All public
 * methods are synchronized because {@code applyAction} can be invoked both
 * from the host's own input handling (render thread) and, indirectly via
 * {@code PvPServer}'s queue drain (also render thread in this design -- see
 * DESIGN.md's threading section) -- synchronizing costs nothing measurable
 * for two players and removes an entire class of races for free.
 */
public final class PvPMatch {
    private static final int MAX_LOG_LINES = 4;

    private final Hero hostHero;
    private final Hero guestHero;
    private final BattleEngine engine;

    private int currentTurn = 0;
    private PvPOutcome outcome = PvPOutcome.ONGOING;
    private MatchStatus status = MatchStatus.IN_PROGRESS;

    public PvPMatch(Hero hostHero, Hero guestHero, long seed) {
        this.hostHero = hostHero;
        this.guestHero = guestHero;
        this.engine = new BattleEngine(seed);
    }

    /**
     * Applies one player's requested action. Rejects silently (no state
     * change, {@code ONGOING} echoed back with an explanatory log line) if
     * it is not that player's turn, the match is not in progress, or the
     * engine itself rejects the action (e.g. not enough mana).
     */
    public synchronized PvPBattleState applyAction(int playerIndex, BattleAction action) {
        return apply(playerIndex, action, null);
    }

    public synchronized PvPBattleState applySkill(int playerIndex, com.projectenigma.model.Skill skill) {
        if (skill == null) return snapshot(single("Unknown skill."));
        return apply(playerIndex, null, skill);
    }

    private PvPBattleState apply(int playerIndex, BattleAction action, com.projectenigma.model.Skill skill) {
        if (status != MatchStatus.IN_PROGRESS) {
            return snapshot(single("The match is not currently accepting actions."));
        }
        if (outcome != PvPOutcome.ONGOING) {
            return snapshot(single("The match has already ended."));
        }
        if (playerIndex != currentTurn) {
            return snapshot(single("It is not your turn."));
        }

        Hero attacker = playerIndex == 0 ? hostHero : guestHero;
        Hero defender = playerIndex == 0 ? guestHero : hostHero;
        if (skill == null && action == null) return snapshot(single("Unknown action."));
        TurnResult result = skill == null ? engine.resolve(attacker, defender, action) : engine.resolveSkill(attacker, defender, skill);
        List<String> log = capped(result.messages());

        if (!result.actionAccepted()) {
            return snapshot(log);
        }

        if (result.outcome() == BattleOutcome.VICTORY) {
            outcome = playerIndex == 0 ? PvPOutcome.HOST_WINS : PvPOutcome.GUEST_WINS;
            status = MatchStatus.FINISHED;
        } else if (result.outcome() == BattleOutcome.ESCAPED) {
            // RUN = surrender: the fleeing player's opponent wins.
            outcome = playerIndex == 0 ? PvPOutcome.GUEST_WINS : PvPOutcome.HOST_WINS;
            status = MatchStatus.FINISHED;
        } else if (result.outcome() == BattleOutcome.DEFEAT) {
            // Defensive guard path in BattleEngine (attacker already dead);
            // should not occur in a well-driven match, but resolve safely.
            outcome = playerIndex == 0 ? PvPOutcome.GUEST_WINS : PvPOutcome.HOST_WINS;
            status = MatchStatus.FINISHED;
        } else {
            currentTurn = 1 - playerIndex;
        }
        return snapshot(log);
    }

    /** Freezes the match after a disconnect. Called by PvPServer's connection listener. */
    public synchronized PvPBattleState pauseForDisconnect() {
        if (status == MatchStatus.IN_PROGRESS) {
            status = MatchStatus.WAITING_FOR_RECONNECT;
        }
        return snapshot(single("Connection lost. Waiting for reconnection..."));
    }

    /** Unfreezes the match after the guest reconnects. */
    public synchronized PvPBattleState resume() {
        if (status == MatchStatus.WAITING_FOR_RECONNECT) {
            status = MatchStatus.IN_PROGRESS;
        }
        return snapshot(single("Connection restored."));
    }

    /** Either player gave up while waiting, or chose to abandon outright. */
    public synchronized PvPBattleState abandon() {
        status = MatchStatus.FINISHED;
        outcome = PvPOutcome.ABANDONED;
        return snapshot(single("The match was abandoned."));
    }

    public synchronized String actionUnavailableReason(int playerIndex, BattleAction action) {
        return com.projectenigma.model.ActionAvailability.reason(playerIndex == 0 ? hostHero : guestHero, action);
    }

    public synchronized PvPBattleState currentState() {
        return snapshot(new ArrayList<>(0));
    }

    public synchronized MatchStatus status() {
        return status;
    }

    public synchronized PvPOutcome outcome() {
        return outcome;
    }

    private PvPBattleState snapshot(List<String> log) {
        return new PvPBattleState(HeroSnapshot.of(hostHero, guestHero, engine), HeroSnapshot.of(guestHero, hostHero, engine),
                currentTurn, outcome, status, log);
    }

    private static List<String> single(String message) {
        List<String> list = new ArrayList<>(1);
        list.add(message);
        return list;
    }

    private static List<String> capped(List<String> messages) {
        List<String> log = new ArrayList<>(Math.min(messages.size(), MAX_LOG_LINES));
        int start = Math.max(0, messages.size() - MAX_LOG_LINES);
        for (int i = start; i < messages.size(); i++) {
            log.add(messages.get(i));
        }
        return log;
    }
}
