package com.projectenigma.model;

import java.util.IdentityHashMap;
import java.util.List;

/** Dungeon-only patrol/chase controller. It reports contact; screens own battle transitions. */
public final class EnemyMovement {
    public static final int PATROL_RADIUS = 2, DETECTION_RADIUS = 5, LEASH_RADIUS = 10;
    public static final float PATROL_STEP_SECONDS = .65f, CHASE_STEP_SECONDS = .18f;
    public static final float ENTRY_GRACE_SECONDS = 1.25f;
    public enum Mode { PATROL, CHASE, RETURN }
    private static final int[][] DIRECTIONS = {{1,0},{0,1},{-1,0},{0,-1}};
    private static final class Motion {
        Mode mode = Mode.PATROL;
        float wait, elapsed = 1, duration = 1, fromX, fromY;
        int dx, dy = -1, patrolStep;
        Motion(DungeonEnemy enemy) { fromX = enemy.x; fromY = enemy.y; wait = .2f + Math.floorMod(enemy.id, 5) * .1f; }
    }
    private final GameSession session;
    private final IdentityHashMap<DungeonEnemy, Motion> motions = new IdentityHashMap<>();
    private float grace = ENTRY_GRACE_SECONDS;
    public EnemyMovement(GameSession session) {
        this.session = session;
        for (DungeonEnemy enemy : session.enemies) {
            if (!enemy.homeInitialized || !session.dungeon().isWalkable(enemy.homeX, enemy.homeY)) {
                enemy.homeX = enemy.x; enemy.homeY = enemy.y; enemy.homeInitialized = true;
            }
        }
    }
    private Motion motion(DungeonEnemy enemy) { return motions.computeIfAbsent(enemy, Motion::new); }
    public Mode mode(DungeonEnemy enemy) { return motion(enemy).mode; }
    public int directionX(DungeonEnemy enemy) { return motion(enemy).dx; }
    public int directionY(DungeonEnemy enemy) { return motion(enemy).dy; }
    public float renderedX(DungeonEnemy enemy) {
        Motion m = motion(enemy); return m.fromX + (enemy.x - m.fromX) * Math.min(1, m.elapsed / m.duration);
    }
    public float renderedY(DungeonEnemy enemy) {
        Motion m = motion(enemy); return m.fromY + (enemy.y - m.fromY) * Math.min(1, m.elapsed / m.duration);
    }
    public DungeonEnemy update(float delta) {
        if (delta <= 0 || !session.hero.isAlive()) return null;
        delta = Math.min(delta, .1f); // Never teleport or catch up after a stalled frame.
        if (grace > 0) { grace -= delta; return null; }
        for (DungeonEnemy enemy : session.enemies) {
            if (!enemy.isAlive()) continue;
            Motion m = motion(enemy); m.elapsed += delta; m.wait -= delta;
            if (m.wait > 0) continue;
            int distance = Math.abs(enemy.x - session.playerX) + Math.abs(enemy.y - session.playerY);
            int homeDistance = Math.abs(enemy.x - enemy.homeX) + Math.abs(enemy.y - enemy.homeY);
            if (m.mode == Mode.CHASE && (distance > LEASH_RADIUS || homeDistance >= LEASH_RADIUS)) m.mode = Mode.RETURN;
            if (m.mode == Mode.RETURN && homeDistance == 0) m.mode = Mode.PATROL;
            if (m.mode == Mode.PATROL && distance <= DETECTION_RADIUS && canSeePlayer(enemy)) m.mode = Mode.CHASE;
            m.duration = m.mode == Mode.CHASE ? CHASE_STEP_SECONDS : PATROL_STEP_SECONDS;
            m.wait = m.duration;
            GridPoint next = null;
            if (m.mode == Mode.PATROL) {
                int offset = Math.floorMod(enemy.id + m.patrolStep++, 4);
                for (int i = 0; i < 4; i++) {
                    int[] direction = DIRECTIONS[(offset + i) % 4];
                    GridPoint candidate = new GridPoint(enemy.x + direction[0], enemy.y + direction[1]);
                    if (Math.abs(candidate.x() - enemy.homeX) + Math.abs(candidate.y() - enemy.homeY) <= PATROL_RADIUS
                            && available(enemy, candidate) && !isPlayer(candidate)) { next = candidate; break; }
                }
            } else {
                GridPoint goal = m.mode == Mode.CHASE ? new GridPoint(session.playerX, session.playerY) : new GridPoint(enemy.homeX, enemy.homeY);
                List<GridPoint> path = DungeonPathfinder.find(session.dungeon(), new GridPoint(enemy.x, enemy.y), goal,
                        p -> available(enemy, p) && (m.mode == Mode.CHASE || !isPlayer(p)));
                if (!path.isEmpty()) next = path.get(0);
            }
            if (next == null) continue;
            m.dx = next.x() - enemy.x; m.dy = next.y() - enemy.y;
            if (isPlayer(next)) return enemy;
            m.fromX = enemy.x; m.fromY = enemy.y; m.elapsed = 0;
            enemy.x = next.x(); enemy.y = next.y();
        }
        return null;
    }
    private boolean isPlayer(GridPoint point) { return point.x() == session.playerX && point.y() == session.playerY; }
    private boolean available(DungeonEnemy enemy, GridPoint point) {
        if (!session.dungeon().isWalkable(point.x(), point.y())) return false;
        DungeonEnemy occupant = session.enemyAt(point.x(), point.y());
        if (occupant != null && occupant != enemy) return false;
        // Contact is allowed even if the player is standing on stairs or a chest tile.
        if (isPlayer(point)) return true;
        DungeonChest chest = session.chestAt(point.x(), point.y());
        return (chest == null || chest.opened) && !point.equals(session.dungeon().exit());
    }
    private boolean canSeePlayer(DungeonEnemy enemy) {
        // Sample the centre-to-centre segment: walls block awareness even at short range.
        float dx = session.playerX - enemy.x, dy = session.playerY - enemy.y;
        int steps = Math.max(1, (int)(Math.max(Math.abs(dx), Math.abs(dy)) * 4));
        for (int i = 1; i <= steps; i++) {
            int x = (int)Math.floor(enemy.x + .5f + dx * i / steps);
            int y = (int)Math.floor(enemy.y + .5f + dy * i / steps);
            if (!session.dungeon().isWalkable(x, y)) return false;
        }
        return true;
    }
}
