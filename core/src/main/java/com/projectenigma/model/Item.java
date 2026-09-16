package com.projectenigma.model;

/**
 * Small serializable item model shared by dungeon pickups and inventory UI.
 * Consumables stack by id; equipment stays as individual entries.
 */
public class Item {
    public enum Type { HEALTH, ENERGY, MAX_HEALTH, MAX_ENERGY, WEAPON, ARMOR }

    public String id;
    public String name;
    public String description;
    public Type type;
    public int amount;
    public int attackBonus;
    public int defenseBonus;
    public int quantity = 1;
    public boolean stackable;

    public Item() {
        // Required by libGDX Json.
    }

    public Item(String id, String name, String description, Type type,
                int amount, int attackBonus, int defenseBonus, boolean stackable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.amount = amount;
        this.attackBonus = attackBonus;
        this.defenseBonus = defenseBonus;
        this.stackable = stackable;
    }

    public static Item health(String id, String name, int amount) {
        return new Item(id, name, "Restores " + amount + " HP.", Type.HEALTH, amount, 0, 0, true);
    }

    public static Item energy(String id, String name, int amount) {
        return new Item(id, name, "Restores " + amount + " EN.", Type.ENERGY, amount, 0, 0, true);
    }

    public static Item maxHealth(String id, String name, int amount) {
        return new Item(id, name, "Permanently increases maximum HP by " + amount + ".",
                Type.MAX_HEALTH, amount, 0, 0, false);
    }

    public static Item maxEnergy(String id, String name, int amount) {
        return new Item(id, name, "Permanently increases maximum EN by " + amount + ".",
                Type.MAX_ENERGY, amount, 0, 0, false);
    }

    public static Item weapon(String id, String name, String description, int attackBonus) {
        return new Item(id, name, description, Type.WEAPON, 0, attackBonus, 0, false);
    }

    public static Item armor(String id, String name, String description, int defenseBonus) {
        return new Item(id, name, description, Type.ARMOR, 0, 0, defenseBonus, false);
    }

    public boolean isConsumable() {
        return type == Type.HEALTH || type == Type.ENERGY;
    }

    public boolean isEquipment() {
        return type == Type.WEAPON || type == Type.ARMOR;
    }

    public Item copy() {
        Item copy = new Item(id, name, description, type, amount, attackBonus, defenseBonus, stackable);
        copy.quantity = quantity;
        return copy;
    }
}
