package com.projectenigma.model;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;

/** One encounter's transient state, owned by the existing BattleEngine. */
public final class CombatEffects {
    final EnumMap<Skill, Integer> cooldowns = new EnumMap<>(Skill.class);
    int stun, resistance, burnTurns, burnDamage, corrosionTurns, corrosionDamage;
    int barrier, deflectHits, phaseHits, exposedHits, overclockHits, repairTurns, repairAmount;
    boolean scanned;

    public int cooldown(Skill skill) { return cooldowns.getOrDefault(skill, 0); }
    void advanceCooldowns() { cooldowns.replaceAll((skill, turns) -> Math.max(0, turns - 1)); }
    public String summary() {
        List<String> bits = new ArrayList<>();
        if (stun > 0) bits.add("Interrupted");
        if (resistance > 0) bits.add("Interrupt resistance");
        if (burnTurns > 0) bits.add("Burn " + burnTurns);
        if (corrosionTurns > 0) bits.add("Corrosion " + corrosionTurns);
        if (barrier > 0) bits.add("Barrier " + barrier);
        if (deflectHits > 0) bits.add("Deflect " + deflectHits);
        if (phaseHits > 0) bits.add("Phase " + phaseHits);
        if (exposedHits > 0) bits.add("Exposed " + exposedHits);
        if (overclockHits > 0) bits.add("Overclock " + overclockHits);
        if (repairTurns > 0) bits.add("Repair " + repairTurns);
        return bits.isEmpty() ? "No active effects" : String.join(" | ", bits);
    }
}
