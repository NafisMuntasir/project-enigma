package com.projectenigma.network;

import com.projectenigma.model.*;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host-computed eligibility lets guests explain disabled skills without predicting damage or turns. */
public record SkillSnapshot(ProgressionSnapshot progression, Map<String, String> reasons,
                            Map<String, Integer> cooldowns, String status) implements Serializable {
    private static final long serialVersionUID = 1L;
    public static SkillSnapshot of(Hero hero, Hero opponent, BattleEngine engine) {
        Map<String, String> reasons = new LinkedHashMap<>();
        Map<String, Integer> cooldowns = new LinkedHashMap<>();
        for (Skill skill : Skill.values()) {
            reasons.put(skill.name(), engine.skillReason(hero, opponent, skill));
            cooldowns.put(skill.name(), engine.effects(hero).cooldown(skill));
        }
        return new SkillSnapshot(ProgressionSnapshot.of(hero), Map.copyOf(reasons), Map.copyOf(cooldowns), engine.effects(hero).summary());
    }
}
