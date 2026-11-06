#!/usr/bin/env python3
"""Run the original BQ synchronization/PacketSender sources against deterministic JVM fakes.

This checks the real quickSync -> queued serialization -> queued broadcast -> onClient chain,
including a login between the two queues. Minecraft, BQ storage, scheduling and transport are
fakes. The production injection methods are invoked reflectively at HEAD; this is NOT proof
of Mixin transformation, a game launch, packet fragmentation, or actual disk persistence.
No network access or project build. Requires a local BQ source checkout and real JDK 8.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--bq-source", type=Path, required=True)
parser.add_argument("--bqapi-reinjector-source", type=Path, required=True,
                    help="Unmodified BQReinjector.java at BetterQuestingAPI ab26a6b468eeb2e73b75a286cab4034cade2b98d")
parser.add_argument("--jdk-home", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
fake_root = output / "fakes"
classes = output / "classes"
classes.mkdir()
receipts = []


def run(name, command):
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (output / (name + ".txt")).write_bytes(result.stdout)
    receipts.append({"name": name, "command": command, "exit_code": result.returncode})
    (output / "commands.json").write_text(json.dumps(receipts, indent=2), encoding="utf-8")
    print(result.stdout.decode("utf-8", errors="replace"), end="", flush=True)
    if result.returncode:
        raise SystemExit(result.returncode)
    return result.stdout


def tool(name):
    return str(args.jdk_home / "bin" / (name + (".exe" if os.name == "nt" else "")))


def fake(name, body):
    package, short_name = name.rsplit(".", 1)
    path = fake_root / (name.replace(".", "/") + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("package " + package + ";\n" + body.replace("$NAME", short_name), encoding="utf-8")


for name in ["javax.annotation.Nullable", "javax.annotation.Nonnull",
             "com.gtnewhorizon.gtnhmixins.LateMixin"]:
    fake(name, "public @interface $NAME {}")
fake("org.spongepowered.asm.mixin.Mixin", 'public @interface Mixin { String[] targets(); boolean remap(); }')
fake("org.spongepowered.asm.mixin.injection.At", 'public @interface At { String value(); }')
fake("org.spongepowered.asm.mixin.injection.Inject", '''public @interface Inject {
    String method(); At at(); boolean cancellable(); }''')
fake("org.spongepowered.asm.mixin.injection.callback.CallbackInfo", '''public class CallbackInfo {
    public boolean cancelled; public void cancel() { cancelled = true; } }''')
fake("cpw.mods.fml.relauncher.Side", '''public enum Side { CLIENT, SERVER;
    public boolean isClient() { return this == CLIENT; } }''')
fake("cpw.mods.fml.relauncher.SideOnly", "public @interface SideOnly { Side value(); }")
fake("cpw.mods.fml.relauncher.FMLLaunchHandler", '''public class FMLLaunchHandler {
    public static Side current = Side.CLIENT; public static Side side() { return current; } }''')
fake("com.gtnewhorizon.gtnhmixins.ILateMixinLoader", '''public interface ILateMixinLoader {
    String getMixinConfig(); java.util.List<String> getMixins(java.util.Set<String> loadedMods); }''')
fake("cpw.mods.fml.common.eventhandler.Event", "public class Event {}")
fake("net.minecraft.util.ResourceLocation", '''public final class ResourceLocation {
    private final String id; public ResourceLocation(String id) { this.id = id; }
    public String toString() { return id; } }''')
fake("net.minecraft.nbt.NBTTagCompound", '''public class NBTTagCompound {
    private final java.util.Map<String,Object> values = new java.util.HashMap<>();
    public void setTag(String key, Object value) { values.put(key, value); }
    public void setString(String key, String value) { values.put(key, value); }
    public String getString(String key) { Object v = values.get(key); return v == null ? "" : v.toString(); }
    public void setInteger(String key, int value) { values.put(key, value); }
    public int getInteger(String key) { return values.containsKey(key) ? (Integer)values.get(key) : 0; }
    public void setBoolean(String key, boolean value) { values.put(key, value); }
    public boolean getBoolean(String key) { return Boolean.TRUE.equals(values.get(key)); }
    public boolean hasKey(String key) { return values.containsKey(key); }
    public boolean hasKey(String key, int type) { return values.containsKey(key); }
    public void removeTag(String key) { values.remove(key); }
    public NBTTagCompound getCompoundTag(String key) {
        Object v = values.get(key); return v instanceof NBTTagCompound ? (NBTTagCompound)v : new NBTTagCompound(); }
    public NBTTagList getTagList(String key, int type) {
        Object v = values.get(key); return v instanceof NBTTagList ? (NBTTagList)v : new NBTTagList(); }
}''')
fake("net.minecraft.nbt.NBTTagList", '''public class NBTTagList {
    private final java.util.List<NBTTagCompound> values = new java.util.ArrayList<>();
    public void appendTag(NBTTagCompound value) { values.add(value); }
    public int tagCount() { return values.size(); }
    public NBTTagCompound getCompoundTagAt(int index) { return values.get(index); }
}''')
fake("net.minecraftforge.common.util.Constants", '''public class Constants {
    public static class NBT { public static final int TAG_LIST = 9, TAG_COMPOUND = 10; } }''')
fake("net.minecraftforge.common.MinecraftForge", '''public class MinecraftForge {
    public static final Bus EVENT_BUS = new Bus(); public static class Bus {
        public final java.util.List<Object> events = new java.util.ArrayList<>();
        public boolean post(Object event) { events.add(event); return false; } } }''')
fake("net.minecraft.network.NetworkManager", '''public class NetworkManager {
    public boolean local = true; public boolean isLocalChannel() { return local; } }''')
fake("net.minecraft.client.network.NetHandlerPlayClient", '''public class NetHandlerPlayClient {
    public final net.minecraft.network.NetworkManager manager = new net.minecraft.network.NetworkManager();
    public net.minecraft.network.NetworkManager getNetworkManager() { return manager; } }''')
fake("net.minecraft.client.Minecraft", '''public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft(); public boolean integrated = true;
    public net.minecraft.client.network.NetHandlerPlayClient handler = new net.minecraft.client.network.NetHandlerPlayClient();
    public static Minecraft getMinecraft() { return INSTANCE; }
    public boolean isIntegratedServerRunning() { return integrated; }
    public net.minecraft.client.network.NetHandlerPlayClient getNetHandler() { return handler; } }''')
fake("net.minecraft.entity.player.EntityPlayerMP", '''public class EntityPlayerMP {
    public final java.util.UUID id; public EntityPlayerMP(java.util.UUID id) { this.id = id; } }''')
fake("net.minecraft.server.MinecraftServer", '''public class MinecraftServer {
    public final Manager manager = new Manager(); public Manager getConfigurationManager() { return manager; }
    public static class Manager { public final java.util.List<net.minecraft.entity.player.EntityPlayerMP>
        playerEntityList = new java.util.ArrayList<>(); } }''')
fake("cpw.mods.fml.common.FMLCommonHandler", '''public class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();
    public final net.minecraft.server.MinecraftServer server = new net.minecraft.server.MinecraftServer();
    public static FMLCommonHandler instance() { return INSTANCE; }
    public net.minecraft.server.MinecraftServer getMinecraftServerInstance() { return server; } }''')
fake("cpw.mods.fml.common.network.NetworkRegistry", "public class NetworkRegistry { public static class TargetPoint {} }")
fake("betterquesting.api.api.QuestingAPI", '''public class QuestingAPI {
    public static java.util.UUID getQuestingUUID(net.minecraft.entity.player.EntityPlayerMP player) { return player.id; } }''')
fake("betterquesting.api.storage.BQ_Settings", "public class BQ_Settings { public static boolean noRewards; }")
fake("com.hfstudio.bqapi.BQApi", '''public class BQApi {
    public static int calls; public static void reinject(net.minecraft.server.MinecraftServer server) { calls++; } }''')
fake("com.hfstudio.bqapi.runtime.BQManagedQuestProgress", '''public class BQManagedQuestProgress {
    public static int calls; public static void replay() { calls++; } }''')
fake("betterquesting.handlers.SaveLoadHandler", '''public class SaveLoadHandler {
    public static final SaveLoadHandler INSTANCE = new SaveLoadHandler();
    public int dirty; public void markDirty() { dirty++; } }''')
fake("betterquesting.network.handlers.NetSettingSync", '''public class NetSettingSync {
    public static int calls; public static void sendSync(net.minecraft.entity.player.EntityPlayerMP player) { calls++; } }''')
fake("betterquesting.api2.utils.Tuple2", '''public class Tuple2<A,B> {
    private final A a; private final B b; public Tuple2(A a, B b) { this.a=a; this.b=b; }
    public A getFirst() { return a; } public B getSecond() { return b; } }''')
fake("betterquesting.api2.utils.BQThreadedIO", '''public class BQThreadedIO {
    public static final BQThreadedIO INSTANCE = new BQThreadedIO();
    public final java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();
    public void enqueue(Runnable job) { queue.add(job); }
    public void step() { queue.remove().run(); }
    public void drain() { while (!queue.isEmpty()) step(); } }''')
fake("betterquesting.api.utils.NBTConverter", '''public class NBTConverter {
    public enum UuidValueType { QUEST, QUEST_LINE;
        public void writeId(java.util.UUID id, net.minecraft.nbt.NBTTagCompound tag) { tag.setString("uuid", id.toString()); }
        public java.util.Optional<java.util.UUID> tryReadId(net.minecraft.nbt.NBTTagCompound tag) {
            return tag.hasKey("uuid") ? java.util.Optional.of(java.util.UUID.fromString(tag.getString("uuid"))) : java.util.Optional.empty(); }
        public net.minecraft.nbt.NBTTagList writeIds(java.util.Collection<java.util.UUID> ids) {
            net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
            for (java.util.UUID id: ids) { net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
                writeId(id, tag); list.appendTag(tag); } return list; }
        public java.util.List<java.util.UUID> readIds(net.minecraft.nbt.NBTTagCompound tag, String key) {
            java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
            net.minecraft.nbt.NBTTagList list = tag.getTagList(key, 10);
            for (int i=0;i<list.tagCount();i++) tryReadId(list.getCompoundTagAt(i)).ifPresent(ids::add); return ids; }
    } }''')
fake("betterquesting.api.questing.IQuest", '''public interface IQuest {
    net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag);
    void readFromNBT(net.minecraft.nbt.NBTTagCompound tag);
    net.minecraft.nbt.NBTTagCompound writeProgressToNBT(net.minecraft.nbt.NBTTagCompound tag, java.util.List<java.util.UUID> users);
    void readProgressFromNBT(net.minecraft.nbt.NBTTagCompound tag, boolean merge); }''')
fake("betterquesting.questing.QuestDatabase", '''public class QuestDatabase extends java.util.LinkedHashMap<java.util.UUID,betterquesting.api.questing.IQuest> {
    public static final QuestDatabase INSTANCE = new QuestDatabase(); public int clears;
    @Override public void clear() { clears++; super.clear(); }
    public java.util.Map<java.util.UUID,betterquesting.api.questing.IQuest> filterKeys(java.util.Collection<java.util.UUID> ids) {
        java.util.Map<java.util.UUID,betterquesting.api.questing.IQuest> result = new java.util.LinkedHashMap<>();
        for (java.util.UUID id: ids) if (containsKey(id)) result.put(id,get(id)); return result; }
    public betterquesting.api.questing.IQuest createNew(java.util.UUID id) { Quest q = new Quest(); put(id,q); return q; }
    public static class Quest implements betterquesting.api.questing.IQuest {
        public int definition; public final java.util.Map<java.util.UUID,Integer> progress = new java.util.HashMap<>();
        public final java.util.Map<java.util.UUID,Integer> claims = new java.util.HashMap<>();
        public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag) {
            tag.setInteger("definition",definition); return tag; }
        public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag) { definition=tag.getInteger("definition"); progress.clear(); claims.clear(); }
        public net.minecraft.nbt.NBTTagCompound writeProgressToNBT(net.minecraft.nbt.NBTTagCompound tag, java.util.List<java.util.UUID> users) {
            net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
            for (java.util.Map.Entry<java.util.UUID,Integer> e: progress.entrySet()) if (users==null || users.contains(e.getKey())) {
                net.minecraft.nbt.NBTTagCompound item = new net.minecraft.nbt.NBTTagCompound(); item.setString("uuid",e.getKey().toString());
                item.setInteger("amount",e.getValue()); item.setInteger("claim",claims.getOrDefault(e.getKey(),0));
                list.appendTag(item); } tag.setTag("users",list); return tag; }
        public void readProgressFromNBT(net.minecraft.nbt.NBTTagCompound tag, boolean merge) {
            if (!merge) { progress.clear(); claims.clear(); } net.minecraft.nbt.NBTTagList list=tag.getTagList("users",10);
            for (int i=0;i<list.tagCount();i++) { net.minecraft.nbt.NBTTagCompound e=list.getCompoundTagAt(i);
                java.util.UUID user=java.util.UUID.fromString(e.getString("uuid"));
                progress.put(user,e.getInteger("amount")); claims.put(user,e.getInteger("claim")); } }
    } }''')
fake("betterquesting.api.questing.IQuestLine", '''public interface IQuestLine {
    net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag, Object ignored);
    void readFromNBT(net.minecraft.nbt.NBTTagCompound tag, boolean merge); }''')
fake("betterquesting.questing.QuestLineDatabase", '''public class QuestLineDatabase extends java.util.LinkedHashMap<java.util.UUID,betterquesting.api.questing.IQuestLine> {
    public static final QuestLineDatabase INSTANCE = new QuestLineDatabase(); public int clears;
    public final java.util.Map<java.util.UUID,Integer> order = new java.util.HashMap<>();
    @Override public void clear() { clears++; super.clear(); order.clear(); }
    public java.util.Map<java.util.UUID,betterquesting.api.questing.IQuestLine> filterKeys(java.util.Collection<java.util.UUID> ids) {
        java.util.Map<java.util.UUID,betterquesting.api.questing.IQuestLine> result = new java.util.LinkedHashMap<>();
        for (java.util.UUID id: ids) if (containsKey(id)) result.put(id,get(id)); return result; }
    public java.util.List<java.util.Map.Entry<java.util.UUID,betterquesting.api.questing.IQuestLine>> getOrderedEntries() {
        return new java.util.ArrayList<>(entrySet()); }
    public void setOrderIndex(java.util.UUID id,int index) { order.put(id,index); }
    public betterquesting.api.questing.IQuestLine createNew(java.util.UUID id) { Chapter c=new Chapter(); put(id,c); return c; }
    public static class Chapter implements betterquesting.api.questing.IQuestLine {
        public int definition;
        public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag,Object ignored) {
            tag.setInteger("definition",definition); return tag; }
        public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag,boolean merge) { definition=tag.getInteger("definition"); }
    } }''')
fake("com.google.common.collect.Lists", '''public class Lists {
    public static <F,T> java.util.List<T> transform(java.util.List<F> input, java.util.function.Function<F,T> fn) {
        java.util.List<T> output = new java.util.ArrayList<>(); for(F item:input) output.add(fn.apply(item)); return output; } }''')
fake("betterquesting.network.PacketTypeRegistry", '''public class PacketTypeRegistry {
    public static final PacketTypeRegistry INSTANCE = new PacketTypeRegistry();
    public void registerClientHandler(net.minecraft.util.ResourceLocation id, java.util.function.Consumer<net.minecraft.nbt.NBTTagCompound> handler) {}
    public void registerServerHandler(net.minecraft.util.ResourceLocation id,
        java.util.function.Consumer<betterquesting.api2.utils.Tuple2<net.minecraft.nbt.NBTTagCompound,net.minecraft.entity.player.EntityPlayerMP>> handler) {} }''')
fake("betterquesting.network.PacketAssembly", '''public class PacketAssembly {
    public static final PacketAssembly INSTANCE = new PacketAssembly();
    public java.util.List<net.minecraft.nbt.NBTTagCompound> splitPacket(net.minecraft.nbt.NBTTagCompound tag) {
        return java.util.Collections.singletonList(tag); } }''')
fake("betterquesting.network.PacketQuesting", '''public class PacketQuesting {
    public final net.minecraft.nbt.NBTTagCompound tag; public PacketQuesting(net.minecraft.nbt.NBTTagCompound tag) { this.tag=tag; } }''')
fake("betterquesting.core.BetterQuesting", '''public class BetterQuesting {
    public static final BetterQuesting instance = new BetterQuesting(); public static final Proxy proxy = new Proxy();
    public final Network network = new Network(); public static class Proxy { public boolean isClient() { return true; } }
    public static class Network {
        public final java.util.List<betterquesting.network.PacketQuesting> delivered = new java.util.ArrayList<>();
        public void sendTo(betterquesting.network.PacketQuesting packet,net.minecraft.entity.player.EntityPlayerMP player) { delivered.add(packet); }
        public void sendToAll(betterquesting.network.PacketQuesting packet) {
            for(net.minecraft.entity.player.EntityPlayerMP p:cpw.mods.fml.common.FMLCommonHandler.instance().server.manager.playerEntityList) sendTo(packet,p); }
        public void sendToServer(betterquesting.network.PacketQuesting packet) { delivered.add(packet); }
        public void sendToAllAround(betterquesting.network.PacketQuesting packet,cpw.mods.fml.common.network.NetworkRegistry.TargetPoint point) { sendToAll(packet); }
        public void sendToDimension(betterquesting.network.PacketQuesting packet,int dimension) { sendToAll(packet); }
    } }''')

native_relative = ["network/handlers/NetQuestSync.java", "network/handlers/NetChapterSync.java",
                   "network/PacketSender.java", "api/network/IPacketSender.java",
                   "api/network/QuestingPacket.java", "api/events/DatabaseEvent.java"]
native_sources = [args.bq_source / "src/main/java/betterquesting" / p for p in native_relative]
# javac requires a matching basename; copy unchanged bytes and retain both source hashes below.
reinjector = output / "upstream/com/hfstudio/bqapi/runtime/BQReinjector.java"
reinjector.parent.mkdir(parents=True)
reinjector.write_bytes(args.bqapi_reinjector_source.read_bytes())
core_base = ROOT / "src/main/java/com/miaokatze/miaokatzecore"
production = [core_base / "bootstrap/CoreLateMixinLoader.java", core_base / "client/bq/SharedQuestDatabase.java",
              core_base / "mixin/bq/MixinNetQuestSync.java", core_base / "mixin/bq/MixinNetChapterSync.java"]
harness = ROOT / "tools/tests/BqSharedSyncRegression.java"
sources = sorted(fake_root.rglob("*.java")) + native_sources + production + [harness, reinjector]
version = run("java-version", [tool("java"), "-version"])
if b'"1.8.' not in version:
    raise SystemExit("Use a real JDK 8 for this receipt.")
argfile = output / "sources.txt"
argfile.write_text("\n".join('"' + p.resolve().as_posix() + '"' for p in sources), encoding="utf-8")
run("compile", [tool("javac"), "-encoding", "UTF-8", "-source", "8", "-target", "8", "-d", str(classes), "@" + str(argfile)])
run("regression", [tool("java"), "-cp", str(classes), "BqSharedSyncRegression"])
source_hashes = {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest()
                 for p in sources + [Path(__file__), args.bqapi_reinjector_source]}
(output / "manifest.json").write_text(json.dumps({"kind": "real-bq-source-chain-with-dependency-fakes",
    "mixin_application": "NOT RUN; HEAD injection invoked reflectively", "game_or_server": "NOT RUN",
    "native_bq_sources": [str(p.resolve()) for p in native_sources], "source_sha256": source_hashes,
    "receipts": receipts}, indent=2), encoding="utf-8")
