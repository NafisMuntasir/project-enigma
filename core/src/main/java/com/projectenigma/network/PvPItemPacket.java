package com.projectenigma.network;

import java.io.Serializable;

/** Only an inventory ID is requested; amounts and ownership come from the host. */
public record PvPItemPacket(String itemId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
