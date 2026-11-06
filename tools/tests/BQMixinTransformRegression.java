import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.tree.*;

/** Applies the production dynamically selected BQ mixins to the official BQ bytecode, without a game. */
public final class BQMixinTransformRegression {
    public static void main(String[] args) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String path : System.getProperty("java.class.path").split(File.pathSeparator))
            urls.add(new File(path).toURI().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.");
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        Launch.minecraftHome = new File(args[0]);
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Method run = Launch.classLoader.loadClass("BQMixinTransformRuntime").getMethod("run", String.class);
        run.setAccessible(true);
        run.invoke(null, args[0]);
    }
}

class BQMixinTransformRuntime {
    public static void run(String output) throws Exception {
        Class<?> fml = Class.forName("cpw.mods.fml.relauncher.FMLLaunchHandler");
        Field side = fml.getDeclaredField("side");
        side.setAccessible(true);
        side.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
        com.miaokatze.miaokatzecore.bootstrap.CoreLateMixinLoader loader =
            new com.miaokatze.miaokatzecore.bootstrap.CoreLateMixinLoader();
        List<String> selected = loader.getMixins(Collections.singleton("betterquesting"));
        check(selected.size() == 2, "client BQ selection");
        check(loader.getMixins(Collections.<String>emptySet()).isEmpty(), "absent BQ selection");
        side.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        check(loader.getMixins(Collections.singleton("betterquesting")).isEmpty(), "server selection");
        side.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
        Field logSide = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
        logSide.setAccessible(true);
        logSide.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
        MixinBootstrap.init();
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
        env.setSide(MixinEnvironment.Side.CLIENT);
        // Mirror GTNH LateMixinOrchestrationMixin: use the real, empty production config and
        // append the actual loader result to its common mixin list before registration.
        Config config = Config.create(loader.getMixinConfig(), (org.spongepowered.asm.mixin.extensibility.IMixinConfigSource) null);
        Field configField = Config.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object inner = configField.get(config);
        Field mixinsField = inner.getClass().getDeclaredField("mixinClasses");
        mixinsField.setAccessible(true);
        @SuppressWarnings("unchecked") List<String> mixins = (List<String>) mixinsField.get(inner);
        mixins.addAll(selected);
        Method register = Mixins.class.getDeclaredMethod("registerConfiguration", Config.class);
        register.setAccessible(true);
        register.invoke(null, config);
        Object transformer = env.getActiveTransformer();
        if (transformer == null) {
            Constructor<?> ctor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
            ctor.setAccessible(true);
            transformer = ctor.newInstance();
        }
        Method transform = transformer.getClass().getMethod("transformClassBytes", String.class, String.class, byte[].class);
        transform.setAccessible(true);
        for (String simple : Arrays.asList("NetQuestSync", "NetChapterSync")) {
            String name = "betterquesting.network.handlers." + simple;
            byte[] original = Launch.classLoader.getClassBytes(name);
            byte[] transformed = (byte[]) transform.invoke(transformer, name, name, original);
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            MethodNode onClient = null;
            int callbackCalls = 0, cancelChecks = 0;
            for (MethodNode method : node.methods) if (method.name.equals("onClient")) onClient = method;
            check(onClient != null && onClient.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V"), "onClient signature");
            for (AbstractInsnNode insn : onClient.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.name.contains("core$keepShared")) callbackCalls++;
                if (call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && call.name.equals("isCancelled")) cancelChecks++;
            }
            check(callbackCalls == 1 && cancelChecks == 1, "injection missing from " + name);
            Path destination = Paths.get(output, "transformed", name.replace('.', '/') + ".class");
            Files.createDirectories(destination.getParent());
            Files.write(destination, transformed);
            // Execute the exact transformed onClient and merged callbacks in a second JVM with
            // dependency fakes. Remove unrelated networking methods/clinit only, preserving every
            // instruction and frame in the methods under test.
            node.methods.removeIf(method -> !method.name.equals("onClient") && !method.name.contains("core$keepShared"));
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            Path executable = Paths.get(output, "isolated", name.replace('.', '/') + ".class");
            Files.createDirectories(executable.getParent());
            Files.write(executable, writer.toByteArray());
            System.out.println("PASS actual Mixin transform " + name + " bytes=" + original.length + "->" + transformed.length
                + " callbackCalls=" + callbackCalls + " cancelChecks=" + cancelChecks);
        }
        System.out.println("PASS dynamic loader client=2 missing-mod=0 server=0");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
