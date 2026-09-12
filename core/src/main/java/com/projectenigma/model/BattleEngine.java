package com.projectenigma.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure Java turn resolver, independent from rendering and from any specific
 * pair of combatants. Resolves exactly one acting combatant's action
 * against one opponent.
 *
 * <p>BattleEngine intentionally does NOT own turn order or automatically
 * resolve a "reply" turn -- callers decide that. This is what lets the same
 * engine serve two different turn-order shapes with no branching inside the
 * engine itself:
 * <ul>
 *   <li>PvE (see {@code CombatScreen}): after the hero's turn resolves with
 *       outcome ONGOING, the caller immediately resolves one automatic
 *       enemy ATTACK turn.</li>
 *   <li>PvP (see {@code network.PvPMatch}): after a turn resolves with
 *       outcome ONGOING, the caller flips {@code currentTurn} and waits for
 *       the other player's action instead of resolving anything
 *       automatically.</li>
 * </ul>
 *
 * <p>Outcomes are always relative to the acting combatant passed as
 * {@code attacker}:
 * <ul>
 *   <li>{@code VICTORY} - the defender was just defeated by this action.</li>
 *   <li>{@code ESCAPED} - the attacker fled (RUN succeeded). In PvP this is
 *       a surrender: the caller should treat the other player as the
 *       winner.</li>
 *   <li>{@code DEFEAT} - the attacker could not act because it was already
 *       dead when {@code resolve} was called; this is a defensive guard,
 *       not a normal path.</li>
 *   <li>{@code ONGOING} - the battle continues.</li>
 * </ul>
 */
public final class BattleEngine {
    private static final int POTION_HEAL_AMOUNT = 35;
    private static final int SKILL_MANA_COST = 3;

    private final Random random;

    public BattleEngine(long seed) {
        this.random = new Random(seed);
    }

    public TurnResult resolve(Combatant attacker, Combatant defender, BattleAction action) {
        if (attacker == null || defender == null || action == null) {
            throw new IllegalArgumentException("Attacker, defender, and action are required");
        }
        if (!attacker.isAlive()) {
            return new TurnResult(BattleOutcome.DEFEAT, List.of(attacker.displayName() + " has fallen."), false);
        }
        if (!defender.isAlive()) {
            return new TurnResult(BattleOutcome.VICTORY, List.of(defender.displayName() + " is already defeated."), false);
        }

        List<String> messages = new ArrayList<>(4);
        switch (action) {
            case ATTACK -> {
                applyDamage(attacker, defender, messages, 1.0f, false);
                return afterDamage(attacker, defender, messages);
            }
            case SKILL -> {
                if (attacker.mana() < SKILL_MANA_COST) {
                    return new TurnResult(BattleOutcome.ONGOING,
                            List.of("Not enough EN for " + attacker.displayName() + "'s tech skill."), false);
                }
                attacker.setMana(attacker.mana() - SKILL_MANA_COST);
                applyDamage(attacker, defender, messages, attacker.skillMultiplier(), true);
                return afterDamage(attacker, defender, messages);
            }
            case GUARD -> {
                attacker.setGuarding(true);
                messages.add(attacker.displayName() + " braces for the next hit.");
                return new TurnResult(BattleOutcome.ONGOING, messages, true);
            }
            case POTION -> {
                int healthBefore = attacker.health();
                if (!attacker.usePotion()) {
                    String reason = attacker.potions() <= 0 ? "No potions remain." : "Health is already full.";
                    return new TurnResult(BattleOutcome.ONGOING, List.of(reason), false);
                }
                messages.add(attacker.displayName() + "'s potion restores " + (attacker.health() - healthBefore) + " HP.");
                return new TurnResult(BattleOutcome.ONGOING, messages, true);
            }
            case RUN -> {
                int chance = attacker.escapeChance();
                if (random.nextInt(100) < chance) {
                    messages.add(attacker.displayName() + " escapes the fight.");
                    return new TurnResult(BattleOutcome.ESCAPED, messages, true);
                }
                messages.add(attacker.displayName() + "'s escape attempt fails!");
                return new TurnResult(BattleOutcome.ONGOING, messages, true);
            }
            default -> throw new IllegalStateException("Unhandled action " + action);
        }
    }

    private TurnResult afterDamage(Combatant attacker, Combatant defender, List<String> messages) {
        if (!defender.isAlive()) {
            messages.add(defender.displayName() + " is defeated.");
            return new TurnResult(BattleOutcome.VICTORY, messages, true);
        }
        return new TurnResult(BattleOutcome.ONGOING, messages, true);
    }

    private void applyDamage(Combatant attacker, Combatant defender, List<String> messages, float multiplier, boolean skill) {
        int variance = random.nextInt(5) - 2;
        boolean critical = random.nextInt(100) < attacker.criticalChance();
        float criticalMultiplier = critical ? 1.6f : 1f;
        int rawDamage = Math.max(1, Math.round((attacker.attack() + variance) * multiplier * criticalMultiplier));
        int damage = Math.max(1, rawDamage - defender.defense());

        boolean wasGuarding = defender.isGuarding();
        if (wasGuarding) {
            damage = Math.max(1, (damage + 1) / 2);
            defender.setGuarding(false);
        }
        defender.setHealth(Math.max(0, defender.health() - damage));

        String move = skill ? attacker.skillName() : "attack";
        String guardNote = wasGuarding ? " (guarded)" : "";
        messages.add(attacker.displayName() + "'s " + move + " deals " + damage + " damage"
                + (critical ? " (critical)" : "") + guardNote + ".");
    }

    public static int skillManaCost() { return SKILL_MANA_COST; }

    public static int potionHealAmount() {
        return POTION_HEAL_AMOUNT;
    }
}
