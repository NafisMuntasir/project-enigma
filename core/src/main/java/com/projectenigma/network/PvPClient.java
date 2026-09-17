package com.projectenigma.network;

import com.projectenigma.model.BattleAction;
import com.projectenigma.model.HeroClass;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Guest-side network wrapper. Owns a small background scheduler that
 * retries the connection every three seconds whenever it is not currently
 * connected -- used both for the player's initial "Join" attempt (retried
 * immediately, in case the host hasn't finished binding yet) and for
 * automatic reconnection after a drop.
 *
 * <p>Same transport and threading approach as {@link PvPServer} -- see
 * that class's Javadoc for the queue + {@code postRunnable} pattern, and
 * DESIGN.md for why this uses plain sockets instead of a third-party
 * networking library. Public API is unchanged from an earlier
 * KryoNet-based version of this class.
 */
public final class PvPClient implements AutoCloseable {

    public interface EventListener {
        void onConnected(boolean isReconnect);
        void onDisconnected();
        void onStateReceived(PvPBattleState state);

        /** Race-to-PvP only. Default no-op -- see {@code PvPServer.EventListener.onReadyForPvp}. */
        default void onRaceStart(RaceStartPacket packet) { }

        /** Race-to-PvP only. Default no-op -- see {@code PvPServer.EventListener.onReadyForPvp}. */
        default void onRaceTimerSync(RaceTimerSyncPacket packet) { }
    }

    private static final int RECONNECT_INTERVAL_SECONDS = 3;
    // Deliberately well under any caller's own wait/assert timeout (e.g. the
    // 5s poll in PvPEndToEndTest) so a stuck attempt fails, logs, and the
    // 3s retry gets another shot inside that window -- instead of the first
    // attempt still being in flight when the caller's own timeout fires.
    private static final int CONNECT_TIMEOUT_MILLIS = 1500;

    private static final EventListener NO_OP = new EventListener() {
        @Override public void onConnected(boolean isReconnect) { }
        @Override public void onDisconnected() { }
        @Override public void onStateReceived(PvPBattleState state) { }
    };

    private final ConcurrentLinkedQueue<Runnable> incoming = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pvp-client-reconnect");
        thread.setDaemon(true);
        return thread;
    });

    private volatile PvPConnection connection;
    private volatile EventListener listener = NO_OP;
    private volatile String hostAddress;
    private volatile int hostPort;
    private volatile boolean everConnected;
    private volatile boolean reconnectLoopActive;
    private ScheduledFuture<?> reconnectTask;

    public PvPClient() {
        // Nothing to start eagerly -- connect() kicks off the reconnect loop,
        // which makes the actual first attempt.
    }

    /** Begins connecting (and, if needed, retrying) toward {@code host:port}. Non-blocking. */
    public void connect(String host, int port) {
        this.hostAddress = host;
        this.hostPort = port;
        MainThreadGateway.log("PvPClient", "connect() called for " + host + ":" + port);
        startReconnectLoop(0);
    }

    /** Call when the player presses Esc while waiting to reconnect. */
    public void cancelPendingConnection() {
        stopReconnectLoop();
    }

    public boolean isConnected() {
        PvPConnection current = connection;
        return current != null && current.isOpen();
    }

    public void sendClassSelection(HeroClass heroClass) {
        send(new PvPClassSelectPacket(heroClass));
    }

    public void sendAction(BattleAction action) {
        send(new PvPActionPacket(action));
    }

    public void sendSkill(com.projectenigma.model.Skill skill) { send(new PvPSkillPacket(skill)); }

    public void sendAbandon() {
        if (isConnected()) {
            send(new PvPAbandonPacket("Player abandoned the match."));
        }
    }

    /** Race-to-PvP only: sent once, when this client's own exploration timer ends. */
    public void sendReadyForPvp(HeroLoadout loadout) {
        send(new ReadyForPvPPacket(loadout));
    }

    private void send(Object payload) {
        PvPConnection current = connection;
        if (current == null) {
            return;
        }
        try {
            current.send(payload);
        } catch (IOException exception) {
            MainThreadGateway.log("PvPClient", "Send failed: " + exception.getMessage());
        }
    }

    public void setListener(EventListener listener) {
        this.listener = listener == null ? NO_OP : listener;
    }

    /** Runs any queued network events on the calling (render) thread. Safe to call every frame. */
    public void drainIncoming() {
        Runnable task;
        while ((task = incoming.poll()) != null) {
            task.run();
        }
    }

    // startReconnectLoop/stopReconnectLoop touch reconnectLoopActive and
    // reconnectTask from three different threads (render thread via
    // connect()/cancelPendingConnection(), this class's own reconnect-
    // executor thread via a successful attemptConnect(), and PvPConnection's
    // reader thread via onClosed()) -- synchronized to make the
    // check-then-act on reconnectLoopActive atomic and reconnectTask visible.
    private synchronized void startReconnectLoop(long initialDelaySeconds) {
        if (reconnectLoopActive) {
            return;
        }
        reconnectLoopActive = true;
        MainThreadGateway.log("PvPClient", "scheduling attemptConnect(), initialDelaySeconds=" + initialDelaySeconds);
        reconnectTask = reconnectExecutor.scheduleWithFixedDelay(this::attemptConnect,
                initialDelaySeconds, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void stopReconnectLoop() {
        reconnectLoopActive = false;
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    private void attemptConnect() {
        MainThreadGateway.log("PvPClient", "attemptConnect() starting, thread=" + Thread.currentThread().getName());
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(hostAddress, hostPort), CONNECT_TIMEOUT_MILLIS);
            MainThreadGateway.log("PvPClient", "raw socket connected, wrapping in PvPConnection");
            boolean isReconnect = everConnected;
            everConnected = true;
            connection = new PvPConnection(socket, new PvPConnection.Listener() {
                @Override
                public void onReceived(Object payload) {
                    if (payload instanceof PvPBattleState state) {
                        enqueue(() -> listener.onStateReceived(state));
                    } else if (payload instanceof RaceStartPacket packet) {
                        enqueue(() -> listener.onRaceStart(packet));
                    } else if (payload instanceof RaceTimerSyncPacket packet) {
                        enqueue(() -> listener.onRaceTimerSync(packet));
                    }
                    // Anything else (a stray/unexpected type) is silently ignored.
                }

                @Override
                public void onClosed() {
                    connection = null;
                    enqueue(() -> listener.onDisconnected());
                    startReconnectLoop(RECONNECT_INTERVAL_SECONDS);
                }
            });
            MainThreadGateway.log("PvPClient", "PvPConnection established, isReconnect=" + isReconnect);
            stopReconnectLoop();
            enqueue(() -> listener.onConnected(isReconnect));
        } catch (IOException exception) {
            MainThreadGateway.log("PvPClient", "Connect attempt to " + hostAddress + ":" + hostPort
                    + " failed: " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
        } catch (RuntimeException exception) {
            MainThreadGateway.log("PvPClient", "attemptConnect() threw unexpectedly: "
                    + exception.getClass().getName() + " - " + exception.getMessage());
            throw exception;
        }
    }

    private void enqueue(Runnable task) {
        incoming.add(task);
        MainThreadGateway.post(this::drainIncoming);
    }

    @Override
    public void close() {
        stopReconnectLoop();
        reconnectExecutor.shutdownNow();
        PvPConnection current = connection;
        if (current != null) {
            current.close();
        }
    }
}
