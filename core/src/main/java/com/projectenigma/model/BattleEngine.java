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
    private final java.util.IdentityHashMap<Combatant, CombatEffects> effects = new java.util.IdentityHashMap<>();

    public CombatEffects effects(Combatant actor) { return effects.computeIfAbsent(actor, ignored -> new CombatEffects()); }

    public String skillReason(Hero actor, Combatant target, Skill skill) {
        if (skill == null) return "Unknown skill.";
        if (!actor.isAlive()) return "This combatant has fallen.";
        if (!target.isAlive()) return "Battle finished.";
        if (!actor.progression().unlocked(skill)) return "Unlock this skill when you level up.";
        if (!actor.progression().equipped(skill)) return "Equip this skill before entering combat.";
        if (!skill.combatUsable()) return "Field skill: use during exploration.";
        if (effects(actor).cooldown(skill) > 0) return "Cooldown: " + effects(actor).cooldown(skill) + " actions remaining.";
        if (actor.mana < skill.cost) return "Not enough energy.";
        if (skill.effect == Skill.Effect.STUN && (effects(target).stun > 0 || effects(target).resistance > 0)) return "Target resists another interrupt.";
        if ((skill.effect == Skill.Effect.HEAL || skill.effect == Skill.Effect.REPAIR) && actor.health >= actor.maxHealth) return "Health is already full.";
        if (skill.effect == Skill.Effect.OVERCLOCK && actor.health <= 8) return "Requires more than 8 HP.";
        return "";
    }

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

        String unavailable = ActionAvailability.reason(attacker, action);
        if (!unavailable.isEmpty()) return new TurnResult(BattleOutcome.ONGOING, List.of(unavailable), false);
        return withTurn(attacker, defender, () -> resolveAction(attacker, defender, action), null);
    }

    private TurnResult resolveAction(Combatant attacker, Combatant defender, BattleAction action) {
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

    public TurnResult resolveSkill(Hero actor, Combatant target, Skill skill) {
        String reason = skillReason(actor, target, skill);
        if (!reason.isEmpty()) return new TurnResult(BattleOutcome.ONGOING, List.of(reason), false);
        return withTurn(actor, target, () -> {
            actor.setMana(actor.mana - skill.cost);
            List<String> log = new ArrayList<>();
            log.add(actor.displayName() + " activates " + skill.title + ".");
            CombatEffects own = effects(actor), other = effects(target);
            if (skill.multiplier > 0) applyDamage(actor, target, log, skill.multiplier, false, skill.effect == Skill.Effect.PIERCE, skill.title);
            switch (skill.effect) {
                case STUN -> other.stun = 1;
                case BURN -> { other.burnTurns = skill.duration; other.burnDamage = 7 + actor.level; }
                case CORROSION -> { other.corrosionTurns = skill.duration; other.corrosionDamage = 4 + actor.level; }
                case DEFLECT -> own.deflectHits = skill.duration;
                case BARRIER -> own.barrier = 24 + 2 * actor.level;
                case PHASE -> own.phaseHits = 1;
                case HEAL -> log.add("Nanites restore " + actor.heal(24 + 2 * actor.level) + " HP.");
                case REPAIR -> { own.repairTurns = skill.duration; own.repairAmount = 10 + actor.level; }
                case SCAN -> { other.exposedHits = skill.duration; other.scanned = true;
                    log.add(target.displayName() + ": ATK " + target.attack() + " / DEF " + target.defense() + "."); }
                case OVERCLOCK -> { actor.setHealth(actor.health - 8); own.overclockHits = skill.duration; }
                default -> { }
            }
            return afterDamage(actor, target, log);
        }, skill);
    }

    public TurnResult resolveItem(Hero actor, Combatant target, Item item) {
        if (!actor.isAlive() || !target.isAlive() || item == null || !actor.inventory.contains(item) || !actor.canUseItem(item))
            return new TurnResult(BattleOutcome.ONGOING, List.of("Cannot use this item right now."), false);
        return withTurn(actor, target, () -> {
            int amount = actor.useItem(item);
            return new TurnResult(BattleOutcome.ONGOING, List.of(actor.displayName() + " uses " + item.name + " and restores " + amount
                    + (item.type == Item.Type.HEALTH ? " HP." : " EN.")), true);
        }, null);
    }

    /** Invalid requests never tick time. Each accepted action (including a skipped one) advances one personal turn. */
    private TurnResult withTurn(Combatant actor, Combatant target, java.util.function.Supplier<TurnResult> action, Skill used) {
        CombatEffects state = effects(actor);
        List<String> log = new ArrayList<>();
        if (state.burnTurns > 0) { state.burnTurns--; periodicDamage(actor, state.burnDamage, "Thermal overload", log); }
        if (state.corrosionTurns > 0 && actor.isAlive()) { state.corrosionTurns--; periodicDamage(actor, state.corrosionDamage, "Nanite corrosion", log); }
        if (!actor.isAlive()) { state.advanceCooldowns(); return new TurnResult(BattleOutcome.DEFEAT, log, true); }
        if (state.repairTurns > 0) {
            state.repairTurns--;
            int before = actor.health();
            if (actor instanceof Hero hero) hero.heal(state.repairAmount);
            else actor.setHealth(actor.health() + state.repairAmount);
            log.add("Repair restores " + (actor.health() - before) + " HP to " + actor.displayName() + ".");
        }
        if (state.stun > 0) {
            state.stun--; state.resistance = 1; state.advanceCooldowns();
            log.add(actor.displayName() + " is interrupted and loses this action.");
            return new TurnResult(BattleOutcome.ONGOING, log, true);
        }
        state.resistance = Math.max(0, state.resistance - 1);
        // Periodic damage can invalidate Overclock's HP cost after initial validation.
        if (used == Skill.OVERCLOCK && actor.health() <= 8) {
            state.advanceCooldowns();
            log.add("Overclock interrupted: requires more than 8 HP after status effects.");
            return new TurnResult(BattleOutcome.ONGOING, log, true);
        }
        TurnResult result = action.get();
        if (result.actionAccepted()) {
            state.advanceCooldowns();
            if (used != null && actor instanceof Hero hero) state.cooldowns.put(used, hero.progression().cooldown(used));
        }
        log.addAll(result.messages());
        return new TurnResult(result.outcome(), log, result.actionAccepted());
    }

    private void periodicDamage(Combatant actor, int amount, String name, List<String> log) {
        int damage = absorb(actor, amount);
        actor.setHealth(actor.health() - damage);
        log.add(name + " deals " + damage + " damage to " + actor.displayName() + ".");
    }
    private int absorb(Combatant target, int damage) {
        CombatEffects state = effects(target);
        int absorbed = Math.min(state.barrier, damage);
        state.barrier -= absorbed;
        return damage - absorbed;
    }

    private TurnResult afterDamage(Combatant attacker, Combatant defender, List<String> messages) {
        if (!defender.isAlive()) {
            messages.add(defender.displayName() + " is defeated.");
            return new TurnResult(BattleOutcome.VICTORY, messages, true);
        }
        return new TurnResult(BattleOutcome.ONGOING, messages, true);
    }

    private void applyDamage(Combatant attacker, Combatant defender, List<String> messages, float multiplier, boolean skill) {
        applyDamage(attacker, defender, messages, multiplier, skill, false, null);
    }

    private void applyDamage(Combatant attacker, Combatant defender, List<String> messages, float multiplier, boolean skill, boolean pierce, String name) {
        CombatEffects own = effects(attacker), other = effects(defender);
        if (own.overclockHits > 0) { multiplier *= 1.3f; own.overclockHits--; }
        if (other.exposedHits > 0) { multiplier *= 1.25f; other.exposedHits--; }
        int variance = random.nextInt(5) - 2;
        boolean critical = random.nextInt(100) < attacker.criticalChance();
        float criticalMultiplier = critical ? 1.6f : 1f;
        int rawDamage = Math.max(1, Math.round((attacker.attack() + variance) * multiplier * criticalMultiplier));
        int damage = Math.max(1, rawDamage - (pierce ? 0 : defender.defense()));

        boolean wasGuarding = defender.isGuarding();
        if (wasGuarding) {
            damage = Math.max(1, (damage + 1) / 2);
            defender.setGuarding(false);
        }
        if (other.phaseHits > 0) { other.phaseHits--; damage = 0; }
        else if (other.deflectHits > 0) { other.deflectHits--; damage = Math.max(1, (damage + 1) / 2); }
        damage = absorb(defender, damage);
        defender.setHealth(Math.max(0, defender.health() - damage));

        String move = name != null ? name : skill ? attacker.skillName() : "attack";
        String guardNote = wasGuarding ? " (guarded)" : "";
        messages.add(attacker.displayName() + "'s " + move + " deals " + damage + " damage"
                + (critical ? " (critical)" : "") + guardNote + ".");
    }

    public static int skillManaCost() { return SKILL_MANA_COST; }

    public static int potionHealAmount() {
        return POTION_HEAL_AMOUNT;
    }
}
