package com.miaokatze.miaokatzecore.api.bq;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Native NBT/BQ contracts without a game, live quest database changes or disk writes. */
public final class QuestPreflightContracts {

    public static void main(String[] args) throws Exception {
        if (!bootstrap(args)) return;
        java.io.PrintStream output = System.out;
        jsonIdentity();
        prerequisites();
        pack();
        output
            .println("QuestPreflightContracts: PASS (JSON identity, native parsing, prerequisite and pack preflight)");
    }

    private static boolean bootstrap(String[] args) throws Exception {
        if (!(QuestPreflightContracts.class.getClassLoader() instanceof LaunchClassLoader)) {
            List<URL> urls = new ArrayList<>();
            for (String path : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
                urls.add(
                    new File(path).toURI()
                        .toURL());
            }
            Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
            Launch.blackboard = new HashMap<>();
            Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
            Launch.minecraftHome = new File(".");
            Thread.currentThread()
                .setContextClassLoader(Launch.classLoader);
            Launch.classLoader.loadClass("cpw.mods.fml.common.Loader")
                .getMethod("injectData", Object[].class)
                .invoke(
                    null,
                    (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."),
                        new ArrayList<>() });
            Launch.classLoader.loadClass(QuestPreflightContracts.class.getName())
                .getMethod("main", String[].class)
                .invoke(null, (Object) args);
            return false;
        }
        Thread.currentThread()
            .setName("Server thread");
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new File("."));
        Field logSide = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        logSide.setAccessible(true);
        logSide.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        Object side = Proxy.newProxyInstance(
            QuestPreflightContracts.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (proxy, method, arguments) -> method.getName()
                .equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        Field delegate = cpw.mods.fml.common.FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        delegate.setAccessible(true);
        delegate.set(cpw.mods.fml.common.FMLCommonHandler.instance(), side);
        net.minecraft.init.Bootstrap.func_151354_b();
        return true;
    }

    private static void jsonIdentity() {
        QuestDefinitionPreflight.validateJsonIds(
            new JsonParser()
                .parse("{\"nested\":[{\"questIDHigh:4\":9223372036854775807,\"questIDLow:4\":-9223372036854775808}]}"));
        for (String invalid : new String[] { "{\"questIDHigh:4\":1.5}", "{\"questIDLow:4\":9223372036854775808}",
            "{\"questIDHigh:4\":\"1\"}", "{\"questIDLow:3\":1}", "{\"questLineIDHigh\":1}",
            "{\"nested\":[{\"questLineIDLow:8\":\"2\"}]}" }) {
            reject(() -> QuestDefinitionPreflight.validateJsonIds(new JsonParser().parse(invalid)));
        }
    }

    private static void prerequisites() {
        NBTTagCompound valid = identity("questID", 9);
        NBTTagCompound before = (NBTTagCompound) valid.copy();
        QuestDefinitionPreflight.validate(valid);
        check(valid.equals(before), "native parse cannot mutate source definition");
        valid.setIntArray("preRequisites", new int[] { 1, 2 });
        QuestDefinitionPreflight.validate(valid);
        NBTTagList list = new NBTTagList();
        list.appendTag(identity("questID", 1));
        valid.setTag("preRequisites", list);
        QuestDefinitionPreflight.validate(valid);
        NBTTagCompound malformed = identity("questID", 9);
        malformed.setString("preRequisites", "broken");
        reject(() -> QuestDefinitionPreflight.validate(malformed));
        NBTTagList wrongType = new NBTTagList();
        wrongType.appendTag(new NBTTagString("broken"));
        malformed.setTag("preRequisites", wrongType);
        reject(() -> QuestDefinitionPreflight.validate(malformed));
        NBTTagList missingHalf = new NBTTagList();
        NBTTagCompound prerequisite = new NBTTagCompound();
        prerequisite.setLong("questIDHigh", 1);
        missingHalf.appendTag(prerequisite);
        malformed.setTag("preRequisites", missingHalf);
        reject(() -> QuestDefinitionPreflight.validate(malformed));
    }

    private static void pack() {
        Map<String, NBTTagCompound> files = new HashMap<>();
        files.put("line", identity("questLineID", 1));
        files.put("quest", identity("questID", 2));
        files.put("entry", identity("questID", 2));
        JsonArray manifest = new JsonArray();
        JsonObject line = line(1);
        manifest.add(line);
        AtomicInteger reads = new AtomicInteger();
        Map<String, NBTTagCompound> prepared = QuestPackPreflight.prepareResources(manifest, path -> {
            reads.incrementAndGet();
            return files.get(path);
        });
        check(reads.get() == 3 && prepared.size() == 3, "each unique path resolved once");
        check(
            files.get("quest")
                .equals(identity("questID", 2)),
            "preflight leaves resource tags intact");
        files.remove("entry");
        reject(() -> QuestPackPreflight.prepareResources(manifest, files::get));
        files.put("entry", identity("questID", 3));
        reject(() -> QuestPackPreflight.prepareResources(manifest, files::get));
        files.put("entry", identity("questID", 2));
        manifest.add(line);
        reject(() -> QuestPackPreflight.prepareResources(manifest, files::get));
        JsonArray empty = new JsonArray();
        JsonObject emptyLine = line(1);
        emptyLine.add("entries", new JsonArray());
        empty.add(emptyLine);
        check(
            QuestPackPreflight.prepareResources(empty, files::get)
                .size() == 1,
            "explicit empty entries accepted");
        emptyLine.addProperty("orderIndex", 2147483648L);
        reject(() -> QuestPackPreflight.prepareResources(empty, files::get));
        check(
            QuestPackPreflight.prepareResources(new JsonArray(), files::get)
                .isEmpty(),
            "empty pack accepted");
    }

    private static JsonObject line(long id) {
        JsonObject line = new JsonObject();
        line.addProperty("idHigh", 0);
        line.addProperty("idLow", id);
        line.addProperty("orderIndex", 0);
        line.addProperty("lineFile", "line");
        JsonArray entries = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("questFile", "quest");
        entry.addProperty("entryFile", "entry");
        entries.add(entry);
        line.add("entries", entries);
        return line;
    }

    private static NBTTagCompound identity(String prefix, long id) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(prefix + "High", 0);
        tag.setLong(prefix + "Low", id);
        return tag;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("malformed pack accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
