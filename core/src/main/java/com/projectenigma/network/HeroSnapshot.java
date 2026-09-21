package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;

import java.io.Serializable;

/** Immutable display state for combat menus, including authoritative inventory.
 * Clients request an action or item ID; they never submit damage or item effects.
 */
public record HeroSnapshot(String displayName, HeroClass heroClass, int level,
                            int health, int maxHealth, int mana, int maxMana, SkillSnapshot skills, InventorySnapshot inventory) implements Serializable {
    private static final long serialVersionUID = 1L;
    public HeroSnapshot(String displayName, HeroClass heroClass, int level, int health, int maxHealth, int mana, int maxMana) {
        this(displayName, heroClass, level, health, maxHealth, mana, maxMana, null, null);
    }

    public HeroSnapshot(String displayName, HeroClass heroClass, int level, int health, int maxHealth, int mana, int maxMana, SkillSnapshot skills) {
        this(displayName, heroClass, level, health, maxHealth, mana, maxMana, skills, null);
    }

    public Hero displayHero() {
        Hero hero = new Hero(heroClass);
        hero.level = level; hero.health = health; hero.maxHealth = maxHealth; hero.mana = mana; hero.maxMana = maxMana;
        if (skills != null && skills.progression() != null) hero.progression = skills.progression().restore(level);
        if (inventory != null) inventory.restore(hero);
        hero.potions = 0;
        return hero;
    }

    public static HeroSnapshot of(Hero hero, Hero opponent, com.projectenigma.model.BattleEngine engine) {
        return new HeroSnapshot(hero.displayName(), hero.heroClass, hero.level, hero.health, hero.maxHealth, hero.mana, hero.maxMana,
                SkillSnapshot.of(hero, opponent, engine), InventorySnapshot.of(hero));
    }

    public static HeroSnapshot of(Hero hero) {
        return new HeroSnapshot(hero.displayName(), hero.heroClass, hero.level,
                hero.health, hero.maxHealth, hero.mana, hero.maxMana, null, InventorySnapshot.of(hero));
    }
}
