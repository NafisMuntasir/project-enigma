package com.projectenigma.model;

public class DungeonPickup {
    public int x;
    public int y;
    public Item item;
    public boolean collected;

    public DungeonPickup() {
        // Required by libGDX Json.
    }

    public DungeonPickup(int x, int y, Item item) {
        this.x = x;
        this.y = y;
        this.item = item;
    }
}
