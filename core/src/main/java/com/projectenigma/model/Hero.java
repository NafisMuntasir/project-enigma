package com.projectenigma.model;

import java.util.ArrayList;
import java.util.List;

public class Hero implements Combatant {
    public HeroClass heroClass = HeroClass.WARRIOR;
    public int level = 1;
    public int experience = 0;
    public int maxHealth = 120;
    public int health = 120;
    public int maxMana = 8;
    public int mana = 8;
    public int attack = 16;
    public int defense = 7;
    public int potions = 3;
    public int gold = 0;
    public Progression progression = new Progression();

    public Progression progression() {
        if (progression == null) progression = new Progression();
        return progression;
    }

    /** Picked-up consumables and spare equipment. Persisted with the run. */
    public ArrayList<Item> inventory = new ArrayList<>();
    public Item equippedWeapon;
    public Item equippedArmor;

    /** Combat-only state. Never persisted -- a fresh guard should never survive a save/load. */
    public transient boolean guarding = false;

    public Hero() {
        // Required by libGDX Json.
    }

    public Hero(HeroClass heroClass) {
        this.heroClass = heroClass;
        this.maxHealth = heroClass.health();
        this.health = maxHealth;
        this.maxMana = heroClass.mana();
        this.mana = maxMana;
        this.attack = heroClass.attack();
        this.defense = heroClass.defense();
    }

    public int experienceForNextLevel() {
        return 45 + (level - 1) * 30;
    }

    public int heal(int amount) {
        int before = health;
        health = Math.min(maxHealth, health + Math.round(Math.max(0, amount)
                * (1 + .1f * progression().rank(PassiveUpgrade.HEALING))));
        return health - before;
    }

    public int restoreMana(int amount) {
        int before = mana;
        mana = Math.min(maxMana, mana + Math.max(0, amount));
        return mana - before;
    }

    public List<String> gainExperience(int amount) {
        List<String> messages = new ArrayList<>();
        experience += Math.max(0, amount);
        while (experience >= experienceForNextLevel()) {
            experience -= experienceForNextLevel();
            level++;
            progression().pendingChoices++;
            int healthIncrease = heroClass == HeroClass.WARRIOR || heroClass == HeroClass.GRAPPLER ? 14 : 10;
            int manaIncrease = heroClass == HeroClass.MAGE || heroClass == HeroClass.CLERIC ? 4 : 2;
            maxHealth += healthIncrease;
            maxMana += manaIncrease;
            attack += heroClass == HeroClass.THIEF || heroClass == HeroClass.GRAPPLER ? 4 : 3;
            defense += heroClass == HeroClass.WARRIOR ? 3 : 2;
            health = maxHealth;
            mana = maxMana;
            messages.add("Level up! You are now level " + level + ".");
        }
        return messages;
    }

    /**
     * Migrates legacy single-player potion counts into the unified item inventory.
     * Kept separate from the PvP potion API so old saves remain readable.
     */
    public int migrateLegacyPotionsToItems() {
        if (potions <= 0) return 0;
        int count = potions;
        addItem(Item.health("health_1", "Med Gel", BattleEngine.potionHealAmount()));
        Item healthItem = null;
        if (inventory != null) {
            for (Item item : inventory) {
                if (item != null && "health_1".equals(item.id)) {
                    healthItem = item;
                    break;
                }
            }
        }
        if (healthItem != null) healthItem.quantity += Math.max(0, count - 1);
        potions = 0;
        return count;
    }

    public boolean usePotion() {
        if (potions <= 0 || health >= maxHealth) {
            return false;
        }
        potions--;
        heal(BattleEngine.potionHealAmount());
        return true;
    }

    public boolean isAlive() {
        return health > 0;
    }

    // ---- Combatant ----

    @Override
    public String displayName() {
        return heroClass.displayName();
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
        return mana;
    }

    @Override
    public int maxMana() {
        return maxMana;
    }

    @Override
    public void setMana(int mana) {
        this.mana = Math.max(0, Math.min(maxMana, mana));
    }

    @Override
    public int attack() {
        return attack + equipmentAttackBonus();
    }

    @Override
    public int defense() {
        return defense + equipmentDefenseBonus();
    }

    public int equipmentAttackBonus() {
        return equippedWeapon == null ? 0 : equippedWeapon.attackBonus;
    }

    public int equipmentDefenseBonus() {
        return equippedArmor == null ? 0 : equippedArmor.defenseBonus;
    }

    public void addItem(Item item) {
        if (item == null) return;
        if (inventory == null) inventory = new ArrayList<>();
        if (item.stackable) {
            for (Item existing : inventory) {
                if (existing != null && existing.stackable && item.id != null && item.id.equals(existing.id)) {
                    existing.quantity += Math.max(1, item.quantity);
                    return;
                }
            }
        }
        inventory.add(item.copy());
    }

    public boolean canUseItem(Item item) {
        if (item == null || !item.isConsumable() || item.quantity <= 0) return false;
        if (item.type == Item.Type.HEALTH) return health < maxHealth;
        return mana < maxMana;
    }

    public int useItem(Item item) {
        if (!canUseItem(item)) return 0;
        int restored = item.type == Item.Type.HEALTH ? heal(item.amount) : restoreMana(item.amount);
        if (restored > 0) {
            item.quantity--;
            if (item.quantity <= 0 && inventory != null) inventory.remove(item);
        }
        return restored;
    }

    public void applyPermanentBoost(Item item) {
        if (item == null) return;
        if (item.type == Item.Type.MAX_HEALTH) {
            maxHealth += Math.max(0, item.amount);
            health = Math.min(maxHealth, health + Math.max(0, item.amount));
        } else if (item.type == Item.Type.MAX_ENERGY) {
            maxMana += Math.max(0, item.amount);
            mana = Math.min(maxMana, mana + Math.max(0, item.amount));
        }
    }

    public void equip(Item item) {
        if (item == null || !item.isEquipment()) return;
        if (inventory == null) inventory = new ArrayList<>();
        Item previous = item.type == Item.Type.WEAPON ? equippedWeapon : equippedArmor;
        if (previous != null) inventory.add(previous);
        inventory.remove(item);
        if (item.type == Item.Type.WEAPON) equippedWeapon = item;
        else equippedArmor = item;
    }

    public void unequip(boolean weapon) {
        Item equipped = weapon ? equippedWeapon : equippedArmor;
        if (equipped == null) return;
        if (inventory == null) inventory = new ArrayList<>();
        inventory.add(equipped);
        if (weapon) equippedWeapon = null;
        else equippedArmor = null;
    }

    public String equippedWeaponName() {
        return equippedWeapon == null ? "None" : equippedWeapon.name;
    }

    public String equippedArmorName() {
        return equippedArmor == null ? "None" : equippedArmor.name;
    }

    @Override
    public int criticalChance() {
        int base = switch (heroClass) {
            case WARRIOR -> 10;
            case MAGE -> 12;
            case THIEF -> 24;
            case GRAPPLER -> 16;
            case CLERIC -> 12;
        };
        return Math.min(50, base + 3 * progression().rank(PassiveUpgrade.CRITICAL));
    }

    @Override
    public float skillMultiplier() {
        return switch (heroClass) {
            case WARRIOR -> 1.55f;
            case MAGE -> 1.85f;
            case THIEF -> 1.65f;
            case GRAPPLER -> 1.75f;
            case CLERIC -> 1.6f;
        };
    }

    @Override
    public String skillName() {
        return switch (heroClass) {
            case WARRIOR -> "breach pulse";
            case MAGE -> "system overload";
            case THIEF -> "deadeye shot";
            case GRAPPLER -> "kinetic slam";
            case CLERIC -> "nano disruptor";
        };
    }

    @Override
    public int escapeChance() {
        return Math.min(75, 38 + Math.max(0, level - 1) * 3);
    }

    @Override
    public int potions() {
        return potions;
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
