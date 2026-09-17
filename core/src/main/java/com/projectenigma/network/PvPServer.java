package com.projectenigma.network;

import com.projectenigma.model.BattleAction;
import com.projectenigma.model.HeroClass;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Host-side network wrapper. Owns a plain {@link ServerSocket}, accepts
 * exactly one guest connection at a time (Phase 1), and turns raw
 * connection events into {@link EventListener} callbacks that always run
 * on the libGDX main (render) thread.
 *
 * <p>Uses {@link PvPConnection} (plain {@code Socket} +
 * {@code ObjectOutputStream}/{@code ObjectInputStream}) rather than a
 * third-party networking library -- see DESIGN.md's networking notes for
 * why. The public API here is intentionally unchanged from an earlier
 * KryoNet-based version of this class, so nothing outside the {@code
 * network} package needed to change when the transport did.
 *
 * <h2>Threading</h2>
 * The accept loop runs on its own daemon thread; each accepted connection
 * gets its own daemon reader thread (both inside {@link PvPConnection}).
 * Every callback below does exactly two things, in order:
 * <ol>
 *   <li>Package the event as a {@code Runnable} and add it to a thread-safe
 *       {@link ConcurrentLinkedQueue} -- no libGDX or game-state calls happen
 *       on a network thread itself.</li>
 *   <li>Call {@code MainThreadGateway.post(this::drainIncoming)} to schedule
 *       the drain on the render thread very soon (before the next frame).</li>
 * </ol>
 * {@link #drainIncoming()} then runs entirely on the render thread and is
 * the only place {@link EventListener} methods are ever invoked, so
 * whatever a screen does inside them (mutate a {@code PvPMatch}, switch
 * screens, touch Scene2D/GL state) is always safe. Screens additionally
 * call {@link #drainIncoming()} once at the top of their own {@code
 * render(delta)} as a cheap, idempotent fallback.
 */
public final class PvPServer implements AutoCloseable {

    /** Forwarded on the render thread only -- see class Javadoc. */
    public interface EventListener {
        void onGuestConnected(boolean isReconnect);
        void onGuestDisconnected();
        void onClassSelected(HeroClass guestClass);
        void onActionReceived(BattleAction action);
        default void onSkillReceived(com.projectenigma.model.Skill skill) { }
        void onAbandon();

        /**
         * Race-to-PvP only: the guest's exploration-phase hero, sent once
         * its exploration timer ends. Default no-op so existing listeners
         * (e.g. classic PvP's lobby listener, {@code PvPCombatScreen})
         * that have nothing to do with Race mode don't need to implement
         * it.
         */
        default void onReadyForPvp(HeroLoadout loadout) { }
    }

    private static final EventListener NO_OP = new EventListener() {
        @Override public void onGuestConnected(boolean isReconnect) { }
        @Override public void onGuestDisconnected() { }
        @Override public void onClassSelected(HeroClass guestClass) { }
        @Override public void onActionReceived(BattleAction action) { }
        @Override public void onAbandon() { }
    };

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final ConcurrentLinkedQueue<Runnable> incoming = new ConcurrentLinkedQueue<>();
    private volatile PvPConnection guestConnection;
    private volatile boolean everConnected;
    private volatile boolean closing;
    private volatile EventListener listener = NO_OP;

    public PvPServer(int tcpPort) throws IOException {
        serverSocket = new ServerSocket(tcpPort);
        acceptThread = new Thread(this::acceptLoop, "pvp-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        MainThreadGateway.log("PvPServer", "Listening on TCP " + serverSocket.getLocalPort());
    }

    /** The actual bound port -- same as the constructor argument unless it was 0 (bind to any free port). */
    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!closing) {
            try {
                Socket socket = serverSocket.accept();
                MainThreadGateway.log("PvPServer", "accepted raw connection from " + socket.getRemoteSocketAddress());
                acceptGuest(socket);
            } catch (IOException exception) {
                // serverSocket.close() (see close()) also unblocks accept()
                // with an IOException; only worth logging if this wasn't
                // an intentional shutdown.
                if (!closing) {
                    MainThreadGateway.log("PvPServer", "Accept failed: " + exception.getMessage());
                }
            }
        }
    }

    private void acceptGuest(Socket socket) {
        // Phase 1 supports exactly one guest. A second connection attempt
        // while one is already open is rejected outright rather than
        // silently replacing the first -- see DESIGN.md, "Known gaps".
        if (guestConnection != null && guestConnection.isOpen()) {
            closeQuietly(socket);
            return;
        }
        boolean isReconnect = everConnected;
        everConnected = true;
        try {
            guestConnection = new PvPConnection(socket, new PvPConnection.Listener() {
                @Override
                public void onReceived(Object payload) {
                    dispatchIncoming(payload);
                }

                @Override
                public void onClosed() {
                    guestConnection = null;
                    enqueue(() -> listener.onGuestDisconnected());
                }
            });
            enqueue(() -> listener.onGuestConnected(isReconnect));
        } catch (IOException exception) {
            MainThreadGateway.log("PvPServer", "Failed to establish guest connection: " + exception.getMessage());
            closeQuietly(socket);
        }
    }

    private void dispatchIncoming(Object payload) {
        if (payload instanceof PvPClassSelectPacket packet) {
            enqueue(() -> listener.onClassSelected(packet.heroClass()));
        } else if (payload instanceof PvPActionPacket packet) {
            enqueue(() -> listener.onActionReceived(packet.action()));
        } else if (payload instanceof PvPSkillPacket packet) {
            enqueue(() -> listener.onSkillReceived(packet.skill()));
        } else if (payload instanceof PvPAbandonPacket) {
            enqueue(() -> listener.onAbandon());
        } else if (payload instanceof ReadyForPvPPacket packet) {
            enqueue(() -> listener.onReadyForPvp(packet.loadout()));
        }
        // Anything else (a stray/unexpected type) is silently ignored.
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

    public boolean hasGuest() {
        PvPConnection connection = guestConnection;
        return connection != null && connection.isOpen();
    }

    public void broadcast(PvPBattleState state) {
        sendToGuest(state);
    }

    /**
     * Generic one-shot send to the current guest, used both by {@link
     * #broadcast} and by the Race-to-PvP lifecycle packets ({@link
     * RaceStartPacket}, {@link RaceTimerSyncPacket}) which have no
     * per-turn shape of their own. Silently does nothing if there is no
     * live guest connection -- callers here are always fire-and-forget
     * status updates, not commands that need a delivery guarantee.
     */
    public void sendToGuest(Object payload) {
        PvPConnection connection = guestConnection;
        if (connection == null) {
            return;
        }
        try {
            connection.send(payload);
        } catch (IOException exception) {
            MainThreadGateway.log("PvPServer", "Send failed: " + exception.getMessage());
        }
    }

    private void enqueue(Runnable task) {
        incoming.add(task);
        MainThreadGateway.post(this::drainIncoming);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing meaningful to do with a failure to close an already-rejected socket.
        }
    }

    @Override
    public void close() {
        closing = true;
        try {
            serverSocket.close(); // unblocks acceptLoop()'s blocking accept() call
        } catch (IOException ignored) {
            // Already closed or closing.
        }
        PvPConnection connection = guestConnection;
        if (connection != null) {
            connection.close();
        }
        MainThreadGateway.log("PvPServer", "Server stopped.");
    }
}
