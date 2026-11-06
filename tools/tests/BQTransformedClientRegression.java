import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.MinecraftForge;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.utils.NBTConverter.UuidValueType;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestDatabase.Quest;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineDatabase.Chapter;

/** Dependency fakes surround actual Mixin-transformed onClient/callback instruction bodies. */
public final class BQTransformedClientRegression {
    private static int checks;
    private static final UUID ID = new UUID(0, 100), ONLINE = new UUID(0, 1), OFFLINE = new UUID(0, 2);
    public static void main(String[] args) throws Exception {
        for (boolean local : new boolean[] { true, false }) {
            for (boolean merge : new boolean[] { false, true }) quest(local, merge);
            chapter(local);
        }
        // A stale integrated-server flag must still apply the native remote clear.
        connection(true, false);
        QuestDatabase.INSTANCE.clear(); QuestDatabase.INSTANCE.clears = 0;
        invoke("NetQuestSync", new NBTTagCompound());
        check(QuestDatabase.INSTANCE.clears == 1, "stale integrated flag + TCP must not cancel");
        System.out.println("PASS " + checks + " assertions; real transformed onClient and merged callbacks; external dependency fakes; no game");
    }
    private static void quest(boolean local, boolean merge) throws Exception {
        connection(local, local);
        QuestDatabase.INSTANCE.clear(); QuestDatabase.INSTANCE.clears = 0;
        Quest original = new Quest(); original.definition = 3;
        original.progress.put(ONLINE, 7); original.progress.put(OFFLINE, 13);
        QuestDatabase.INSTANCE.put(ID, original);
        NBTTagCompound payload = new NBTTagCompound(), entry = new NBTTagCompound(), config = new NBTTagCompound();
        config.setInteger("definition", 99); entry.setTag("config", config);
        UuidValueType.QUEST.writeId(ID, entry);
        NBTTagList data = new NBTTagList(); data.appendTag(entry);
        payload.setTag("data", data); payload.setBoolean("merge", merge);
        invoke("NetQuestSync", payload);
        Quest result = (Quest) QuestDatabase.INSTANCE.get(ID);
        check(QuestDatabase.INSTANCE.clears == (local || merge ? 0 : 1), "quest clear local=" + local + " merge=" + merge);
        check((result == original) == (local || merge), "quest identity local=" + local + " merge=" + merge);
        check(result.definition == (local ? 3 : 99), "quest config local=" + local + " merge=" + merge);
        check(local ? result.progress.get(ONLINE) == 7 && result.progress.get(OFFLINE) == 13 : result.progress.isEmpty(), "quest progress local=" + local);
        event(DatabaseEvent.DBType.QUEST);
    }
    private static void chapter(boolean local) throws Exception {
        connection(local, local);
        QuestLineDatabase.INSTANCE.clear(); QuestLineDatabase.INSTANCE.clears = 0;
        Chapter original = new Chapter(); original.definition = 5;
        QuestLineDatabase.INSTANCE.put(ID, original);
        NBTTagCompound payload = new NBTTagCompound(), entry = new NBTTagCompound(), config = new NBTTagCompound();
        config.setInteger("definition", 91); entry.setTag("config", config);
        UuidValueType.QUEST_LINE.writeId(ID, entry);
        NBTTagList data = new NBTTagList(); data.appendTag(entry); payload.setTag("data", data);
        invoke("NetChapterSync", payload);
        Chapter result = (Chapter) QuestLineDatabase.INSTANCE.get(ID);
        check(QuestLineDatabase.INSTANCE.clears == (local ? 0 : 1), "chapter clear local=" + local);
        check((result == original) == local && result.definition == (local ? 5 : 91), "chapter definition/identity local=" + local);
        event(DatabaseEvent.DBType.CHAPTER);
    }
    private static void connection(boolean integrated, boolean local) {
        Minecraft.getMinecraft().integrated = integrated;
        Minecraft.getMinecraft().handler = new NetHandlerPlayClient();
        Minecraft.getMinecraft().handler.manager.local = local;
        MinecraftForge.EVENT_BUS.events.clear();
    }
    private static void event(DatabaseEvent.DBType expected) {
        check(MinecraftForge.EVENT_BUS.events.size() == 1 &&
            ((DatabaseEvent) MinecraftForge.EVENT_BUS.events.get(0)).getType() == expected, "exactly one database update " + expected);
    }
    private static void invoke(String handler, NBTTagCompound payload) throws Exception {
        Method method = Class.forName("betterquesting.network.handlers." + handler).getDeclaredMethod("onClient", NBTTagCompound.class);
        method.setAccessible(true); method.invoke(null, payload);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
}
