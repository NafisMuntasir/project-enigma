package com.projectenigma.model;

/** Repeatable, bounded augmentations. Changing balance never changes saved base stats retroactively. */
public enum PassiveUpgrade {
    DAMAGE("Precision Actuators", "+2 base ATK per rank.", 5),
    VITALITY("Vitality Lattice", "+12 maximum HP and restore 12 HP per rank.", 5),
    DEFENSE("Adaptive Plating", "+1 base DEF per rank.", 5),
    MOVEMENT("Vector Servos", "+8% field movement speed per rank (maximum +40%).", 5),
    COOLDOWN("Parallel Processors", "General combat skill cooldowns recover one action sooner per rank; minimum one action.", 2),
    CRITICAL("Predictive Targeting", "+3 percentage points critical chance per rank; total capped at 50%.", 5),
    ENERGY("Expanded Capacitor", "+2 maximum EN and restore 2 EN per rank.", 5),
    HEALING("Nanite Amplifier", "+10% healing effectiveness per rank (maximum +50%).", 5);
    public final String title, description;
    public final int maxRank;
    PassiveUpgrade(String title, String description, int maxRank) {
        this.title = title; this.description = description; this.maxRank = maxRank;
    }
    public void apply(Hero hero) {
        switch (this) {
            case DAMAGE -> hero.attack += 2;
            case VITALITY -> { hero.maxHealth += 12; hero.setHealth(hero.health + 12); }
            case DEFENSE -> hero.defense++;
            case ENERGY -> { hero.maxMana += 2; hero.setMana(hero.mana + 2); }
            default -> { /* Derived modifiers read the saved rank. */ }
        }
    }
}
