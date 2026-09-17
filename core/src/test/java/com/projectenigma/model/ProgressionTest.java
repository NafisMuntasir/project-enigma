package com.projectenigma.model;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    @Test void levelBonusesAndXpRemainUnchangedAndChoicesQueuePerLevel() {
        Hero h = new Hero(HeroClass.WARRIOR);
        h.gainExperience(45 + 75 + 7);
        assertEquals(3, h.level); assertEquals(7, h.experience);
        assertEquals(148, h.maxHealth); assertEquals(148, h.health);
        assertEquals(12, h.maxMana); assertEquals(22, h.attack); assertEquals(13, h.defense);
        assertEquals(2, h.progression().pendingChoices);
        assertFalse(h.progression().choose(h, "SKILL:REACTOR_BURST"));
        assertTrue(h.progression().choose(h, "SKILL:DISRUPTION_PULSE"));
        assertTrue(h.progression().equipped(Skill.DISRUPTION_PULSE));
        assertFalse(h.progression().choose(h, "SKILL:DISRUPTION_PULSE"));
        assertTrue(h.progression().choose(h, "PASSIVE:DAMAGE"));
        assertEquals(24, h.attack); assertEquals(0, h.progression().pendingChoices);
        assertFalse(h.progression().choose(h, "PASSIVE:DEFENSE"));
    }
    @Test void eachOfferedOptionWorksAndSpendsExactlyOneChoice() {
        Hero template = new Hero(HeroClass.MAGE); template.level = 5; template.progression().pendingChoices = 1;
        for (var choice : template.progression().choices(template)) {
            Hero hero = new Hero(HeroClass.MAGE); hero.level = 5; hero.progression().pendingChoices = 1;
            assertTrue(hero.progression().choose(hero, choice.id()), choice.title());
            assertEquals(0, hero.progression().pendingChoices);
            if (choice.skill() != null) assertTrue(hero.progression().unlocked(choice.skill()));
            if (choice.passive() != null) assertEquals(1, hero.progression().rank(choice.passive()));
        }
    }
    @Test void passiveCapsAndDerivedStatsAreReal() {
        Hero h = new Hero(HeroClass.THIEF); h.level = 30; h.progression().pendingChoices = 29;
        for (int i = 0; i < 5; i++) assertTrue(h.progression().choose(h, "PASSIVE:CRITICAL"));
        assertEquals(39, h.criticalChance()); assertFalse(h.progression().choose(h, "PASSIVE:CRITICAL"));
        h.progression().choose(h, "PASSIVE:HEALING"); h.health = 1;
        assertEquals(22, h.heal(20));
        h.progression().choose(h, "PASSIVE:MOVEMENT"); assertEquals(1.08f, h.progression().movementMultiplier(), .001);
        h.progression().choose(h, "PASSIVE:COOLDOWN"); assertEquals(1, h.progression().cooldown(Skill.PHOTON_LANCE));
    }
    @Test void saveRoundTripAndOldSavesKeepProgressWithoutRetroactiveRewards() {
        Json json = new Json();
        GameSession session = new GameSession(10, HeroClass.CLERIC);
        session.hero.gainExperience(120);
        session.hero.progression().choose(session.hero, "SKILL:NANITE_RESTORATION");
        session.hero.health -= 40;
        assertTrue(session.hero.progression().useField(session.hero, Skill.NANITE_RESTORATION));
        GameSession loaded = json.fromJson(GameSession.class, json.toJson(session)); loaded.rebuildTransientState();
        assertEquals(1, loaded.hero.progression().pendingChoices);
        assertTrue(loaded.hero.progression().equipped(Skill.NANITE_RESTORATION));
        assertEquals(12, loaded.hero.progression().restorationSteps);
        Hero legacy = json.fromJson(Hero.class, "{\"level\":8,\"attack\":36}");
        assertEquals(36, legacy.attack); assertEquals(0, legacy.progression().pendingChoices);
        assertTrue(legacy.progression().unlocked.isEmpty());
        legacy.gainExperience(legacy.experienceForNextLevel()); assertEquals(1, legacy.progression().pendingChoices);
    }
    @Test void equippedSlotsAndFieldRecoveryCannotBeBypassedByReequippingOrReloading() {
        Hero h = new Hero(HeroClass.MAGE); h.level = 4; h.progression().pendingChoices = 3;
        h.progression().choose(h, "SKILL:VECTOR_BOOST");
        assertTrue(h.progression().useField(h, Skill.VECTOR_BOOST));
        assertEquals(1.4f, h.progression().movementMultiplier(), .001);
        h.progression().equip(Skill.VECTOR_BOOST, 2);
        assertFalse(h.progression().useField(h, Skill.VECTOR_BOOST));
        for (int i = 0; i < 20; i++) h.progression().moved();
        assertEquals(1, h.progression().movementMultiplier());
        assertEquals(10, h.progression().vectorCooldownSteps);
        for (int i = 0; i < 10; i++) h.progression().moved();
        assertTrue(h.progression().useField(h, Skill.VECTOR_BOOST));
        assertEquals(1, h.progression().equipped.stream().filter(Skill.VECTOR_BOOST.name()::equals).count());
    }
}
