package com.miaokatze.miaokatzecore.api.testlab;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.miaokatzecore.testlab.LabWorldPolicy;
import com.miaokatze.miaokatzecore.testlab.MiaoTestCommand;

import cpw.mods.fml.common.eventhandler.Event;

/** Real Forge event objects and actual temporary jars, without a server, player or graphics context. */
public final class TestlabContracts {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        registryAndEvents();
        environment();
        command();
        artifact();
        LabRegistry.clear();
        System.out.println(
            "TestlabContracts passed: metadata, bounded events, exact-world spawn isolation, fixture scope, OP2/request/receipt, loaded-jar identity");
    }

    private static void registryAndEvents() {
        LabRegistry.clear();
        Set<String> tags = new LinkedHashSet<String>();
        tags.add("server");
        LabCase task = new LabCase("case", "Case", "server", tags, true, false);
        tags.clear();
        check(
            task.tags()
                .contains("server"),
            "metadata defensively copied");
        try {
            task.tags()
                .add("changed");
            throw new AssertionError("tags mutable");
        } catch (UnsupportedOperationException expected) {}
        Adapter adapter = new Adapter();
        adapter.enabled = false;
        LabRegistry.register(adapter);
        check(
            LabRegistry.modules()
                .isEmpty(),
            "disabled adapters are not retained");
        LabEvents.publish("contract", "run", "task", "run.started", "", null, "pending");
        check(
            LabEvents.snapshot("contract")
                .isEmpty(),
            "disabled observations discarded");
        adapter.enabled = true;
        LabRegistry.register(adapter);
        for (int i = 0; i < 300; i++)
            LabEvents.publish("contract", "run", "task", "check", "check" + i, LabResult.PASS, "pending");
        check(
            LabEvents.snapshot("contract")
                .size() == 256,
            "bounded ring");
        check(
            LabEvents.snapshot("contract")
                .get(0)
                .check()
                .equals("check44"),
            "oldest evicted");
        adapter.enabled = false;
        LabEvents.publish("contract", "run", "task", "check", "discard", null, "unknown");
        check(
            LabEvents.snapshot("contract")
                .isEmpty(),
            "disabled module data released");
        LabRegistry.clear();
    }

    private static void environment() throws Exception {
        // Allocate inert worlds without invoking Minecraft setup; exercise actual event predicates only.
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        World owned = (World) unsafe.allocateInstance(InertWorld.class);
        World other = (World) unsafe.allocateInstance(InertWorld.class);
        java.lang.reflect.Field provider = World.class.getDeclaredField("provider");
        provider.setAccessible(true);
        net.minecraft.world.WorldProvider ownedProvider = (net.minecraft.world.WorldProvider) unsafe
            .allocateInstance(net.minecraft.world.WorldProviderSurface.class);
        ownedProvider.dimensionId = 12345;
        provider.set(owned, ownedProvider);
        net.minecraft.world.WorldProvider otherProvider = (net.minecraft.world.WorldProvider) unsafe
            .allocateInstance(net.minecraft.world.WorldProviderSurface.class);
        otherProvider.dimensionId = 12346;
        provider.set(other, otherProvider);
        owned.loadedEntityList = new ArrayList<net.minecraft.entity.Entity>();
        other.loadedEntityList = new ArrayList<net.minecraft.entity.Entity>();
        Adapter adapter = new Adapter();
        adapter.world = owned;
        LabRegistry.register(adapter);
        LabWorldPolicy policy = new LabWorldPolicy();
        owned.rainingStrength = 0.8F;
        owned.thunderingStrength = 0.8F;
        other.rainingStrength = 0.7F;
        other.thunderingStrength = 0.7F;
        owned.difficultySetting = net.minecraft.world.EnumDifficulty.HARD;
        policy.worldTick(
            new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(
                cpw.mods.fml.relauncher.Side.SERVER,
                cpw.mods.fml.common.gameevent.TickEvent.Phase.END,
                owned));
        policy.worldTick(
            new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(
                cpw.mods.fml.relauncher.Side.SERVER,
                cpw.mods.fml.common.gameevent.TickEvent.Phase.END,
                other));
        check(
            owned.rainingStrength == 0.0F && owned.thunderingStrength == 0.0F,
            "owned weather strengths cleared without WorldInfo");
        check(other.rainingStrength == 0.7F && other.thunderingStrength == 0.7F, "foreign weather preserved");
        check(owned.difficultySetting == net.minecraft.world.EnumDifficulty.HARD, "survival difficulty preserved");
        WorldEvent.PotentialSpawns natural = spawns(owned);
        policy.potentialSpawns(natural);
        check(natural.isCanceled(), "natural spawn denied in exact owned world");
        WorldEvent.PotentialSpawns foreign = spawns(other);
        policy.potentialSpawns(foreign);
        check(!foreign.isCanceled(), "other world unaffected even without distinct dimension ID");
        LivingSpawnEvent.CheckSpawn checkSpawn = new LivingSpawnEvent.CheckSpawn(null, owned, 0, 0, 0);
        policy.checkSpawn(checkSpawn);
        check(checkSpawn.getResult() == Event.Result.DENY, "CheckSpawn denied");
        LabWorldPolicy.withFixtureSpawns(() -> {
            WorldEvent.PotentialSpawns explicit = spawns(owned);
            policy.potentialSpawns(explicit);
            check(!explicit.isCanceled(), "explicit fixture scope permitted");
            LivingSpawnEvent.CheckSpawn fixture = new LivingSpawnEvent.CheckSpawn(null, owned, 0, 0, 0);
            policy.checkSpawn(fixture);
            check(fixture.getResult() == Event.Result.DEFAULT, "fixture CheckSpawn preserved");
        });
        try {
            LabWorldPolicy.withFixtureSpawns(() -> { throw new IllegalStateException("fixture"); });
        } catch (IllegalStateException expected) {}
        WorldEvent.PotentialSpawns after = spawns(owned);
        policy.potentialSpawns(after);
        check(after.isCanceled(), "fixture allowance released after failure");
        net.minecraft.entity.Entity old = (net.minecraft.entity.Entity) unsafe
            .allocateInstance(net.minecraft.entity.monster.EntityZombie.class);
        net.minecraft.entity.Entity explicitMob = (net.minecraft.entity.Entity) unsafe
            .allocateInstance(net.minecraft.entity.monster.EntityZombie.class);
        net.minecraft.entity.Entity foreignMob = (net.minecraft.entity.Entity) unsafe
            .allocateInstance(net.minecraft.entity.monster.EntityZombie.class);
        net.minecraftforge.event.entity.EntityJoinWorldEvent join = join(old, owned);
        policy.entityJoin(join);
        check(join.isCanceled(), "ambient hostile join refused");
        net.minecraftforge.event.entity.EntityJoinWorldEvent explicitJoin = join(explicitMob, owned);
        LabWorldPolicy.withFixtureSpawns(() -> policy.entityJoin(explicitJoin));
        check(!explicitJoin.isCanceled(), "explicit hostile fixture allowed and marked");
        owned.loadedEntityList.add(old);
        owned.loadedEntityList.add(explicitMob);
        other.loadedEntityList.add(foreignMob);
        policy.worldTick(
            new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(
                cpw.mods.fml.relauncher.Side.SERVER,
                cpw.mods.fml.common.gameevent.TickEvent.Phase.END,
                owned));
        policy.worldTick(
            new cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent(
                cpw.mods.fml.relauncher.Side.SERVER,
                cpw.mods.fml.common.gameevent.TickEvent.Phase.END,
                other));
        check(old.isDead && !explicitMob.isDead && !foreignMob.isDead, "only ambient owned-world remnant removed");
        net.minecraftforge.event.entity.EntityJoinWorldEvent foreignJoin = join(foreignMob, other);
        policy.entityJoin(foreignJoin);
        check(!foreignJoin.isCanceled(), "foreign join preserved");
        adapter.enabled = false;
        net.minecraftforge.event.entity.EntityJoinWorldEvent disabledJoin = join(foreignMob, owned);
        policy.entityJoin(disabledJoin);
        check(!disabledJoin.isCanceled(), "disabled owner preserves hostile join");
        WorldEvent.PotentialSpawns disabled = spawns(owned);
        policy.potentialSpawns(disabled);
        check(!disabled.isCanceled(), "disabled owner cannot change world decisions");
        LabRegistry.clear();
    }

    private static net.minecraftforge.event.entity.EntityJoinWorldEvent join(net.minecraft.entity.Entity entity,
        World world) {
        return new net.minecraftforge.event.entity.EntityJoinWorldEvent(entity, world) {

            public boolean isCancelable() {
                return true;
            }
        };
    }

    private static WorldEvent.PotentialSpawns spawns(World world) {
        // Forge's launch transformer generates this annotation-derived method in a real game.
        check(
            WorldEvent.PotentialSpawns.class.isAnnotationPresent(cpw.mods.fml.common.eventhandler.Cancelable.class),
            "Forge event is cancellable");
        return new WorldEvent.PotentialSpawns(world, EnumCreatureType.monster, 0, 0, 0, Collections.emptyList()) {

            public boolean isCancelable() {
                return true;
            }
        };
    }

    private static void command() throws Exception {
        Adapter adapter = new Adapter();
        LabRegistry.register(adapter);
        Path directory = Files.createTempDirectory("miaotest-command-contract");
        MiaoTestCommand command = new MiaoTestCommand(directory);
        Sender op1 = new Sender(1);
        try {
            command.processCommand(op1, new String[] { "contract", "run" });
            throw new AssertionError("OP1 accepted");
        } catch (net.minecraft.command.CommandException expected) {}
        Sender op2 = new Sender(2);
        String request = UUID.randomUUID()
            .toString();
        command.processCommand(op2, new String[] { "contract", "RUN", "case", "--request", request });
        check(
            Arrays.equals(adapter.args, new String[] { "run", "case" }),
            "normalized action and stripped request option");
        check(request.equals(adapter.request), "request correlation reaches module");
        JsonObject response = new JsonParser().parse(op2.last.substring("[MIAO-TESTLAB] ".length()))
            .getAsJsonObject();
        check(
            response.get("accepted")
                .getAsBoolean(),
            "dispatch acknowledged");
        check(
            response.getAsJsonObject("snapshot")
                .get("state")
                .getAsString()
                .equals("RUNNING"),
            "acceptance preserves unfinished state");
        check(Files.isRegularFile(directory.resolve(request + ".json")), "atomic machine receipt exists");
        Files.delete(directory.resolve(request + ".json"));
        Files.delete(directory);
        LabRegistry.clear();
    }

    private static void artifact() throws Exception {
        check(
            ArtifactIdentity.capture(TestlabContracts.class)
                .asMap()
                .get("jarSha256")
                .equals("unknown"),
            "classes directory never guessed as jar");
        Path jarPath = Files.createTempFile("miaotest-artifact-contract", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes()
            .putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes()
            .putValue("Miaokatze-Source-Commit", "contract-commit");
        manifest.getMainAttributes()
            .putValue("Miaokatze-Source-Dirty", "true");
        String resource = ArtifactIdentity.class.getName()
            .replace('.', '/') + ".class";
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest);
            InputStream input = ArtifactIdentity.class.getClassLoader()
                .getResourceAsStream(resource)) {
            jar.putNextEntry(new JarEntry(resource));
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) jar.write(buffer, 0, count);
            jar.closeEntry();
        }
        try (URLClassLoader loader = new URLClassLoader(
            new java.net.URL[] { jarPath.toUri()
                .toURL() },
            null)) {
            Class<?> anchor = Class.forName(ArtifactIdentity.class.getName(), true, loader);
            Object identity = anchor.getMethod("capture", Class.class)
                .invoke(null, anchor);
            Map<?, ?> values = (Map<?, ?>) anchor.getMethod("asMap")
                .invoke(identity);
            StringBuilder expected = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jarPath))) expected.append(String.format(Locale.ROOT, "%02x", b & 255));
            check(
                expected.toString()
                    .equals(values.get("jarSha256")),
                "SHA binds actual loaded jar bytes");
            check(
                values.get("sourceCommit")
                    .equals("contract-commit"),
                "commit read from loaded jar manifest");
            check(
                values.get("sourceDirty")
                    .equals("true"),
                "dirty marker retained");
        }
        Files.delete(jarPath);
    }

    private static final class Adapter implements LabModule {

        boolean enabled = true;
        World world;
        String[] args;
        String request;

        public String id() {
            return "contract";
        }

        public boolean enabled() {
            return enabled;
        }

        public List<LabCase> cases() {
            return Collections.emptyList();
        }

        public void execute(ICommandSender sender, String[] args) {
            this.args = args;
        }

        public void execute(ICommandSender sender, String[] args, String request) {
            this.args = args;
            this.request = request;
        }

        public String status() {
            return "RUNNING";
        }

        public Map<String, Object> snapshot() {
            return Collections.<String, Object>singletonMap("state", "RUNNING");
        }

        public boolean ownsWorld(World world) {
            return this.world == world;
        }
    }

    private static final class Sender implements ICommandSender {

        final int permission;
        String last;

        Sender(int permission) {
            this.permission = permission;
        }

        public String getCommandSenderName() {
            return "contract";
        }

        public IChatComponent func_145748_c_() {
            return new ChatComponentText("contract");
        }

        public void addChatMessage(IChatComponent message) {
            last = message.getUnformattedText();
        }

        public boolean canCommandSenderUseCommand(int permission, String command) {
            return this.permission >= permission;
        }

        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        public World getEntityWorld() {
            return null;
        }
    }

    private static final class InertWorld extends World {

        private InertWorld() {
            super(
                null,
                "contract",
                (net.minecraft.world.WorldProvider) null,
                (net.minecraft.world.WorldSettings) null,
                null);
        }

        protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() {
            return null;
        }

        protected int func_152379_p() {
            return 0;
        }

        public net.minecraft.entity.Entity getEntityByID(int id) {
            return null;
        }
    }
}
