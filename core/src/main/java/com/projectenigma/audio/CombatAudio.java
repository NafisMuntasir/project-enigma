package com.projectenigma.audio;

import com.projectenigma.network.HeroSnapshot;
import com.projectenigma.network.MatchStatus;
import com.projectenigma.network.PvPBattleState;
import java.util.ArrayList;
import java.util.List;

/** Cues derive from confirmed HP/energy changes, never a speculative network request. */
public final class CombatAudio {
    private CombatAudio() { }
    public static List<SoundCue> changes(boolean spentEnergy, boolean tookDamage, boolean healed) {
        List<SoundCue> result = new ArrayList<>(3);
        if (spentEnergy) result.add(SoundCue.SKILL);
        if (tookDamage) result.add(SoundCue.DAMAGE);
        if (healed) result.add(SoundCue.HEAL);
        return result;
    }

    public static List<SoundCue> between(PvPBattleState before, PvPBattleState after) {
        if (before == null || before.status() != MatchStatus.IN_PROGRESS
                || (after.status() != MatchStatus.IN_PROGRESS && after.status() != MatchStatus.FINISHED)) return List.of();
        HeroSnapshot a = before.player0(), b = before.player1(), c = after.player0(), d = after.player1();
        return changes(c.mana() < a.mana() || d.mana() < b.mana(),
                c.health() < a.health() || d.health() < b.health(),
                c.health() > a.health() || d.health() > b.health());
    }

    public static void queue(SoundEffects sounds, Object owner, List<SoundCue> cues, float offset) {
        for (SoundCue cue : cues) sounds.schedule(owner, cue,
                offset + (cue == SoundCue.DAMAGE ? .22f : cue == SoundCue.HEAL ? .12f : 0));
    }
}
