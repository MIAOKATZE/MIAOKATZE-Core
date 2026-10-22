package com.miaokatze.miaokatzecore.client.items;

import java.util.ArrayList;
import java.util.Comparator;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog.Entry;
import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog.View;

import codechicken.nei.ItemSorter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Optional client adapter. No common API or dedicated-server entry point links to NEI classes. */
public final class NEIItemOrdering {

    private static boolean installed;

    private NEIItemOrdering() {}

    public static synchronized void install() {
        if (installed) return;
        MinecraftForge.EVENT_BUS.register(new NEIItemOrdering());
        installed = true;
    }

    @SubscribeEvent
    public void onConfigsLoaded(NEIConfigsLoadedEvent event) {
        refresh();
    }

    /** Also useful to hosts that install the adapter after NEI's configuration event. */
    public static void refresh() {
        ItemOrderCatalog.invalidate();
        ArrayList<ItemSorter.SortEntry> active = new ArrayList<>(ItemSorter.list);
        for (int i = 0; i < ItemSorter.entries.size(); i++) {
            ItemSorter.SortEntry original = ItemSorter.entries.get(i);
            String option = original.name;
            if (original.comparator instanceof CatalogComparator
                || !("nei.itemsort.minecraft".equals(option) || "nei.itemsort.mod".equals(option)
                    || "nei.itemsort.id".equals(option)))
                continue;
            ItemSorter.SortEntry replacement = new ItemSorter.SortEntry(
                option,
                new CatalogComparator(option, original.comparator));
            ItemSorter.entries.set(i, replacement);
            for (int j = 0; j < active.size(); j++) if (active.get(j) == original) active.set(j, replacement);
        }
        ItemSorter.list = active;
    }

    private static final class CatalogComparator implements Comparator<ItemStack> {

        private final String option;
        private final Comparator<ItemStack> original;

        private CatalogComparator(String option, Comparator<ItemStack> original) {
            this.option = option;
            this.original = original;
        }

        @Override
        public int compare(ItemStack left, ItemStack right) {
            View view = ItemOrderCatalog.view();
            Entry a = view.entry(left), b = view.entry(right);
            if (a == null && b == null) return original.compare(left, right);
            if ("nei.itemsort.minecraft".equals(option))
                return Boolean.compare("minecraft".equals(owner(right, b)), "minecraft".equals(owner(left, a)));
            if ("nei.itemsort.mod".equals(option)) return compareOwners(owner(left, a), owner(right, b));

            // A single tuple for every pair, including catalogue/non-catalogue pairs, guarantees transitivity.
            // One fixed catalogue anchor is sufficient; looking for the minimum ID per comparison is quadratic.
            int result = Integer.compare(
                a == null ? Item.getIdFromItem(left.getItem()) : a.anchorId(),
                b == null ? Item.getIdFromItem(right.getItem()) : b.anchorId());
            if (result != 0) return result;
            if (a == null || b == null) return a == null ? 1 : -1;
            result = a.owner()
                .compareTo(b.owner());
            return result != 0 ? result : Integer.compare(a.rank(), b.rank());
        }

        private static String owner(ItemStack stack, Entry entry) {
            return entry == null ? ItemInfo.itemOwners.get(stack.getItem()) : entry.owner();
        }

        private static int compareOwners(String left, String right) {
            if (left == null) return right == null ? 0 : 1;
            return right == null ? -1 : left.compareTo(right);
        }
    }
}
