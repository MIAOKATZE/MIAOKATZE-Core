package com.miaokatze.miaokatzecore.api.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Shared presentation order. Register after the mod has completed item/machine registration; suppliers must return
 * that mod's complete creative catalogue in domain order. Registration never registers, renames or modifies items.
 * Ownership is exact item identity + damage: distinct metadata of a shared GT ItemMachines may belong to different
 * mods.
 * NBT and stack size are deliberately ignored, so named/charged copies retain their variant's position.
 */
public final class ItemOrderCatalog {

    private static final Map<String, Supplier<List<ItemStack>>> SOURCES = new LinkedHashMap<>();
    private static volatile View cached;

    private ItemOrderCatalog() {}

    /** Replaces this owner's previous supplier, making repeated lifecycle registration harmless. */
    public static synchronized void register(String ownerModId, Supplier<List<ItemStack>> catalogue) {
        if (ownerModId == null || !ownerModId.matches("[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Invalid owner mod ID: " + ownerModId);
        SOURCES.put(ownerModId, Objects.requireNonNull(catalogue, "catalogue"));
        invalidate();
    }

    /** Call after changing a supplier's contents. NEI configuration reload also invalidates automatically. */
    public static synchronized void invalidate() {
        cached = null;
    }

    /** Owner-scoped notification; the shared ownership index is rebuilt atomically, even before registration. */
    public static void invalidate(String ownerModId) {
        Objects.requireNonNull(ownerModId, "ownerModId");
        invalidate();
    }

    /** Defensive copies in catalogue order; an unregistered owner has an empty catalogue. */
    public static List<ItemStack> orderedItems(String ownerModId) {
        List<ItemStack> result = new ArrayList<>();
        List<ItemStack> items = view().catalogues.get(ownerModId);
        if (items != null) for (ItemStack stack : items) result.add(stack.copy());
        return result;
    }

    /**
     * Reorders only the supplied creative-page entries. Unknown entries retain their relative order after known
     * entries;
     * no items from this or another mod are added. The input stacks themselves are never changed.
     */
    public static void sort(String ownerModId, List<ItemStack> items) {
        View order = view();
        items.sort((left, right) -> Integer.compare(order.rank(ownerModId, left), order.rank(ownerModId, right)));
    }

    /** Immutable ownership/rank snapshot for presentation adapters; lookup is O(1), not a catalogue scan. */
    public static View view() {
        View current = cached;
        if (current != null) return current;
        synchronized (ItemOrderCatalog.class) {
            if (cached != null) return cached;
            Map<Key, Entry> entries = new HashMap<>();
            Map<String, List<ItemStack>> catalogues = new HashMap<>();
            for (Map.Entry<String, Supplier<List<ItemStack>>> source : SOURCES.entrySet()) {
                List<ItemStack> items = new ArrayList<>();
                Item anchor = null;
                for (ItemStack stack : Objects.requireNonNull(
                    source.getValue()
                        .get(),
                    "catalogue returned null")) {
                    if (stack == null || stack.getItem() == null) continue;
                    Key key = new Key(stack);
                    Entry previous = entries.get(key);
                    if (previous != null) {
                        if (!previous.owner.equals(source.getKey())) throw new IllegalStateException(
                            "Item variant claimed by " + previous.owner + " and " + source.getKey());
                        continue;
                    }
                    if (anchor == null) anchor = stack.getItem();
                    entries.put(key, new Entry(source.getKey(), items.size(), anchor));
                    items.add(stack.copy());
                }
                catalogues.put(source.getKey(), Collections.unmodifiableList(items));
            }
            cached = new View(entries, catalogues);
            return cached;
        }
    }

    public static final class View {

        private final Map<Key, Entry> entries;
        private final Map<String, List<ItemStack>> catalogues;

        private View(Map<Key, Entry> entries, Map<String, List<ItemStack>> catalogues) {
            this.entries = entries;
            this.catalogues = catalogues;
        }

        public Entry entry(ItemStack stack) {
            return stack == null || stack.getItem() == null ? null : entries.get(new Key(stack));
        }

        private int rank(String owner, ItemStack stack) {
            Entry entry = entry(stack);
            return entry != null && entry.owner.equals(owner) ? entry.rank : Integer.MAX_VALUE;
        }
    }

    public static final class Entry {

        private final String owner;
        private final int rank;
        private final Item anchor;

        private Entry(String owner, int rank, Item anchor) {
            this.owner = owner;
            this.rank = rank;
            this.anchor = anchor;
        }

        public String owner() {
            return owner;
        }

        public int rank() {
            return rank;
        }

        /** Resolve live registry IDs, including after a server/world remap, without caching numeric IDs. */
        public int anchorId() {
            return Item.getIdFromItem(anchor);
        }
    }

    private static final class Key {

        private final Item item;
        private final int damage;

        private Key(ItemStack stack) {
            item = stack.getItem();
            damage = stack.getItemDamage();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return item == key.item && damage == key.damage;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(item) + damage;
        }
    }
}
