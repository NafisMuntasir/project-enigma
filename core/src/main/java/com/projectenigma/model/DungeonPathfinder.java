package com.projectenigma.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Shortest four-direction path, excluding the start and including the destination. */
public final class DungeonPathfinder {
    private DungeonPathfinder() {}
    public static List<GridPoint> find(DungeonMap map, GridPoint start, GridPoint goal,
                                       Predicate<GridPoint> allowed) {
        if (!map.isWalkable(start.x(), start.y()) || !map.isWalkable(goal.x(), goal.y())
                || !allowed.test(goal) || start.equals(goal)) return List.of();
        GridPoint[][] parent = new GridPoint[map.width()][map.height()];
        ArrayDeque<GridPoint> queue = new ArrayDeque<>();
        queue.add(start); parent[start.x()][start.y()] = start;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            GridPoint p = queue.remove();
            if (p.equals(goal)) {
                List<GridPoint> path = new ArrayList<>();
                while (!p.equals(start)) { path.add(p); p = parent[p.x()][p.y()]; }
                Collections.reverse(path); return path;
            }
            for (int i = 0; i < 4; i++) {
                GridPoint next = new GridPoint(p.x() + dx[i], p.y() + dy[i]);
                if (map.isWalkable(next.x(), next.y()) && parent[next.x()][next.y()] == null && allowed.test(next)) {
                    parent[next.x()][next.y()] = p; queue.add(next);
                }
            }
        }
        return List.of();
    }
}
