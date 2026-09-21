package com.projectenigma.model;

public class DungeonEnemy implements Combatant {
    public long id;
    public EnemyType type = EnemyType.CAVE_SLIME;
    public int x;
    public int y;
    public int homeX;
    public int homeY;
    public boolean homeInitialized;
    public int maxHealth;
    public int health;
    public int attack;
    public int defense;
    public int experienceReward;
    public int goldReward;

    /** Combat-only state, never persisted. */
    public transient boolean guarding = false;

    public DungeonEnemy() {
        // Required by libGDX Json.
    }

    public DungeonEnemy(long id, EnemyType type, int x, int y, int floorNumber) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.homeX = x;
        this.homeY = y;
        this.homeInitialized = true;

        int difficulty = Math.max(0, floorNumber - 1);
        int typeBonus = switch (type) {
            case CAVE_SLIME -> 0;
            case BONE_SENTINEL -> 8;
            case ABYSS_MAGE -> 5;
            case FLOOR_WARDEN -> 22;
        };
        maxHealth = 28 + difficulty * 7 + typeBonus;
        health = maxHealth;
        attack = 8 + difficulty * 2 + (type == EnemyType.ABYSS_MAGE ? 3 : 0);
        defense = 2 + difficulty / 2 + (type == EnemyType.BONE_SENTINEL ? 2 : 0);
        experienceReward = 18 + difficulty * 5 + typeBonus / 2;
        goldReward = 7 + difficulty * 3 + typeBonus / 3;
    }

    public boolean isAlive() {
        return health > 0;
    }

    // ---- Combatant ----
    // Enemies are only ever driven with ATTACK by CombatScreen today (see the
    // "PvE flavor note" in DESIGN.md), so SKILL/POTION/RUN-related methods
    // below exist to satisfy the interface and are safe, inert defaults.

    @Override
    public String displayName() {
        return type.displayName();
    }

    @Override
    public int health() {
        return health;
    }

    @Override
    public int maxHealth() {
        return maxHealth;
    }

    @Override
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(maxHealth, health));
    }

    @Override
    public int mana() {
        return 0;
    }

    @Override
    public int maxMana() {
        return 0;
    }

    @Override
    public void setMana(int mana) {
        // Enemies have no mana pool; SKILL is never issued for them.
    }

    @Override
    public int attack() {
        return attack;
    }

    @Override
    public int defense() {
        return defense;
    }

    @Override
    public int criticalChance() {
        // Replaces the old fixed 18% "heavy strike" (+3 flat damage) roll.
        // Folding it into the shared critical-hit path (1.6x multiplier)
        // is a deliberate, minor PvE balance change from unifying the
        // engine -- see DESIGN.md.
        return 18;
    }

    @Override
    public float skillMultiplier() {
        return 1f;
    }

    @Override
    public String skillName() {
        return "attack";
    }

    @Override
    public int escapeChance() {
        return 0;
    }

    @Override
    public int potions() {
        return 0;
    }

    @Override
    public boolean usePotion() {
        return false;
    }

    @Override
    public boolean isGuarding() {
        return guarding;
    }

    @Override
    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
    }
}
