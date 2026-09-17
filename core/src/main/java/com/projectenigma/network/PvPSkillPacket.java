package com.projectenigma.network;

import com.projectenigma.model.Skill;
import java.io.Serializable;

/** Skill request only; the host validates the loadout, turn, cost and cooldown. */
public record PvPSkillPacket(Skill skill) implements Serializable {
    private static final long serialVersionUID = 1L;
}
