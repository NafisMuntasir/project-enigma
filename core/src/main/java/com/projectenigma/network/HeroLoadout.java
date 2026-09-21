package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;

import java.io.Serializable;

/**
 * Rush handoff for both players: base stats, progression, inventory and equipped
 * gear. Base ATK/DEF exclude equipment so reconstruction cannot apply it twice.
 * Exploration is locally simulated, as before; the host bounds incoming values
 * and owns all subsequent combat, item consumption and turn validation.
 */
public record HeroLoadout(HeroClass heroClass, int level, int maxHealth, int health,
                           int maxMana, int mana, int attack, int defense, int potions, ProgressionSnapshot progression, InventorySnapshot inventory)
        implements Serializable {
    private static final long serialVersionUID = 1L;
    public HeroLoadout(HeroClass heroClass, int level, int maxHealth, int health, int maxMana, int mana, int attack, int defense, int potions) {
        this(heroClass, level, maxHealth, health, maxMana, mana, attack, defense, potions, null, null);
    }

    public HeroLoadout(HeroClass heroClass, int level, int maxHealth, int health, int maxMana, int mana, int attack, int defense, int potions, ProgressionSnapshot progression) {
        this(heroClass, level, maxHealth, health, maxMana, mana, attack, defense, potions, progression, null);
    }

    public static HeroLoadout of(Hero hero) {
        return new HeroLoadout(hero.heroClass, hero.level, hero.maxHealth, hero.health,
                hero.maxMana, hero.mana, hero.attack, hero.defense, hero.potions, ProgressionSnapshot.of(hero), InventorySnapshot.of(hero));
    }

    /**
     * Reconstructs a full combat {@link Hero} from this loadout for use as
     * one side of a {@code PvPMatch}. Every progressed value is clamped to
     * a generous but finite ceiling derived from {@code level} (see
     * {@link #reasonableCap}) so a modified or buggy client claiming
     * impossible progress cannot hand the host an unbeatable opponent --
     * the host is still the one constructing and resolving the match, this
     * just stops it from trusting the numbers blindly.
     */
    public Hero toHero() {
        Hero hero = new Hero(heroClass);
        hero.level = Math.max(1, Math.min(100, level));
        if (progression != null) hero.progression = progression.restore(hero.level);
        hero.maxHealth = reasonableCap(maxHealth, heroClass.health(), hero.level);
        hero.maxHealth = Math.max(hero.maxHealth, Math.min(maxHealth,
                heroClass.health() + (hero.level - 1) * MAX_PER_LEVEL_GAIN + 12 * hero.progression().rank(com.projectenigma.model.PassiveUpgrade.VITALITY)));
        hero.health = clampAtLeastOne(health, hero.maxHealth);
        hero.maxMana = reasonableCap(maxMana, heroClass.mana(), hero.level);
        hero.mana = Math.max(0, Math.min(hero.maxMana, mana));
        hero.attack = reasonableCap(attack, heroClass.attack(), hero.level);
        hero.defense = reasonableCap(defense, heroClass.defense(), hero.level);
        hero.potions = Math.max(0, Math.min(9, potions));
        if (inventory != null) {
            // Permanent pickups can increase stats without increasing level. Preserve them,
            // with finite transfer bounds, rather than treating every boost as a level gain.
            hero.maxHealth = Math.max(hero.maxHealth, Math.min(100000, maxHealth));
            hero.maxMana = Math.max(hero.maxMana, Math.min(100000, maxMana));
            hero.attack = Math.max(hero.attack, Math.min(10000, attack));
            hero.defense = Math.max(hero.defense, Math.min(10000, defense));
            hero.health = clampAtLeastOne(health, hero.maxHealth);
            hero.mana = Math.max(0, Math.min(hero.maxMana, mana));
            inventory.restore(hero);
        }
        return hero;
    }

    private static int clampAtLeastOne(int claimedHealth, int maxHealth) {
        return Math.max(1, Math.min(maxHealth, claimedHealth));
    }

    /**
     * The largest per-level increase {@code Hero.gainExperience} grants any
     * class to any stat is 14 (max health, for Warrior/Grappler). Using
     * that figure uniformly for every stat is deliberately generous rather
     * than tight -- this is an anti-cheat sanity ceiling, not a balance
     * mechanism -- while still being finite.
     */
    private static final int MAX_PER_LEVEL_GAIN = 14;

    private static int reasonableCap(int claimed, int base, int level) {
        int maxPlausible = base + Math.max(0, level - 1) * MAX_PER_LEVEL_GAIN;
        return Math.max(base, Math.min(claimed, maxPlausible));
    }
}
