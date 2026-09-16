package com.projectenigma.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemEquipmentTest {
    @Test
    void consumablesStackAndCanBeUsed() {
        Hero hero = new Hero(HeroClass.WARRIOR);
        hero.health = 60;
        hero.addItem(Item.health("health_1", "Med Gel", 30));
        hero.addItem(Item.health("health_1", "Med Gel", 30));

        assertEquals(1, hero.inventory.size());
        assertEquals(2, hero.inventory.get(0).quantity);
        assertEquals(30, hero.useItem(hero.inventory.get(0)));
        assertEquals(1, hero.inventory.get(0).quantity);
    }

    @Test
    void equipmentChangesEffectiveCombatStats() {
        Hero hero = new Hero(HeroClass.WARRIOR);
        Item weapon = Item.weapon("weapon_1", "Pulse Blade", "+5 ATK", 5);
        Item armor = Item.armor("armor_1", "Photon Mantle", "+3 DEF", 3);
        hero.addItem(weapon);
        hero.addItem(armor);

        hero.equip(hero.inventory.get(0));
        hero.equip(hero.inventory.get(0));

        assertNotNull(hero.equippedWeapon);
        assertNotNull(hero.equippedArmor);
        assertEquals(hero.heroClass.attack() + 5, hero.attack());
        assertEquals(hero.heroClass.defense() + 3, hero.defense());
    }

    @Test
    void permanentBoostIsAppliedImmediately() {
        Hero hero = new Hero(HeroClass.MAGE);
        int hp = hero.maxHealth;
        int en = hero.maxMana;
        hero.applyPermanentBoost(Item.maxHealth("mh", "Vitality Lattice", 15));
        hero.applyPermanentBoost(Item.maxEnergy("me", "Core Capacitor", 2));

        assertEquals(hp + 15, hero.maxHealth);
        assertEquals(en + 2, hero.maxMana);
        assertTrue(hero.health <= hero.maxHealth);
        assertTrue(hero.mana <= hero.maxMana);
    }

    @Test
    void floorContainsPersistablePickups() {
        GameSession session = new GameSession(20260916L, HeroClass.CLERIC);
        assertTrue(session.pickups.size() >= 3);
        assertNotNull(session.pickups.get(0).item);
    }
}
