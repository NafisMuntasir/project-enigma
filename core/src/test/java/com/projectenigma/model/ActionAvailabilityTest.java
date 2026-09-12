package com.projectenigma.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionAvailabilityTest {
    @Test void disabledActionsMatchEngineRejectionsWithoutSpendingTurns() {
        Hero hero = new Hero(HeroClass.MAGE); Hero enemy = new Hero(HeroClass.WARRIOR);
        BattleEngine engine = new BattleEngine(42);
        assertFalse(ActionAvailability.reason(hero, BattleAction.POTION).isEmpty());
        assertFalse(engine.resolve(hero, enemy, BattleAction.POTION).actionAccepted());
        hero.health -= 10; hero.potions = 0;
        assertFalse(ActionAvailability.reason(hero, BattleAction.POTION).isEmpty());
        assertFalse(engine.resolve(hero, enemy, BattleAction.POTION).actionAccepted());
        hero.mana = BattleEngine.skillManaCost() - 1;
        assertFalse(ActionAvailability.reason(hero, BattleAction.SKILL).isEmpty());
        assertFalse(engine.resolve(hero, enemy, BattleAction.SKILL).actionAccepted());
        hero.mana++;
        assertEquals("", ActionAvailability.reason(hero, BattleAction.SKILL));
        assertTrue(engine.resolve(hero, enemy, BattleAction.SKILL).actionAccepted());
    }
}
