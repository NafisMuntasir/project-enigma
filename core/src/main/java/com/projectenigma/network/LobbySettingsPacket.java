package com.projectenigma.network;

import java.io.Serializable;

/** The host alone chooses the mode and exploration duration. */
public record LobbySettingsPacket(boolean rush, int durationSeconds) implements Serializable {
    private static final long serialVersionUID = 1L;
}
