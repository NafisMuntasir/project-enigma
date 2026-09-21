package com.projectenigma.model;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyMovementTest {
    private GameSession arena() throws Exception {
        GameSession session = new GameSession();
        DungeonMap map = new DungeonMap(31, 21);
        for (int x = 1; x < 30; x++) for (int y = 1; y < 20; y++) map.carve(x,y);
        map.setStart(new GridPoint(1,1)); map.setExit(new GridPoint(29,19));
        var field = GameSession.class.getDeclaredField("dungeon"); field.setAccessible(true); field.set(session, map);
        session.playerX=25; session.playerY=15;
        return session;
    }
    private DungeonEnemy enemy(GameSession session, int x, int y) {
        DungeonEnemy enemy = new DungeonEnemy(1, EnemyType.BONE_SENTINEL, x,y,1);
        session.enemies.add(enemy); return enemy;
    }
    @Test void patrolMovesButStaysInSmallHomeAreaAndUsesWalkableTiles() throws Exception {
        GameSession s=arena(); DungeonEnemy e=enemy(s,5,5); EnemyMovement ai=new EnemyMovement(s);
        boolean moved=false;
        for(int i=0;i<500;i++) {
            assertNull(ai.update(.1f)); moved |= e.x!=5 || e.y!=5;
            assertTrue(Math.abs(e.x-5)+Math.abs(e.y-5)<=EnemyMovement.PATROL_RADIUS);
            assertTrue(s.dungeon().isWalkable(e.x,e.y));
        }
        assertTrue(moved);
    }
    @Test void proximityChasesAndReportsOneContactWithoutOverlappingPlayer() throws Exception {
        GameSession s=arena(); DungeonEnemy e=enemy(s,5,5); s.playerX=9;s.playerY=5;
        EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<10;i++) assertNull(ai.update(.1f)); // Entry / escape grace.
        DungeonEnemy caught=null;
        for(int i=0;i<100 && caught==null;i++) caught=ai.update(.1f);
        assertSame(e,caught); assertEquals(EnemyMovement.Mode.CHASE,ai.mode(e));
        assertEquals(1, Math.abs(e.x-s.playerX)+Math.abs(e.y-s.playerY));
    }
    @Test void wallsBlockDetectionAndEnemyCannotWalkThroughThem() throws Exception {
        GameSession s=arena(); DungeonMap map=new DungeonMap(31,21);
        for(int x=1;x<30;x++) for(int y=1;y<20;y++) if(x!=7) map.carve(x,y);
        map.setExit(new GridPoint(29,19));
        var f=GameSession.class.getDeclaredField("dungeon");f.setAccessible(true);f.set(s,map);
        DungeonEnemy e=enemy(s,5,5);s.playerX=8;s.playerY=5;EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<200;i++) { assertNull(ai.update(.1f));assertEquals(EnemyMovement.Mode.PATROL,ai.mode(e));assertTrue(e.x<7); }
    }
    @Test void enemiesAvoidEachOtherAndClosedChests() throws Exception {
        GameSession s=arena();DungeonEnemy a=enemy(s,5,5),b=enemy(s,6,5);b.id=2;
        s.chests.add(new DungeonChest(5,6));EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<300;i++) {
            ai.update(.1f);
            assertFalse(a.x==b.x && a.y==b.y);
            assertFalse(a.x==5 && a.y==6);assertFalse(b.x==5 && b.y==6);
        }
    }
    @Test void chaseRepathsAroundAWallAfterPlayerMovesBehindIt() throws Exception {
        GameSession s=arena();DungeonMap map=new DungeonMap(31,21);
        for(int x=1;x<30;x++) for(int y=1;y<20;y++) if(x!=6 || y<4 || y>7) map.carve(x,y);
        map.setExit(new GridPoint(29,19));
        var f=GameSession.class.getDeclaredField("dungeon");f.setAccessible(true);f.set(s,map);
        DungeonEnemy e=enemy(s,4,5);s.playerX=4;s.playerY=8;EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<18;i++) ai.update(.1f);
        assertEquals(EnemyMovement.Mode.CHASE,ai.mode(e));
        s.playerX=8;s.playerY=6;
        DungeonEnemy caught=null;
        for(int i=0;i<100 && caught==null;i++) {
            int x=e.x,y=e.y;caught=ai.update(.1f);
            assertTrue(map.isWalkable(e.x,e.y));assertTrue(Math.abs(x-e.x)+Math.abs(y-e.y)<=1);
        }
        assertSame(e,caught);
    }
    @Test void breakingLeashReturnsEnemyHomeInsteadOfFollowingForever() throws Exception {
        GameSession s=arena();DungeonEnemy e=enemy(s,5,5);s.playerX=9;s.playerY=5;
        EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<20;i++) ai.update(.1f);
        assertEquals(EnemyMovement.Mode.CHASE,ai.mode(e));
        s.playerX=25;s.playerY=15;
        for(int i=0;i<150;i++) assertNull(ai.update(.1f));
        assertEquals(EnemyMovement.Mode.PATROL,ai.mode(e));
        assertTrue(Math.abs(e.x-e.homeX)+Math.abs(e.y-e.homeY)<=EnemyMovement.PATROL_RADIUS);
    }
    @Test void saveKeepsPatrolOriginAndLegacyEnemiesReceiveAnOrigin() throws Exception {
        GameSession s=arena();DungeonEnemy e=enemy(s,5,5);e.x=6;
        Json json=new Json(); DungeonEnemy loaded=json.fromJson(DungeonEnemy.class,json.toJson(e));
        assertEquals(5,loaded.homeX);assertEquals(6,loaded.x);
        loaded.homeInitialized=false;s.enemies.clear();s.enemies.add(loaded);
        new EnemyMovement(s);assertEquals(6,loaded.homeX);assertTrue(loaded.homeInitialized);
    }
    @Test void deadEnemiesNeverMoveOrStartBattle() throws Exception {
        GameSession s=arena();DungeonEnemy e=enemy(s,5,5);e.health=0;s.playerX=6;s.playerY=5;
        EnemyMovement ai=new EnemyMovement(s);
        for(int i=0;i<100;i++) assertNull(ai.update(.1f));
        assertEquals(5,e.x);assertEquals(5,e.y);
    }
}
