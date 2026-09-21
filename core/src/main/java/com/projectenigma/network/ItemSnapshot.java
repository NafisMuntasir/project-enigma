package com.projectenigma.network;

import com.projectenigma.model.Item;
import java.io.Serializable;

/** Immutable item data; never share mutable inventory objects with a snapshot. */
public record ItemSnapshot(String id, String name, String description, Item.Type type,
                           int amount, int attackBonus, int defenseBonus, int quantity,
                           boolean stackable) implements Serializable {
    private static final long serialVersionUID = 1L;
    public static ItemSnapshot of(Item item) {
        return item == null ? null : new ItemSnapshot(item.id, item.name, item.description, item.type,
                item.amount, item.attackBonus, item.defenseBonus, item.quantity, item.stackable);
    }
    public Item toItem() {
        Item item = new Item(id, name, description, type, Math.max(0, Math.min(10000, amount)),
                Math.max(0, Math.min(1000, attackBonus)), Math.max(0, Math.min(1000, defenseBonus)), stackable);
        item.quantity = Math.max(0, Math.min(10000, quantity));
        return item;
    }
}
