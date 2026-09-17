package com.projectenigma.model;

/** Catalogue and balance values. IDs are stable save/network identifiers. */
public enum Skill {
    DISRUPTION_PULSE("Disruption Pulse", "Control", "Stun: interrupt the opponent's next action. A recovered target resists another interrupt for one action.", 2, 3, 2, 0, 0, Effect.STUN),
    ARC_DISCHARGE("Arc Discharge", "Control", "Shock: deal 90% ATK damage and interrupt the opponent's next action. Respects interrupt resistance.", 3, 5, 4, .9f, 0, Effect.STUN),
    THERMAL_OVERLOAD("Thermal Overload", "Damage over time", "Burn for 7 + your level damage at the start of each of the target's next two actions. Reapplying refreshes, never stacks.", 2, 3, 3, .4f, 2, Effect.BURN),
    REACTOR_BURST("Reactor Burst", "Burst damage", "Explosion: release 320% ATK in a single burst. High energy cost and a long recovery window.", 4, 8, 5, 3.2f, 0, Effect.DAMAGE),
    PHOTON_LANCE("Photon Lance", "Precision damage", "Deal 135% ATK damage, ignoring armor. Barriers and deflection still protect the target.", 2, 4, 2, 1.35f, 0, Effect.PIERCE),
    NANITE_CORROSION("Nanite Corrosion", "Damage over time", "Deal 4 + your level damage before each of the target's next three actions. Bypasses armor; barriers still absorb it.", 3, 4, 4, 0, 3, Effect.CORROSION),
    DEFLECTION_FIELD("Deflection Field", "Defense", "Halve damage from the next two direct hits. Does not reduce damage over time. Does not stack with itself.", 2, 3, 3, 0, 2, Effect.DEFLECT),
    ENERGY_BARRIER("Energy Barrier", "Defense", "Absorb 24 + twice your level incoming damage, including damage over time. Recasting replaces the barrier.", 2, 4, 4, 0, 0, Effect.BARRIER),
    PHASE_SHIFT("Phase Shift", "Defense", "Evade the next direct hit completely. Damage over time still applies.", 3, 4, 4, 0, 1, Effect.PHASE),
    NANITE_RESTORATION("Nanite Restoration", "Combat / field support", "Restore 24 + twice your level HP. Healing amplifiers apply. In the field, recovery takes 12 successful tile moves.", 2, 4, 3, 0, 0, Effect.HEAL),
    EMERGENCY_REPAIR("Emergency Repair", "Sustained support", "Restore 10 + your level HP before each of your next three actions. Recasting refreshes the repair sequence.", 3, 4, 4, 0, 3, Effect.REPAIR),
    SYSTEM_SCAN("System Scan", "Utility", "Expose opponent ATK and DEF and amplify the next two direct hits against them by 25%.", 2, 2, 3, 0, 2, Effect.SCAN),
    OVERCLOCK("Overclock", "Offense", "Spend 8 HP to amplify your next three damaging attacks by 30%. Cannot activate at 8 HP or less.", 3, 3, 4, 0, 3, Effect.OVERCLOCK),
    VECTOR_BOOST("Vector Boost", "Field mobility", "Move 40% faster for 20 successful tile moves. Recovery takes 30 tile moves. Does not bypass walls or enemies.", 2, 3, 0, 0, 20, Effect.MOBILITY);

    public enum Effect { STUN, BURN, CORROSION, DAMAGE, PIERCE, DEFLECT, BARRIER, PHASE, HEAL, REPAIR, SCAN, OVERCLOCK, MOBILITY }
    public final String title, category, description;
    public final int level, cost, cooldown, duration;
    public final float multiplier;
    public final Effect effect;
    Skill(String title, String category, String description, int level, int cost, int cooldown,
          float multiplier, int duration, Effect effect) {
        this.title = title; this.category = category; this.description = description;
        this.level = level; this.cost = cost; this.cooldown = cooldown;
        this.multiplier = multiplier; this.duration = duration; this.effect = effect;
    }
    public boolean fieldUsable() { return effect == Effect.HEAL || effect == Effect.MOBILITY; }
    public boolean combatUsable() { return effect != Effect.MOBILITY; }
}
