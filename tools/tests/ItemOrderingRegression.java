import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import com.miaokatze.miaokatzecore.api.items.ItemOrderCatalog;
import com.miaokatze.miaokatzecore.client.items.NEIItemOrdering;

import codechicken.nei.ItemSorter;
import codechicken.nei.api.ItemInfo;

/** Bounded production comparator contracts using the official RC2 NEI jar, without launching a game. */
public final class ItemOrderingRegression {
    public static void main(String[] args) throws Exception {
        boolean withoutNei = args.length > 0 && "--without-nei".equals(args[0]);
        List<URL> urls = new ArrayList<>();
        for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (withoutNei && path.toLowerCase().contains("notenoughitems")) continue;
            urls.add(new File(path).toURI().toURL());
        }
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        Launch.minecraftHome = new File(".");
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData", Object[].class)
            .invoke(null, (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."), new ArrayList<>() });
        Method run = Launch.classLoader.loadClass(withoutNei ? "ItemOrderingNoNeiRuntime" : "ItemOrderingRuntime")
            .getMethod("run");
        run.setAccessible(true);
        run.invoke(null);
    }
}

class ItemOrderingBootstrap {
    static void run() throws Exception {
        Thread.currentThread().setName("Server thread");
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new File("."));
        Object side = Proxy.newProxyInstance(ItemOrderingBootstrap.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (proxy, method, arguments) -> method.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        Field delegate = cpw.mods.fml.common.FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        delegate.setAccessible(true);
        delegate.set(cpw.mods.fml.common.FMLCommonHandler.instance(), side);
        net.minecraft.init.Bootstrap.func_151354_b();
    }
}

class ItemOrderingNoNeiRuntime {
    public static void run() throws Exception {
        ItemOrderingBootstrap.run();
        try {
            Class.forName("codechicken.nei.ItemSorter");
            throw new AssertionError("NEI was not actually removed from the classpath");
        } catch (ClassNotFoundException expected) {}
        Class<?> entry = Class.forName("com.miaokatze.miaokatzecore.MiaokatzeCore");
        entry.getDeclaredMethods();
        entry.getDeclaredConstructors()[0].newInstance();
        ItemStack stack = new ItemStack(new Item());
        ItemOrderCatalog.register("without_nei", () -> Arrays.asList(stack));
        List<ItemStack> items = ItemOrderCatalog.orderedItems("without_nei");
        ItemOrderCatalog.sort("without_nei", items);
        if (items.size() != 1) throw new AssertionError("Common catalogue requires NEI");
        System.out.println("RESULT no-NEI common-entry-classload=PASS catalogue=PASS");
    }
}

class ItemOrderingRuntime {
    public static void run() throws Exception {
        ItemOrderingBootstrap.run();
        // Use RC2's actual GT ItemMachines type without triggering GT's full registration bootstrap.
        Class<?> machineClass = Class.forName("gregtech.common.blocks.ItemMachines");
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Item sharedMachine = (Item) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafeField.get(null), machineClass);
        Field itemDelegate = Item.class.getDeclaredField("delegate");
        itemDelegate.setAccessible(true);
        itemDelegate.set(sharedMachine, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<Item>(sharedMachine, Item.class));
        Item aItem = new Item(), zItem = new Item(), ordinary = new Item();
        List<ItemStack> a = Arrays.asList(new ItemStack(aItem), new ItemStack(sharedMachine, 1, 77), new ItemStack(aItem, 1, 2));
        List<ItemStack> z = Arrays.asList(new ItemStack(sharedMachine, 1, 88), new ItemStack(zItem), new ItemStack(sharedMachine, 1, 99));
        List<ItemStack> other = Arrays.asList(new ItemStack(ordinary), new ItemStack(sharedMachine, 1, 1));
        List<ItemStack> all = new ArrayList<>(a);
        all.addAll(z);
        all.addAll(other);
        List<Item> uniqueItems = Arrays.asList(aItem, zItem, ordinary, sharedMachine);
        Object registry = cpw.mods.fml.common.registry.GameData.getItemRegistry();
        Method raw = registry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true);
        for (int i = 0; i < uniqueItems.size(); i++) {
            raw.invoke(registry, 10000 + i, "fixture:item_" + i, uniqueItems.get(i));
            ItemInfo.itemOwners.put(uniqueItems.get(i), i == 3 ? "gregtech" : i == 0 ? "alpha" : i == 1 ? "zeta" : "other");
        }
        for (int i = 0; i < all.size(); i++) all.get(i).setStackDisplayName("reverse_" + (all.size() - i));
        AtomicInteger evaluations = new AtomicInteger();
        ItemOrderCatalog.invalidate("alpha");
        ItemOrderCatalog.register("alpha", () -> { evaluations.incrementAndGet(); return a; });
        ItemOrderCatalog.register("alpha", () -> { evaluations.incrementAndGet(); return a; });
        ItemOrderCatalog.register("zeta", () -> z);
        check(evaluations.get() == 0, "Supplier evaluated during registration");
        check(ItemOrderCatalog.orderedItems("alpha").size() == 3, "Wrong catalogue");
        ItemOrderCatalog.orderedItems("alpha").get(0).setItemDamage(90);
        check(ItemOrderCatalog.orderedItems("alpha").get(0).getItemDamage() == 0, "Mutable catalogue leaked");
        List<ItemStack> creative = new ArrayList<>(Arrays.asList(other.get(0), a.get(2), a.get(0), z.get(0)));
        ItemOrderCatalog.sort("alpha", creative);
        check(creative.equals(Arrays.asList(a.get(0), a.get(2), other.get(0), z.get(0))), "Creative sort inserted or reordered foreign entries");

        ItemSorter.entries.clear();
        ItemSorter.list.clear();
        String[] names = { "minecraft", "mod", "id", "default", "damage", "name" };
        for (int i = 0; i < names.length; i++) ItemSorter.add("nei.itemsort." + names[i], upstream(i));
        NEIItemOrdering.refresh();
        Object id = ItemSorter.find("nei.itemsort.id");
        NEIItemOrdering.refresh();
        check(id == ItemSorter.find("nei.itemsort.id"), "Wrapped twice");
        String config = ItemSorter.getSaveString(ItemSorter.list);
        check(config.equals("nei.itemsort.minecraft,nei.itemsort.mod,nei.itemsort.id,nei.itemsort.default,nei.itemsort.damage,nei.itemsort.name"), "Changed sort options");
        ItemSorter.instance.ordering.put(a.get(0), 200);
        ItemSorter.instance.ordering.put(a.get(1), 1);
        ItemSorter.instance.ordering.put(z.get(2), -10);
        Random random = new Random(20261022L);
        long triples = 0;
        for (int seed = 0; seed < 64; seed++) {
            List<Integer> ids = new ArrayList<>(Arrays.asList(10000, 10001, 10002, 10003));
            Collections.shuffle(ids, random);
            for (int i = 0; i < uniqueItems.size(); i++)
                raw.invoke(registry, ids.get(i), "fixture:item_" + i, uniqueItems.get(i));
            List<ItemStack> actual = new ArrayList<>(all);
            Collections.shuffle(actual, random);
            ItemSorter.sort(actual);
            check(actual.subList(0, 3).equals(a), "Alpha catalogue split/reordered under remap");
            check(actual.subList(actual.size() - 3, actual.size()).equals(z), "Zeta catalogue split/reordered under remap");
            check("gregtech".equals(ItemInfo.itemOwners.get(sharedMachine)), "Shared GT owner globally mutated");
            verifyComparator(all);
            triples += all.size() * all.size() * all.size();
            // Disable mod priority too: shared anchors and third-party variants still form a valid total order.
            ItemSorter.list = ItemSorter.fromSaveString("nei.itemsort.id," + config);
            verifyComparator(all);
            triples += all.size() * all.size() * all.size();
            ItemSorter.list = ItemSorter.fromSaveString(config);
        }
        check(evaluations.get() == 2, "Comparison repeatedly scanned catalogue suppliers");
        // Both mods can anchor on the same GT Item, while the unclaimed GT metadata remains independent.
        ItemOrderCatalog.register("alpha", () -> Arrays.asList(a.get(1), a.get(0), a.get(2)));
        ItemSorter.list = ItemSorter.fromSaveString("nei.itemsort.id," + config);
        verifyComparator(all);
        ItemSorter.list = ItemSorter.fromSaveString(config);
        ItemOrderCatalog.register("alpha", () -> a);
        // Configuration recreation and active-entry replacement use NEI's real option mechanism.
        NEIItemOrdering.refresh();
        ItemSorter.list = ItemSorter.fromSaveString(config);
        List<ItemStack> actual = new ArrayList<>(all);
        ItemSorter.sort(actual);
        check(actual.subList(0, 3).equals(a), "Reload lost catalogue");
        ItemSorter.list = ItemSorter.fromSaveString("nei.itemsort.name," + config);
        List<ItemStack> named = new ArrayList<>(all);
        named.sort(upstream(5));
        ItemSorter.sort(actual);
        check(actual.equals(named), "Explicit name priority overridden");
        // New variants appear only after invalidation, and rank cache rebuild cannot retain stale ownership.
        ItemOrderCatalog.register("alpha", () -> Arrays.asList(a.get(2), a.get(0)));
        check(ItemOrderCatalog.view().entry(a.get(1)) == null, "Replaced supplier retained stale metadata ownership");
        ItemOrderCatalog.register("zeta", () -> Arrays.asList(a.get(2)));
        try {
            ItemOrderCatalog.view();
            throw new AssertionError("Cross-mod duplicate accepted");
        } catch (IllegalStateException expected) {}
        ItemOrderCatalog.register("zeta", () -> z);
        ItemOrderCatalog.view();
        System.out.println("SOURCE NEI " + ItemSorter.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE GT " + machineClass.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("RESULT item-order remaps=64/64 comparator-triples=" + triples
            + " shared-meta=PASS creative=PASS foreign=PASS reload=PASS duplicate-register=PASS name-priority=PASS cache=PASS conflict=PASS");
    }

    static void verifyComparator(List<ItemStack> all) {
        for (ItemStack a : all) for (ItemStack b : all) {
            int ab = Integer.signum(ItemSorter.instance.compare(a, b));
            check(ab == -Integer.signum(ItemSorter.instance.compare(b, a)), "Antisymmetry");
            for (ItemStack c : all) {
                if (ab <= 0 && ItemSorter.instance.compare(b, c) <= 0)
                    check(ItemSorter.instance.compare(a, c) <= 0, "Transitivity");
            }
        }
    }

    static Comparator<ItemStack> upstream(int index) throws Exception {
        Method method = ItemSorter.class.getDeclaredMethod("lambda$initConfig$" + index, ItemStack.class, ItemStack.class);
        method.setAccessible(true);
        return (left, right) -> {
            try { return (Integer) method.invoke(null, left, right); }
            catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        };
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
