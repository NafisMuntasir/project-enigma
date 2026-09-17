package com.projectenigma.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class GeneralSkillTest {
    @Test void damageOverTimeCannotMakeOverclockPayALethalHealthCost() {
        Hero h = hero(Skill.OVERCLOCK); var e = target(); var engine = new BattleEngine(1);
        h.health = 12;
        engine.effects(h).burnTurns = 1;
        engine.effects(h).burnDamage = 5;
        TurnResult turn = engine.resolveSkill(h, e, Skill.OVERCLOCK);
        assertTrue(turn.actionAccepted());
        assertEquals(7, h.health);
        assertEquals(100, h.mana);
        assertEquals(0, engine.effects(h).overclockHits);
        assertEquals(0, engine.effects(h).cooldown(Skill.OVERCLOCK));
        assertEquals(0, engine.effects(h).burnTurns);
    }
    private Hero hero(Skill... skills) {
        Hero h = new Hero(HeroClass.MAGE); h.level = 15; h.mana = h.maxMana = 100;
        for (int i = 0; i < skills.length; i++) { h.progression().unlocked.add(skills[i].name()); h.progression().equip(skills[i], i); }
        return h;
    }
    private DungeonEnemy target() { var e = new DungeonEnemy(1, EnemyType.FLOOR_WARDEN, 2, 2, 1); e.health = e.maxHealth = 1000; return e; }
    @Test void stunAndShockSkipExactlyOneReplyAndPreventChainInterrupts() {
        for (Skill s : new Skill[]{Skill.DISRUPTION_PULSE, Skill.ARC_DISCHARGE}) {
            Hero h = hero(s); var e = target(); var engine = new BattleEngine(10);
            int hp = h.health, enemyHp = e.health;
            assertTrue(engine.resolveSkill(h, e, s).actionAccepted());
            assertEquals(s == Skill.ARC_DISCHARGE, e.health < enemyHp);
            assertTrue(engine.resolve(e, h, BattleAction.ATTACK).actionAccepted());
            assertEquals(hp, h.health);
            assertEquals(1, engine.effects(e).resistance);
            engine.resolve(e, h, BattleAction.ATTACK); assertTrue(h.health < hp);
        }
    }
    @Test void burnTicksTwiceAndLethalDotEndsTheTargetsTurnBeforeItCanAttack() {
        Hero h = hero(Skill.THERMAL_OVERLOAD); var e = target(); var engine = new BattleEngine(5);
        engine.resolveSkill(h, e, Skill.THERMAL_OVERLOAD); int hp = e.health;
        engine.resolve(e, h, BattleAction.GUARD); assertEquals(hp - 22, e.health);
        engine.resolve(e, h, BattleAction.GUARD); assertEquals(hp - 44, e.health);
        engine.resolve(e, h, BattleAction.GUARD); assertEquals(hp - 44, e.health);
        engine = new BattleEngine(5); engine.resolveSkill(h, e, Skill.THERMAL_OVERLOAD); e.health = 1;
        int heroHp = h.health;
        assertEquals(BattleOutcome.DEFEAT, engine.resolve(e, h, BattleAction.ATTACK).outcome());
        assertEquals(heroHp, h.health); assertEquals(0, e.health);
    }
    @Test void burstIsStrongExpensiveAndInvalidRequestsNeverAdvanceCooldowns() {
        Hero h = hero(Skill.REACTOR_BURST); var e = target(); var engine = new BattleEngine(123);
        int en = h.mana, hp = e.health;
        engine.resolveSkill(h, e, Skill.REACTOR_BURST);
        assertTrue(hp - e.health >= 30); assertEquals(en - 8, h.mana);
        assertEquals(5, engine.effects(h).cooldown(Skill.REACTOR_BURST));
        assertFalse(engine.resolveSkill(h, e, Skill.REACTOR_BURST).actionAccepted());
        assertEquals(5, engine.effects(h).cooldown(Skill.REACTOR_BURST));
        h.mana = 0; assertFalse(engine.resolve(h, e, BattleAction.SKILL).actionAccepted());
        assertEquals(5, engine.effects(h).cooldown(Skill.REACTOR_BURST));
        for (int i = 0; i < 5; i++) engine.resolve(h, e, BattleAction.GUARD);
        h.mana = 8; assertTrue(engine.resolveSkill(h, e, Skill.REACTOR_BURST).actionAccepted());
    }
    @Test void lockedAndUnequippedSkillsAreRejectedWithoutChangingState() {
        Hero h = hero(); var e = target(); var engine = new BattleEngine(1);
        assertFalse(engine.resolveSkill(h, e, Skill.PHOTON_LANCE).actionAccepted());
        h.progression().unlocked.add(Skill.PHOTON_LANCE.name());
        assertFalse(engine.resolveSkill(h, e, Skill.PHOTON_LANCE).actionAccepted());
        assertEquals(100, h.mana); assertEquals(1000, e.health);
    }
    @Test void barriersPhaseAndDeflectionHaveDistinctFiniteProtection() {
        Hero h = hero(Skill.ENERGY_BARRIER, Skill.PHASE_SHIFT, Skill.DEFLECTION_FIELD); var e = target(); var engine = new BattleEngine(2);
        engine.resolveSkill(h, e, Skill.ENERGY_BARRIER); int hp = h.health;
        engine.resolve(e, h, BattleAction.ATTACK); assertEquals(hp, h.health); assertTrue(engine.effects(h).barrier < 54);
        engine.effects(h).barrier = 0;
        engine.resolveSkill(h, e, Skill.PHASE_SHIFT); engine.resolve(e, h, BattleAction.ATTACK); assertEquals(hp, h.health);
        engine.resolve(e, h, BattleAction.ATTACK); assertTrue(h.health < hp);
        engine.resolveSkill(h, e, Skill.DEFLECTION_FIELD);
        engine.resolve(e, h, BattleAction.ATTACK); assertEquals(1, engine.effects(h).deflectHits);
        engine.resolve(e, h, BattleAction.ATTACK); assertEquals(0, engine.effects(h).deflectHits);
    }
    @Test void repairTicksThreeTimesAndItemsParticipateInTurnTiming() {
        Hero h = hero(Skill.EMERGENCY_REPAIR); var e = target(); var engine = new BattleEngine(1);
        h.health = 1; engine.resolveSkill(h, e, Skill.EMERGENCY_REPAIR); assertEquals(1, h.health);
        h.addItem(Item.energy("cell", "Cell", 1)); h.mana -= 1;
        engine.resolveItem(h, e, h.inventory.get(0)); assertEquals(26, h.health);
        engine.resolve(h, e, BattleAction.GUARD); assertEquals(51, h.health);
        engine.resolve(h, e, BattleAction.GUARD); assertEquals(76, h.health);
        engine.resolve(h, e, BattleAction.GUARD); assertEquals(76, h.health);
    }
    @ParameterizedTest @EnumSource(HeroClass.class)
    void originalTechnicalSkillStillCostsThreeEnergyAndUsesClassMultiplier(HeroClass type) {
        Hero h = new Hero(type); var e = target();
        TurnResult turn = new BattleEngine(22).resolve(h, e, BattleAction.SKILL);
        assertTrue(turn.actionAccepted()); assertEquals(type.mana() - 3, h.mana);
        assertTrue(turn.messages().get(0).contains(h.skillName())); assertTrue(e.health < e.maxHealth);
    }
    @ParameterizedTest @EnumSource(Skill.class)
    void everyCatalogueSkillHasAWorkingEffect(Skill skill) {
        Hero h = hero(skill); h.health = 40; var e = target(); var engine = new BattleEngine(6);
        if (!skill.combatUsable()) { assertTrue(h.progression().useField(h, skill)); assertTrue(h.progression().vectorSteps > 0); return; }
        assertTrue(engine.resolveSkill(h, e, skill).actionAccepted(), skill.title);
        assertEquals(100 - skill.cost, h.mana);
        assertTrue(e.health < e.maxHealth || !engine.effects(e).summary().equals("No active effects")
                || !engine.effects(h).summary().equals("No active effects") || h.health > 40);
    }
}
