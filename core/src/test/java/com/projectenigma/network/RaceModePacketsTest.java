package com.projectenigma.network;

import com.projectenigma.model.BattleAction;
import com.projectenigma.model.HeroClass;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the Race-to-PvP wire protocol ({@link RaceStartPacket}, {@link
 * RaceTimerSyncPacket}, {@link ReadyForPvPPacket}) over real localhost
 * sockets, the same way {@link PvPConnectionTest} pins down the classic PvP
 * packets -- this is the layer that would silently swallow a new packet
 * type if {@code PvPServer}/{@code PvPClient}'s dispatch (the {@code
 * instanceof} chain in each class's connection listener) weren't updated
 * alongside a new record type.
 */
class RaceModePacketsTest {

    @Test
    void hostCanSendRaceStartAndTimerSyncAndGuestCanSendReadyForPvp() throws Exception {
        try (PvPServer server = new PvPServer(0)) {
            int port = server.port();

            BlockingQueue<RaceStartPacket> clientRaceStart = new ArrayBlockingQueue<>(1);
            BlockingQueue<RaceTimerSyncPacket> clientTimerSync = new ArrayBlockingQueue<>(5);
            BlockingQueue<HeroLoadout> serverReadyLoadout = new ArrayBlockingQueue<>(1);
            BlockingQueue<Boolean> clientConnected = new ArrayBlockingQueue<>(1);

            server.setListener(new PvPServer.EventListener() {
                @Override public void onGuestConnected(boolean isReconnect) { }
                @Override public void onGuestDisconnected() { }
                @Override public void onClassSelected(HeroClass guestClass) { }
                @Override public void onActionReceived(BattleAction action) { }
                @Override public void onAbandon() { }
                @Override public void onReadyForPvp(HeroLoadout loadout) { serverReadyLoadout.add(loadout); }
            });

            PvPClient client = new PvPClient();
            client.setListener(new PvPClient.EventListener() {
                @Override public void onConnected(boolean isReconnect) { clientConnected.add(true); }
                @Override public void onDisconnected() { }
                @Override public void onStateReceived(PvPBattleState state) { }
                @Override public void onRaceStart(RaceStartPacket packet) { clientRaceStart.add(packet); }
                @Override public void onRaceTimerSync(RaceTimerSyncPacket packet) { clientTimerSync.add(packet); }
            });

            try {
                client.connect("127.0.0.1", port);
                assertNotNull(poll(clientConnected), "client should connect");

                server.sendToGuest(new RaceStartPacket(424242L, 180));
                RaceStartPacket start = poll(clientRaceStart);
                assertEquals(424242L, start.dungeonSeed());
                assertEquals(180, start.durationSeconds());

                server.sendToGuest(new RaceTimerSyncPacket(90));
                assertEquals(90, poll(clientTimerSync).secondsRemaining());
                server.sendToGuest(new RaceTimerSyncPacket(0));
                assertEquals(0, poll(clientTimerSync).secondsRemaining(),
                        "a secondsRemaining of exactly 0 is the authoritative end-of-exploration signal -- it must round-trip exactly");

                HeroLoadout loadout = new HeroLoadout(HeroClass.MAGE, 4, 96, 70, 20, 14, 19, 6, 1);
                client.sendReadyForPvp(loadout);
                HeroLoadout received = poll(serverReadyLoadout);
                assertEquals(loadout, received, "HeroLoadout record equality is component-wise, so this pins down a full, exact round trip");
                assertEquals(HeroClass.MAGE, received.heroClass(), "nested HeroClass enum must survive serialization");
            } finally {
                client.close();
            }
        }
    }

    @Test
    void classicPvpListenersAreUnaffectedByTheNewDefaultMethods() throws Exception {
        // A listener that only implements the original (pre-Race-mode) abstract
        // methods must still compile and behave correctly -- onReadyForPvp/
        // onRaceStart/onRaceTimerSync are default no-ops precisely so that
        // PvPCombatScreen and the classic-PvP lobby listener in
        // ProjectEnigmaGame never had to change.
        try (PvPServer server = new PvPServer(0)) {
            PvPServer.EventListener minimalListener = new PvPServer.EventListener() {
                @Override public void onGuestConnected(boolean isReconnect) { }
                @Override public void onGuestDisconnected() { }
                @Override public void onClassSelected(HeroClass guestClass) { }
                @Override public void onActionReceived(BattleAction action) { }
                @Override public void onAbandon() { }
            };
            server.setListener(minimalListener);
            assertDoesNotThrow(() -> minimalListener.onReadyForPvp(
                    new HeroLoadout(HeroClass.WARRIOR, 1, 120, 120, 8, 8, 16, 7, 3)));
        }
    }

    private static <T> T poll(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(3, TimeUnit.SECONDS);
        assertNotNull(value, "timed out waiting for a value from the connection");
        return value;
    }
}
