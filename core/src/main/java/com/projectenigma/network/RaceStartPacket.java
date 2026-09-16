package com.projectenigma.network;

import java.io.Serializable;

/**
 * Sent once by the host to the guest immediately after both players have
 * picked a class for Race-to-PvP mode. Carries the shared dungeon seed --
 * so both players explore an identically-laid-out (but independently
 * mutable) dungeon -- and the exploration duration in whole seconds. The
 * host starts its own local exploration timer at the same instant it sends
 * this, and that local timer (not this packet) is what stays authoritative
 * for the actual end-of-exploration transition; see {@link RaceTimerSyncPacket}.
 */
public record RaceStartPacket(long dungeonSeed, int durationSeconds) implements Serializable {
    private static final long serialVersionUID = 1L;
}
