package com.projectenigma.audio;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;
import com.projectenigma.network.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CombatAudioTest {
    private final Hero host = new Hero(HeroClass.WARRIOR), guest = new Hero(HeroClass.MAGE);
    private PvPBattleState snapshot(MatchStatus status) {
        return new PvPBattleState(HeroSnapshot.of(host), HeroSnapshot.of(guest), 0,
                PvPOutcome.ONGOING, status, List.of());
    }
    @Test void damageAndHealingWorkForEitherPlayer() {
        PvPBattleState before = snapshot(MatchStatus.IN_PROGRESS);
        guest.health -= 12;
        assertEquals(List.of(SoundCue.DAMAGE), CombatAudio.between(before, snapshot(MatchStatus.IN_PROGRESS)));
        before = snapshot(MatchStatus.IN_PROGRESS); guest.health += 12;
        assertEquals(List.of(SoundCue.HEAL), CombatAudio.between(before, snapshot(MatchStatus.IN_PROGRESS)));
        before = snapshot(MatchStatus.IN_PROGRESS); host.health -= 12;
        assertEquals(List.of(SoundCue.DAMAGE), CombatAudio.between(before, snapshot(MatchStatus.IN_PROGRESS)));
        before = snapshot(MatchStatus.IN_PROGRESS); host.health += 12;
        assertEquals(List.of(SoundCue.HEAL), CombatAudio.between(before, snapshot(MatchStatus.IN_PROGRESS)));
    }
    @Test void skillHasCastAndImpactButRejectedAndDuplicateStatesAreSilent() {
        PvPBattleState before = snapshot(MatchStatus.IN_PROGRESS);
        host.mana -= 3; guest.health -= 20;
        PvPBattleState after = snapshot(MatchStatus.IN_PROGRESS);
        assertEquals(List.of(SoundCue.SKILL, SoundCue.DAMAGE), CombatAudio.between(before, after));
        assertTrue(CombatAudio.between(after, after).isEmpty());
        PvPBattleState rejected = new PvPBattleState(after.player0(), after.player1(), 0, after.outcome(), after.status(), List.of("Not enough EN."));
        assertTrue(CombatAudio.between(after, rejected).isEmpty());
    }
    @Test void reconnectSnapshotsDoNotReplayHistoricalDamageOrHealing() {
        PvPBattleState paused = snapshot(MatchStatus.WAITING_FOR_RECONNECT);
        guest.health -= 20; host.mana -= 3;
        assertTrue(CombatAudio.between(paused, snapshot(MatchStatus.IN_PROGRESS)).isEmpty());
        assertTrue(CombatAudio.between(null, snapshot(MatchStatus.IN_PROGRESS)).isEmpty());
    }
}
