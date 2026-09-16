package com.projectenigma.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GameSession {
    public static final int DUNGEON_WIDTH = 61;
    public static final int DUNGEON_HEIGHT = 41;

    public long campaignSeed;
    public long floorSeed;
    public int floorNumber = 1;
    public Hero hero = new Hero();
    public int playerX;
    public int playerY;
    public int stepsTaken;
    public ArrayList<DungeonEnemy> enemies = new ArrayList<>();
    public ArrayList<DungeonChest> chests = new ArrayList<>();
    public ArrayList<DungeonPickup> pickups = new ArrayList<>();

    private transient DungeonMap dungeon;

    public GameSession() {
        // Required by libGDX Json.
    }

    public GameSession(long campaignSeed, HeroClass heroClass) {
        this.campaignSeed = campaignSeed;
        this.hero = new Hero(heroClass);
        // The single-player game uses the unified Items inventory.
        this.hero.migrateLegacyPotionsToItems();
        beginFloor(1);
    }

    public DungeonMap dungeon() {
        if (dungeon == null) {
            rebuildTransientState();
        }
        return dungeon;
    }

    public void beginNextFloor() {
        beginFloor(floorNumber + 1);
        hero.restoreMana(Math.max(2, hero.maxMana / 3));
        hero.heal(Math.max(6, hero.maxHealth / 10));
    }

    private void beginFloor(int targetFloor) {
        floorNumber = Math.max(1, targetFloor);
        floorSeed = mixSeed(campaignSeed, floorNumber);
        dungeon = new DungeonGenerator().generate(DUNGEON_WIDTH, DUNGEON_HEIGHT, floorSeed);
        playerX = dungeon.start().x();
        playerY = dungeon.start().y();
        enemies.clear();
        chests.clear();
        pickups.clear();
        populateFloor();
    }

    public void rebuildTransientState() {
        if (floorNumber < 1) {
            floorNumber = 1;
        }
        if (floorSeed == 0L) {
            floorSeed = mixSeed(campaignSeed, floorNumber);
        }
        dungeon = new DungeonGenerator().generate(DUNGEON_WIDTH, DUNGEON_HEIGHT, floorSeed);
        if (!dungeon.isWalkable(playerX, playerY)) {
            playerX = dungeon.start().x();
            playerY = dungeon.start().y();
        }
        if (enemies == null) {
            enemies = new ArrayList<>();
        }
        if (chests == null) {
            chests = new ArrayList<>();
        }
        if (pickups == null) {
            pickups = new ArrayList<>();
        }
        enemies.removeIf(enemy -> enemy == null || !dungeon.isWalkable(enemy.x, enemy.y));
        chests.removeIf(chest -> chest == null || !dungeon.isWalkable(chest.x, chest.y));
        pickups.removeIf(pickup -> pickup == null || pickup.item == null || !dungeon.isWalkable(pickup.x, pickup.y));
        // Old saves stored potions as a separate integer. Convert them once into Items.
        hero.migrateLegacyPotionsToItems();
    }

    private void populateFloor() {
        List<GridPoint> candidates = dungeon.walkableTiles();
        Random random = new Random(floorSeed ^ 0x6A09E667F3BCC909L);
        Collections.shuffle(candidates, random);
        Set<GridPoint> occupied = new HashSet<>();
        occupied.add(dungeon.start());
        occupied.add(dungeon.exit());

        int enemyTarget = Math.min(12, 4 + floorNumber);
        int enemyIndex = 0;
        for (GridPoint point : candidates) {
            if (enemyIndex >= enemyTarget) {
                break;
            }
            if (!validSpawn(point, occupied, 7, 3)) {
                continue;
            }
            EnemyType type = chooseEnemyType(random, enemyIndex);
            long id = mixSeed(floorSeed, enemyIndex + 1);
            enemies.add(new DungeonEnemy(id, type, point.x(), point.y(), floorNumber));
            occupied.add(point);
            enemyIndex++;
        }

        int chestTarget = Math.min(6, 2 + (floorNumber + 1) / 2);
        for (GridPoint point : candidates) {
            if (chests.size() >= chestTarget) {
                break;
            }
            if (validSpawn(point, occupied, 4, 2)) {
                chests.add(new DungeonChest(point.x(), point.y()));
                occupied.add(point);
            }
        }

        int pickupTarget = Math.min(7, 3 + floorNumber / 2);
        int pickupIndex = 0;
        for (GridPoint point : candidates) {
            if (pickups.size() >= pickupTarget) break;
            if (!validSpawn(point, occupied, 5, 2)) continue;
            pickups.add(new DungeonPickup(point.x(), point.y(), createPickupItem(random, pickupIndex)));
            occupied.add(point);
            pickupIndex++;
        }
    }

    private Item createPickupItem(Random random, int index) {
        int roll = random.nextInt(100);
        int tier = Math.max(1, (floorNumber + 1) / 2);
        if (roll < 38) {
            return Item.health("health_" + tier, "Med Gel", 22 + tier * 5);
        }
        if (roll < 65) {
            return Item.energy("energy_" + tier, "Aether Cell", 2 + tier);
        }
        if (roll < 77) {
            return Item.maxHealth("vitality_" + tier + "_" + index, "Vitality Lattice", 10 + tier * 3);
        }
        if (roll < 88) {
            return Item.maxEnergy("capacity_" + tier + "_" + index, "Core Capacitor", 1 + (tier >= 4 ? 1 : 0));
        }
        if (random.nextBoolean()) {
            int bonus = 3 + tier * 2;
            return Item.weapon("weapon_" + floorNumber + "_" + index,
                    tier >= 3 ? "Helix Arc Rifle" : "Pulse Blade",
                    "+" + bonus + " ATK", bonus);
        }
        int bonus = 2 + tier;
        return Item.armor("armor_" + floorNumber + "_" + index,
                tier >= 3 ? "Aegis Weave" : "Photon Mantle",
                "+" + bonus + " DEF", bonus);
    }

    private boolean validSpawn(GridPoint point, Set<GridPoint> occupied, int distanceFromStart, int distanceFromExit) {
        return !occupied.contains(point)
                && point.manhattanDistance(dungeon.start()) >= distanceFromStart
                && point.manhattanDistance(dungeon.exit()) >= distanceFromExit;
    }

    private EnemyType chooseEnemyType(Random random, int enemyIndex) {
        if (floorNumber % 5 == 0 && enemyIndex == 0) {
            return EnemyType.FLOOR_WARDEN;
        }
        int roll = random.nextInt(100);
        if (floorNumber >= 3 && roll < 25) {
            return EnemyType.ABYSS_MAGE;
        }
        if (floorNumber >= 2 && roll < 60) {
            return EnemyType.BONE_SENTINEL;
        }
        return EnemyType.CAVE_SLIME;
    }

    public boolean canMoveTo(int x, int y) {
        return dungeon().isWalkable(x, y);
    }

    public void movePlayerTo(int x, int y) {
        if (!canMoveTo(x, y)) {
            throw new IllegalArgumentException("Player cannot move onto a wall");
        }
        playerX = x;
        playerY = y;
        stepsTaken++;
    }

    public boolean isAtExit() {
        return playerX == dungeon().exit().x() && playerY == dungeon().exit().y();
    }

    public DungeonEnemy enemyAt(int x, int y) {
        for (DungeonEnemy enemy : enemies) {
            if (enemy.isAlive() && enemy.x == x && enemy.y == y) {
                return enemy;
            }
        }
        return null;
    }

    public DungeonChest chestAt(int x, int y) {
        for (DungeonChest chest : chests) {
            if (chest.x == x && chest.y == y) {
                return chest;
            }
        }
        return null;
    }

    public DungeonPickup pickupAt(int x, int y) {
        if (pickups == null) return null;
        for (DungeonPickup pickup : pickups) {
            if (!pickup.collected && pickup.x == x && pickup.y == y) return pickup;
        }
        return null;
    }

    public List<String> collectPickup(DungeonPickup pickup) {
        if (pickup == null || pickup.collected || pickup.item == null) return List.of();
        pickup.collected = true;
        Item item = pickup.item;
        List<String> result = new ArrayList<>();
        if (item.type == Item.Type.MAX_HEALTH || item.type == Item.Type.MAX_ENERGY) {
            hero.applyPermanentBoost(item);
            result.add(item.name + " activated: " + item.description);
        } else {
            hero.addItem(item);
            if (item.isEquipment()) {
                result.add("Found " + item.name + " (" + item.description + "). Open Equipment to equip it.");
            } else {
                result.add("Found " + item.name + " x" + item.quantity + ". Use it from Items in combat.");
            }
        }
        return result;
    }

    public List<String> openChest(DungeonChest chest) {
        if (chest == null || chest.opened) {
            return List.of();
        }
        chest.opened = true;
        Random lootRandom = new Random(floorSeed ^ ((long) chest.x << 32) ^ chest.y);
        int goldFound = 8 + floorNumber * 3 + lootRandom.nextInt(12);
        hero.gold += goldFound;
        List<String> result = new ArrayList<>();
        result.add("Chest opened: +" + goldFound + " gold.");
        if (lootRandom.nextInt(100) < 55) {
            hero.addItem(Item.health("health_1", "Med Gel", BattleEngine.potionHealAmount()));
            result.add("You also found a Med Gel. It was added to Items.");
        }
        if (lootRandom.nextInt(100) < 35) {
            int restored = hero.restoreMana(3);
            if (restored > 0) {
                result.add("An energy cell restores " + restored + " EN.");
            }
        }
        return result;
    }

    public List<String> defeatEnemy(DungeonEnemy defeated) {
        List<String> result = new ArrayList<>();
        if (defeated == null) {
            return result;
        }
        int experience = defeated.experienceReward;
        int gold = defeated.goldReward;
        hero.gold += gold;
        result.add("Victory: +" + experience + " XP, +" + gold + " gold.");
        result.addAll(hero.gainExperience(experience));

        Random lootRandom = new Random(defeated.id ^ floorSeed);
        if (lootRandom.nextInt(100) < 28) {
            hero.addItem(Item.health("health_1", "Med Gel", BattleEngine.potionHealAmount()));
            result.add("The enemy dropped a Med Gel. It was added to Items.");
        }
        for (Iterator<DungeonEnemy> iterator = enemies.iterator(); iterator.hasNext();) {
            if (iterator.next().id == defeated.id) {
                iterator.remove();
                break;
            }
        }
        return result;
    }

    public static long mixSeed(long seed, int value) {
        long mixed = seed + 0x9E3779B97F4A7C15L * value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
