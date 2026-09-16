package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;

import java.io.Serializable;

/**
 * Full combat-relevant snapshot of a {@link Hero} developed during a
 * Race-to-PvP exploration phase, sent exactly once by the guest -- when its
 * exploration timer ends -- so the host can build the {@code PvPMatch} from
 * the hero the guest actually earned rather than a fresh {@code HeroClass}
 * default.
 *
 * <p>Deliberately a separate type from {@link HeroSnapshot}: {@code
 * HeroSnapshot} is a live, once-per-turn, display-only broadcast the host
 * repeatedly sends for the duration of an already-running match, and it
 * intentionally omits {@code attack}/{@code defense}/{@code potions} so a
 * client never has the fields it would need to fake damage math (see
 * {@code HeroSnapshot}'s Javadoc). {@code HeroLoadout} is sent exactly
 * once, before any match exists, describing progress the guest legitimately
 * earned in its own single-player dungeon run -- there is no repeated trust
 * decision to make every turn, only a one-time "here is the hero I grew"
 * transfer, which the host sanity-clamps on arrival via {@link #toHero()}.
 */
public record HeroLoadout(HeroClass heroClass, int level, int maxHealth, int health,
                           int maxMana, int mana, int attack, int defense, int potions)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    public static HeroLoadout of(Hero hero) {
        return new HeroLoadout(hero.heroClass, hero.level, hero.maxHealth, hero.health,
                hero.maxMana, hero.mana, hero.attack(), hero.defense(), hero.potions);
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
        hero.level = Math.max(1, level);
        hero.maxHealth = reasonableCap(maxHealth, heroClass.health(), hero.level);
        hero.health = clampAtLeastOne(health, hero.maxHealth);
        hero.maxMana = reasonableCap(maxMana, heroClass.mana(), hero.level);
        hero.mana = Math.max(0, Math.min(hero.maxMana, mana));
        hero.attack = reasonableCap(attack, heroClass.attack(), hero.level);
        hero.defense = reasonableCap(defense, heroClass.defense(), hero.level);
        hero.potions = Math.max(0, Math.min(9, potions));
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
