package com.projectenigma.network;

import java.io.Serializable;

/**
 * Sent once by the guest when its Race-to-PvP exploration timer ends (i.e.
 * on receiving a {@link RaceTimerSyncPacket} with {@code secondsRemaining
 * <= 0}). Carries the {@link HeroLoadout} the guest grew during its own
 * independent dungeon run. The host waits until it has both this packet
 * and its own exploration result before creating the {@code PvPMatch} --
 * see {@code ProjectEnigmaGame.tryBeginRaceMatch()}.
 */
public record ReadyForPvPPacket(HeroLoadout loadout) implements Serializable {
    private static final long serialVersionUID = 1L;
}
