package com.projectenigma.model;

/** UI eligibility without mutating combat state or spending a turn. */
public final class ActionAvailability {
    private ActionAvailability() {}
    public static String reason(Combatant actor, BattleAction action) {
        return reason(actor.health(), actor.maxHealth(), actor.mana(), actor.potions(), action);
    }
    public static String reason(int health, int maxHealth, int mana, int potions, BattleAction action) {
        if (health <= 0) return "This combatant has fallen.";
        if (action == BattleAction.SKILL && mana < BattleEngine.skillManaCost()) return "Not enough energy.";
        if (action == BattleAction.POTION) {
            if (potions <= 0) return "No potions remain.";
            if (health >= maxHealth) return "Health is already full.";
        }
        return "";
    }
}
