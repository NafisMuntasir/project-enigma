package com.projectenigma.network;

import com.projectenigma.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatConsistencyTest {
    private Hero hero() {
        Hero hero = new Hero(HeroClass.MAGE);
        hero.level = 15; hero.health = 40; hero.mana = hero.maxMana = 100;
        hero.migrateLegacyPotionsToItems();
        return hero;
    }
    @ParameterizedTest @EnumSource(Skill.class)
    void everyGeneralSkillResolvesIdenticallyInSoloAndPvp(Skill skill) {
        Hero source = hero(); source.progression().unlocked.add(skill.name()); source.progression().equip(skill, 0);
        Hero solo = HeroLoadout.of(source).toHero(), network = HeroLoadout.of(source).toHero();
        Hero enemy = hero(); enemy.health = enemy.maxHealth = 1000;
        Hero opponent = HeroLoadout.of(enemy).toHero();
        BattleEngine engine = new BattleEngine(72);
        PvPMatch match = new PvPMatch(network, opponent, 72);
        TurnResult expected = engine.resolveSkill(solo, enemy, skill);
        PvPBattleState actual = match.applySkill(0, skill);
        assertEquals(solo.health, actual.player0().health()); assertEquals(solo.mana, actual.player0().mana());
        assertEquals(enemy.health, actual.player1().health());
        assertEquals(expected.actionAccepted() ? 1 : 0, actual.currentTurn());
        assertEquals(engine.effects(solo).summary(), actual.player0().skills().status());
        assertEquals(engine.effects(enemy).summary(), actual.player1().skills().status());
        if (expected.actionAccepted()) {
            engine.resolve(enemy, solo, BattleAction.GUARD);
            actual = match.applyAction(1, BattleAction.GUARD);
            assertEquals(enemy.health, actual.player1().health());
            assertEquals(engine.effects(enemy).summary(), actual.player1().skills().status());
        }
    }
    @ParameterizedTest @EnumSource(value = HeroClass.class)
    void equippedStatsAndClassTechniqueUseIdenticalDamage(HeroClass type) {
        Hero source = new Hero(type);
        source.equippedWeapon = Item.weapon("weapon", "Weapon", "", 9);
        source.equippedArmor = Item.armor("armor", "Armor", "", 6);
        Hero solo = HeroLoadout.of(source).toHero(), remote = HeroLoadout.of(source).toHero();
        assertEquals(source.attack(), remote.attack()); assertEquals(source.defense(), remote.defense());
        Hero enemy = new Hero(HeroClass.WARRIOR), opponent = new Hero(HeroClass.WARRIOR);
        new BattleEngine(88).resolve(solo, enemy, BattleAction.SKILL);
        PvPBattleState state = new PvPMatch(remote, opponent, 88).applyAction(0, BattleAction.SKILL);
        assertEquals(solo.mana, state.player0().mana()); assertEquals(enemy.health, state.player1().health());
    }
    @Test void inventoryRoundTripsWithoutAliasingOrDoubleEquipmentBonuses() throws Exception {
        Hero source = hero();
        source.addItem(Item.energy("cell", "Cell", 7)); source.addItem(Item.energy("cell", "Cell", 7));
        source.equippedWeapon = Item.weapon("weapon", "Weapon", "", 9);
        source.equippedArmor = Item.armor("armor", "Armor", "", 6);
        source.applyPermanentBoost(Item.maxHealth("boost", "Boost", 25));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(HeroLoadout.of(source)); }
        Hero restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = ((HeroLoadout)in.readObject()).toHero();
        }
        assertEquals(InventorySnapshot.of(source), InventorySnapshot.of(restored));
        assertEquals(source.maxHealth, restored.maxHealth);
        assertEquals(source.attack(), restored.attack()); assertEquals(source.defense(), restored.defense());
        restored.inventory.get(0).quantity--;
        assertNotEquals(source.inventory.get(0).quantity, restored.inventory.get(0).quantity);
    }
    @Test void itemsShareHealingBonusesStatusTicksAndHostValidation() {
        Hero solo = hero(), remote = hero(), enemy = hero(), opponent = hero();
        solo.progression().passiveRanks[PassiveUpgrade.HEALING.ordinal()] = 2;
        remote.progression().passiveRanks[PassiveUpgrade.HEALING.ordinal()] = 2;
        BattleEngine engine = new BattleEngine(12);
        PvPMatch match = new PvPMatch(remote, opponent, 12);
        Item item = solo.inventory.get(0);
        engine.resolveItem(solo, enemy, item);
        PvPBattleState state = match.applyItem(0, item.id);
        assertEquals(solo.health, state.player0().health());
        assertEquals(InventorySnapshot.of(solo), state.player0().inventory());
        int quantity = remote.inventory.get(0).quantity;
        match.applyItem(0, item.id); // Duplicate / wrong turn.
        assertEquals(quantity, remote.inventory.get(0).quantity);
        match.applyItem(1, "forged-id"); assertEquals(1, match.currentState().currentTurn());
        match.pauseForDisconnect(); match.applyItem(1, item.id);
        assertEquals(3, opponent.inventory.get(0).quantity);
        assertEquals(state.player0().inventory(), match.resume().player0().inventory());
    }
    @Test void oldEncounterGuardDoesNotGiveHostAnAdvantage() {
        Hero host = hero(), guest = hero(); host.guarding = true; guest.guarding = true;
        new PvPMatch(host, guest, 1);
        assertFalse(host.guarding); assertFalse(guest.guarding);
    }
    @Test void interruptedItemUseConsumesTheTurnButNotTheItemInBothModes() {
        Hero host = hero(), guest = hero();
        host.progression().unlocked.add(Skill.DISRUPTION_PULSE.name());
        host.progression().equip(Skill.DISRUPTION_PULSE, 0);
        Hero soloHost = HeroLoadout.of(host).toHero(), soloGuest = HeroLoadout.of(guest).toHero();
        BattleEngine engine = new BattleEngine(1);
        PvPMatch match = new PvPMatch(host, guest, 1);
        engine.resolveSkill(soloHost, soloGuest, Skill.DISRUPTION_PULSE);
        match.applySkill(0, Skill.DISRUPTION_PULSE);
        engine.resolveItem(soloGuest, soloHost, soloGuest.inventory.get(0));
        PvPBattleState state = match.applyItem(1, guest.inventory.get(0).id);
        assertEquals(0, state.currentTurn());
        assertEquals(40, guest.health);
        assertEquals(3, guest.inventory.get(0).quantity);
        assertEquals(InventorySnapshot.of(soloGuest), state.player1().inventory());
        assertEquals(engine.effects(soloGuest).summary(), state.player1().skills().status());
    }
}
