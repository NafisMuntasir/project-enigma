package com.projectenigma.network;

import com.projectenigma.model.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Immutable progression transfer; no combat timers or pending choices cross between matches. */
public record ProgressionSnapshot(List<String> unlocked, List<String> equipped, List<Integer> ranks) implements Serializable {
    private static final long serialVersionUID = 1L;
    public static ProgressionSnapshot of(Hero hero) {
        var p = hero.progression();
        List<Integer> ranks = new ArrayList<>();
        for (int rank : p.passiveRanks) ranks.add(rank);
        return new ProgressionSnapshot(List.copyOf(p.unlocked), List.copyOf(p.equipped), List.copyOf(ranks));
    }
    public Progression restore(int level) {
        Progression p = new Progression();
        int budget = Math.max(0, Math.min(100, level) - 1);
        if (ranks != null) for (PassiveUpgrade passive : PassiveUpgrade.values()) {
            int claimed = passive.ordinal() < ranks.size() && ranks.get(passive.ordinal()) != null ? ranks.get(passive.ordinal()) : 0;
            int rank = Math.max(0, Math.min(budget, Math.min(passive.maxRank, claimed)));
            p.passiveRanks[passive.ordinal()] = rank; budget -= rank;
        }
        if (unlocked != null) for (String id : unlocked) {
            Skill skill = Progression.skill(id);
            if (budget > 0 && skill != null && skill.level <= level && !p.unlocked(skill)) { p.unlocked.add(id); budget--; }
        }
        if (equipped != null) p.equipped = new ArrayList<>(equipped.subList(0, Math.min(3, equipped.size())));
        p.normalize(); return p;
    }
}
