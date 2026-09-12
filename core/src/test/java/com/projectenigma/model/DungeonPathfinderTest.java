package com.projectenigma.model;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPathfinderTest {
    private DungeonMap map() {
        DungeonMap map = new DungeonMap(15, 15);
        for (int x = 1; x < 14; x++) for (int y = 1; y < 14; y++) {
            if (x != 7 || y == 10) map.carve(x, y);
        }
        return map;
    }
    @Test void routesAroundWallsUsingShortestFourDirectionPath() {
        DungeonMap map = map(); GridPoint start = new GridPoint(5, 5), end = new GridPoint(9, 5);
        List<GridPoint> path = DungeonPathfinder.find(map, start, end, p -> true);
        assertEquals(map.shortestPathLength(start, end), path.size());
        assertEquals(end, path.get(path.size() - 1));
        GridPoint previous = start;
        for (GridPoint p : path) { assertEquals(1, previous.manhattanDistance(p)); assertTrue(map.isWalkable(p.x(), p.y())); previous = p; }
    }
    @Test void cannotRouteThroughUnexploredPassage() {
        assertTrue(DungeonPathfinder.find(map(), new GridPoint(5, 5), new GridPoint(9, 5), p -> p.y() < 10).isEmpty());
    }
    @Test void avoidsEnemiesUnlessExplicitDestinationIsAllowed() {
        DungeonMap map = map(); GridPoint enemy = new GridPoint(7, 10);
        Set<GridPoint> enemies = new HashSet<>(Set.of(enemy));
        assertTrue(DungeonPathfinder.find(map, new GridPoint(5, 5), new GridPoint(9, 5), p -> !enemies.contains(p)).isEmpty());
        assertFalse(DungeonPathfinder.find(map, new GridPoint(5, 5), enemy, p -> !enemies.contains(p) || p.equals(enemy)).isEmpty());
    }
    @Test void rejectsWallsOutsideMapAndUnknownDestination() {
        DungeonMap map = map(); GridPoint start = new GridPoint(5, 5);
        for (GridPoint end : List.of(new GridPoint(-1, 0), new GridPoint(7, 5), new GridPoint(15, 15))) {
            assertTrue(DungeonPathfinder.find(map, start, end, p -> true).isEmpty());
        }
        assertTrue(DungeonPathfinder.find(map, start, new GridPoint(6, 5), p -> false).isEmpty());
        assertTrue(DungeonPathfinder.find(map, start, start, p -> true).isEmpty());
    }
}
