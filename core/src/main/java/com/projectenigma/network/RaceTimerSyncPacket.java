package com.projectenigma.network;

import java.io.Serializable;

/**
 * Sent periodically by the host while a Race-to-PvP exploration phase is
 * running, both to correct clock drift on the guest's own locally-ticked
 * display countdown, and -- once {@code secondsRemaining <= 0} -- to serve
 * as the single authoritative "exploration is over" signal.
 *
 * <p>The guest ticks its own displayed countdown between syncs purely for
 * a smooth, responsive UI, but it never ends its own exploration phase
 * because its <em>local</em> countdown reached zero. Only receiving this
 * packet with {@code secondsRemaining <= 0} does that -- the host's clock,
 * not the client's, is what decides when exploration ends (see
 * DESIGN.md's Race-to-PvP notes).
 */
public record RaceTimerSyncPacket(int secondsRemaining) implements Serializable {
    private static final long serialVersionUID = 1L;
}
