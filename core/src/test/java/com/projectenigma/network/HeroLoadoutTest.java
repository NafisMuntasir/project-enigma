package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.HeroClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link HeroLoadout} is the one place a Race-to-PvP guest's own claims
 * about its exploration-phase hero get trusted (once) by the host -- these
 * tests pin down both the honest round trip and the anti-cheat clamp.
 */
class HeroLoadoutTest {
    @Test
    void roundTripsAnHonestlyProgressedHeroExactly() {
        Hero hero = new Hero(HeroClass.WARRIOR);
        hero.level = 3;
        hero.maxHealth = 148;
        hero.health = 100;
        hero.maxMana = 12;
        hero.mana = 8;
        hero.attack = 22;
        hero.defense = 13;
        hero.potions = 2;

        Hero reconstructed = HeroLoadout.of(hero).toHero();

        assertEquals(hero.heroClass, reconstructed.heroClass);
        assertEquals(hero.level, reconstructed.level);
        assertEquals(hero.maxHealth, reconstructed.maxHealth);
        assertEquals(hero.health, reconstructed.health);
        assertEquals(hero.maxMana, reconstructed.maxMana);
        assertEquals(hero.mana, reconstructed.mana);
        assertEquals(hero.attack, reconstructed.attack);
        assertEquals(hero.defense, reconstructed.defense);
        assertEquals(hero.potions, reconstructed.potions);
    }

    @Test
    void clampsImplausibleClaimsInsteadOfTrustingThem() {
        // A modified/buggy client claiming absurd stats for a level-2 hero.
        HeroLoadout cheating = new HeroLoadout(HeroClass.WARRIOR, 2, 999_999, 999_999, 999, 999, 999, 999, 999);
        Hero hero = cheating.toHero();

        int base = HeroClass.WARRIOR.health();
        int maxPlausibleHealth = base + (2 - 1) * 14; // see HeroLoadout.reasonableCap's Javadoc
        assertEquals(maxPlausibleHealth, hero.maxHealth);
        assertEquals(hero.maxHealth, hero.health, "health should also be clamped down to the (already-clamped) max");
        assertEquals(9, hero.potions, "potions should be clamped to the sanity ceiling");
    }

    @Test
    void neverProducesAHeroBelowItsClassBaseStats() {
        // A claim *worse* than the class's own starting stats (e.g. a stale or corrupted snapshot at level 1)
        // should never be allowed to drag a hero below what BattleEngine/ActionAvailability already assume is possible.
        HeroLoadout underpowered = new HeroLoadout(HeroClass.MAGE, 1, 1, 1, 0, 0, 0, 0, 0);
        Hero hero = underpowered.toHero();

        assertEquals(HeroClass.MAGE.health(), hero.maxHealth);
        assertEquals(HeroClass.MAGE.attack(), hero.attack);
        assertEquals(HeroClass.MAGE.defense(), hero.defense);
    }
}
