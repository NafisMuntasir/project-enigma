package com.projectenigma.network;

import com.projectenigma.model.Hero;
import com.projectenigma.model.Item;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record InventorySnapshot(List<ItemSnapshot> items, ItemSnapshot weapon, ItemSnapshot armor) implements Serializable {
    private static final long serialVersionUID = 1L;
    public InventorySnapshot { items = items == null ? List.of() : List.copyOf(items); }
    public static InventorySnapshot of(Hero hero) {
        return new InventorySnapshot(hero.inventory == null ? List.of() : hero.inventory.stream()
                .filter(i -> i != null && i.type != null && i.quantity > 0).map(ItemSnapshot::of).toList(),
                ItemSnapshot.of(hero.equippedWeapon), ItemSnapshot.of(hero.equippedArmor));
    }
    public void restore(Hero hero) {
        hero.inventory = new ArrayList<>();
        for (ItemSnapshot entry : items.stream().limit(512).toList()) {
            if (entry.type() != null && entry.id() != null && entry.quantity() > 0) hero.inventory.add(entry.toItem());
        }
        hero.equippedWeapon = weapon != null && weapon.type() == Item.Type.WEAPON ? weapon.toItem() : null;
        hero.equippedArmor = armor != null && armor.type() == Item.Type.ARMOR ? armor.toItem() : null;
    }
}
