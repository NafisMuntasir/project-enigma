package com.projectenigma.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Saved run progression; combat-only timers live in BattleEngine instead. */
public final class Progression {
    public int pendingChoices;
    public int choicesMade;
    public ArrayList<String> unlocked = new ArrayList<>();
    public ArrayList<String> equipped = new ArrayList<>(Arrays.asList("", "", ""));
    public int[] passiveRanks = new int[PassiveUpgrade.values().length];
    public int restorationSteps, vectorCooldownSteps, vectorSteps;

    public record Choice(String id, String title, String description, Skill skill, PassiveUpgrade passive) { }

    public void normalize() {
        if (unlocked == null) unlocked = new ArrayList<>();
        unlocked.removeIf(id -> skill(id) == null);
        unlocked = new ArrayList<>(new java.util.LinkedHashSet<>(unlocked));
        if (equipped == null) equipped = new ArrayList<>();
        while (equipped.size() < 3) equipped.add("");
        while (equipped.size() > 3) equipped.remove(equipped.size() - 1);
        var seen = new java.util.HashSet<String>();
        for (int i = 0; i < 3; i++) {
            String id = equipped.get(i);
            if (!unlocked.contains(id) || !seen.add(id)) equipped.set(i, "");
        }
        if (passiveRanks == null) passiveRanks = new int[PassiveUpgrade.values().length];
        passiveRanks = Arrays.copyOf(passiveRanks, PassiveUpgrade.values().length);
        for (PassiveUpgrade p : PassiveUpgrade.values()) passiveRanks[p.ordinal()] = Math.max(0, Math.min(p.maxRank, passiveRanks[p.ordinal()]));
        pendingChoices = Math.max(0, pendingChoices); choicesMade = Math.max(0, choicesMade);
        restorationSteps = Math.max(0, restorationSteps); vectorCooldownSteps = Math.max(0, vectorCooldownSteps); vectorSteps = Math.max(0, vectorSteps);
    }
    public static Skill skill(String id) {
        try { return id == null ? null : Skill.valueOf(id); } catch (IllegalArgumentException e) { return null; }
    }
    public int rank(PassiveUpgrade p) { return passiveRanks[p.ordinal()]; }
    public boolean unlocked(Skill skill) { return skill != null && unlocked.contains(skill.name()); }
    public boolean equipped(Skill skill) { return unlocked(skill) && equipped.contains(skill.name()); }
    public boolean equip(Skill skill, int slot) {
        if (slot < 0 || slot >= 3 || !unlocked(skill)) return false;
        for (int i = 0; i < 3; i++) if (skill.name().equals(equipped.get(i))) equipped.set(i, "");
        equipped.set(slot, skill.name()); return true;
    }
    public List<Choice> choices(Hero hero) {
        List<Choice> result = new ArrayList<>();
        for (PassiveUpgrade p : PassiveUpgrade.values()) if (rank(p) < p.maxRank)
            result.add(new Choice("PASSIVE:" + p.name(), p.title, p.description, null, p));
        // The oldest outstanding level determines eligibility, even after a multi-level XP reward.
        int choiceLevel = Math.max(2, hero.level - pendingChoices + 1);
        for (Skill s : Skill.values()) if (!unlocked(s) && s.level <= choiceLevel)
            result.add(new Choice("SKILL:" + s.name(), s.title, s.description, s, null));
        if (result.isEmpty()) result.add(new Choice("RESERVE", "Reserve Credits", "All augmentations are complete. Receive 25 credits.", null, null));
        // Show both active unlocks and passive choices on the first page.
        List<Choice> passive = result.stream().filter(c -> c.skill == null).toList();
        List<Choice> active = result.stream().filter(c -> c.skill != null).toList();
        List<Choice> mixed = new ArrayList<>();
        for (int i = 0; i < Math.max(passive.size(), active.size()); i++) {
            if (i < active.size()) mixed.add(active.get(i));
            if (i < passive.size()) mixed.add(passive.get(i));
        }
        return mixed;
    }
    public boolean choose(Hero hero, String id) {
        if (pendingChoices <= 0) return false;
        Choice choice = choices(hero).stream().filter(c -> c.id.equals(id)).findFirst().orElse(null);
        if (choice == null) return false;
        if (choice.skill != null) {
            unlocked.add(choice.skill.name());
            int slot = equipped.indexOf("");
            if (slot >= 0) equipped.set(slot, choice.skill.name());
        } else if (choice.passive != null) {
            passiveRanks[choice.passive.ordinal()]++;
            choice.passive.apply(hero);
        } else hero.gold += 25;
        pendingChoices--; choicesMade++; return true;
    }
    public int cooldown(Skill skill) { return Math.max(1, skill.cooldown - rank(PassiveUpgrade.COOLDOWN)); }
    public float movementMultiplier() { return 1f + rank(PassiveUpgrade.MOVEMENT) * .08f + (vectorSteps > 0 ? .4f : 0); }
    public void moved() {
        restorationSteps = Math.max(0, restorationSteps - 1);
        vectorCooldownSteps = Math.max(0, vectorCooldownSteps - 1);
        vectorSteps = Math.max(0, vectorSteps - 1);
    }
    public String fieldReason(Hero hero, Skill skill) {
        if (!unlocked(skill)) return "Unlock this skill when you level up.";
        if (!equipped(skill)) return "Assign this skill to a loadout slot first.";
        if (!skill.fieldUsable()) return "Combat skill: use during an encounter.";
        if (hero.mana < skill.cost) return "Not enough energy.";
        int remaining = skill == Skill.VECTOR_BOOST ? vectorCooldownSteps : restorationSteps;
        if (remaining > 0) return "Recovery: " + remaining + " tile moves remaining.";
        if (skill == Skill.NANITE_RESTORATION && hero.health >= hero.maxHealth) return "Health is already full.";
        return "";
    }
    public boolean useField(Hero hero, Skill skill) {
        if (!fieldReason(hero, skill).isEmpty()) return false;
        hero.setMana(hero.mana - skill.cost);
        if (skill == Skill.NANITE_RESTORATION) { hero.heal(24 + 2 * hero.level); restorationSteps = 12; }
        else { vectorSteps = 20; vectorCooldownSteps = 30; }
        return true;
    }
}
