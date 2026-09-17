package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;

import java.io.Serializable;

/**
 * Read-only render data for one hero in a PvP match. Deliberately excludes
 * attack/defense/gold/potion-count/etc. -- everything a client needs to draw
 * the HUD and nothing a client could use to second-guess the host's damage
 * math. Damage is always computed on the host; clients only ever display
 * the result.
 *
 * <p>{@code implements Serializable} is what lets this cross the wire via
 * plain {@code ObjectOutputStream}/{@code ObjectInputStream} in {@link
 * PvPConnection} -- see that class and {@code PvPServer}/{@code PvPClient}
 * for why this project uses the JDK's built-in serialization instead of a
 * third-party library. Records serialize correctly under it (their
 * serialized form round-trips through the canonical constructor), which is
 * not true of every serialization library for every Java/library version
 * combination -- see DESIGN.md's networking notes for the concrete story.
 */
public record HeroSnapshot(String displayName, HeroClass heroClass, int level,
                            int health, int maxHealth, int mana, int maxMana, SkillSnapshot skills) implements Serializable {
    private static final long serialVersionUID = 1L;
    public HeroSnapshot(String displayName, HeroClass heroClass, int level, int health, int maxHealth, int mana, int maxMana) {
        this(displayName, heroClass, level, health, maxHealth, mana, maxMana, null);
    }

    public Hero displayHero() {
        Hero hero = new Hero(heroClass);
        hero.level = level; hero.health = health; hero.maxHealth = maxHealth; hero.mana = mana; hero.maxMana = maxMana;
        if (skills != null && skills.progression() != null) hero.progression = skills.progression().restore(level);
        return hero;
    }

    public static HeroSnapshot of(Hero hero, Hero opponent, com.projectenigma.model.BattleEngine engine) {
        return new HeroSnapshot(hero.displayName(), hero.heroClass, hero.level, hero.health, hero.maxHealth, hero.mana, hero.maxMana,
                SkillSnapshot.of(hero, opponent, engine));
    }

    public static HeroSnapshot of(Hero hero) {
        return new HeroSnapshot(hero.displayName(), hero.heroClass, hero.level,
                hero.health, hero.maxHealth, hero.mana, hero.maxMana);
    }
}
