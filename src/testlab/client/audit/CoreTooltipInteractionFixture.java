package audit;

import static audit.Mui2ClientQa.*;

import java.awt.geom.Rectangle2D;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** Opt-in client-only source compiled by the registered native QA runner, never into a runtime Core jar. */
public final class CoreTooltipInteractionFixture {
    public interface Hooks {
        void complete(Map<String, Object> receipt, Throwable failure);
        void screenshot(String name);
    }

    private static final String CONTROL = "com.miaokatze.miaokatzecore.client.tooltip.TooltipController";
    private static final String PIN = "com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning";
    private static final String PAGE = "com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage";
    private final Hooks hooks;
    private final JsonObject expectedArtifacts;
    private final Path save, output;
    private final Map<String, Object> receipt = new LinkedHashMap<>();
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private final Object[] original = new Object[36];
    private volatile Object inventory;
    private volatile boolean ready, restored, cancel, paperMoved;
    private Throwable failure;
    private Object mc, gui, stick, paper, lastContext, pinBinding;
    private int phase, frames, ticks, phaseTicks, x, y, oldX, oldY, pinCode, oldScale;
    private int expectedVariant;
    private boolean opened, finished, pointerOwned, savedSettings, links, prompt;

    public CoreTooltipInteractionFixture(Path planPath, Hooks hooks) throws Exception {
        this.hooks = hooks;
        JsonObject plan = new JsonParser().parse(new String(Files.readAllBytes(planPath), StandardCharsets.UTF_8)).getAsJsonObject();
        require("core-tooltip-interaction-v1".equals(plan.get("scriptOnly").getAsString()), "Explicit native profile required");
        expectedArtifacts = plan.getAsJsonObject("expectedArtifacts");
        save = Paths.get(System.getProperty("miaotest.host.worldPath")).toRealPath();
        output = Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();
        JsonObject owner = new JsonParser().parse(new String(Files.readAllBytes(save.resolve(".miaotest-host-owner.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        require(System.getProperty("miaotest.host.runId").equals(owner.get("runId").getAsString()), "Owned save marker");
        receipt.put("runId", System.getProperty("miaotest.host.runId"));
        receipt.put("checks", checks);
        receipt.put("cleanupPending", true);
        receipt.put("input", "Real GuiInventory/NEI; scoped lwjgl3ify event snapshots and native GUI fallback; no OS input");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private static void require(boolean valid, String detail) { if (!valid) throw new IllegalStateException(detail); }
    private void check(String name, boolean valid, Object detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name); item.put("pass", valid); item.put("detail", detail); checks.add(item);
        System.out.println("CORE_TOOLTIP_QA " + new Gson().toJson(item));
        require(valid, name + ": " + detail);
    }
    public void cancel(Throwable cause) {
        while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) cause = cause.getCause();
        if (failure == null) failure = cause;
        cancel = true;
    }
    public boolean completeForExit() { return finished; }
    public void serverStopping(Object server) { if (!restored) cancel(new IllegalStateException("Inventory recovery incomplete")); }
    public void render(boolean start) {}

    public void serverTick(Object server) {
        try {
            if (cancel) {
                if (inventory != null) {
                    for (int i = 0; i < original.length; i++) call(inventory, "setInventorySlotContents|func_70299_a", i, original[i]);
                    call(inventory, "markDirty|func_70296_d");
                }
                restored = true;
                return;
            }
            if (ready) {
                if (phase >= 2) {
                    boolean sourceEmpty = call(inventory, "getStackInSlot|func_70301_a", 1) == null;
                    boolean found = false;
                    for (int i = 9; i < 36; i++) {
                        Object stack = call(inventory, "getStackInSlot|func_70301_a", i);
                        if (stack != null && call(stack, "getItem|func_77973_b") == call(paper, "getItem|func_77973_b")) found = true;
                    }
                    paperMoved = sourceEmpty && found;
                }
                return;
            }
            List<?> actors = (List<?>) field(call(server, "getConfigurationManager|func_71203_ab"), "playerEntityList|field_72404_b");
            if (actors.isEmpty()) return;
            require(actors.size() == 1, "Single owned actor");
            Object actor = actors.get(0), world = field(actor, "worldObj|field_70170_p");
            Path actual = ((java.io.File) call(call(world, "getSaveHandler|func_72860_G"), "getWorldDirectory|func_75765_b")).toPath().toRealPath();
            require(save.equals(actual), "Exact owned save");
            Map<?, ?> mods = (Map<?, ?>) call(call(type("cpw.mods.fml.common.Loader"), "instance"), "getIndexedModList");
            for (Map.Entry<String, com.google.gson.JsonElement> expected : expectedArtifacts.entrySet()) {
                Object container = mods.get(expected.getKey());
                require(container != null, "Missing loaded mod " + expected.getKey());
                Path jar = ((java.io.File) call(container, "getSource")).toPath();
                StringBuilder actualHash = new StringBuilder();
                for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)))
                    actualHash.append(String.format("%02x", value & 255));
                check("artifact." + expected.getKey(), actualHash.toString().equals(expected.getValue().getAsString()), jar.toString());
            }
            receipt.put("coreCodeSource", call(call(type("com.miaokatze.miaokatzecore.api.testlab.ArtifactIdentity"), "capture", type(CONTROL)), "asMap"));
            inventory = field(actor, "inventory|field_71071_by");
            for (int i = 0; i < original.length; i++) {
                Object stack = call(inventory, "getStackInSlot|func_70301_a", i);
                original[i] = stack == null ? null : call(stack, "copy|func_77946_l");
            }
            stick = stack("stick|field_151055_y");
            paper = stack("paper|field_151121_aF");
            call(inventory, "setInventorySlotContents|func_70299_a", 0, stick);
            call(inventory, "setInventorySlotContents|func_70299_a", 1, paper);
            call(inventory, "markDirty|func_70296_d");
            ready = true;
        } catch (Throwable cause) { cancel(cause); }
    }

    private Object stack(String name) throws Exception {
        return type("net.minecraft.item.ItemStack").getConstructor(type("net.minecraft.item.Item")).newInstance(field(type("net.minecraft.init.Items"), name));
    }

    @SubscribeEvent public void tooltip(ItemTooltipEvent event) {
        try {
            Object value = field(event, "itemStack");
            if (call(value, "getItem|func_77973_b") != field(type("net.minecraft.init.Items"), "stick|field_151055_y")) return;
            Object page = call(type(PAGE), "forItem", value);
            Object table = call(type("com.miaokatze.miaokatzecore.api.tooltip.component.TooltipTable"), "of", "Native Core fixture",
                Arrays.asList("Resource", "Amount"), Arrays.asList(Arrays.asList("iron", "1"), Arrays.asList("copper", "2")));
            Object art = Enum.valueOf((Class) type("com.miaokatze.miaokatzecore.api.tooltip.TooltipArt"), "STEAM");
            table = call(table, "withArt", art);
            int variant = ((Number) call(page, "variant", "qa.route", 2)).intValue();
            Object chart = call(type("com.miaokatze.miaokatzecore.api.tooltip.component.TooltipChart"), "bars", "Native chart",
                table, new double[] { 1 + variant, 2 + variant }, null, 4.0);
            call(page, "chart", "qa.route", Arrays.asList("A", "B"), chart);
            call(page, "input", "Ingredient [item:minecraft:iron_ingot|Iron]");
            call(page, "line", "https://miaokatze.com/");
            for (int i = 0; i < 5; i++) call(page, "line", "Ordinary description row " + i);
            call(page, "appendTo", value, field(event, "toolTip"));
        } catch (Throwable cause) { cancel(cause); }
    }

    public void clientTick(Object client) throws Exception {
        mc = client;
        if (finished) return;
        if (!opened) {
            require(field(mc, "theWorld|field_71441_e") == null, "Refuse existing world");
            Object survival = Enum.valueOf((Class) type("net.minecraft.world.WorldSettings$GameType"), "SURVIVAL");
            Object settings = type("net.minecraft.world.WorldSettings").getConstructor(long.class,
                type("net.minecraft.world.WorldSettings$GameType"), boolean.class, boolean.class, type("net.minecraft.world.WorldType"))
                .newInstance(20261128L, survival, false, false, field(type("net.minecraft.world.WorldType"), "FLAT|field_77138_c"));
            call(mc, "launchIntegratedServer|func_71371_a", save.getFileName().toString(), "Owned Core Tooltip", settings);
            opened = true;
            return;
        }
        if (cancel) {
            restorePointer();
            if (!restored) return;
            if (pinBinding != null) call(pinBinding, "setKeyCode|func_151462_b", pinCode);
            if (savedSettings) {
                Object settings = field(mc, "gameSettings|field_71474_y");
                set(settings, "chatLinks|field_74359_p", links); set(settings, "chatLinksPrompt|field_74358_q", prompt);
                set(settings, "guiScale|field_74335_Z", oldScale);
            }
            receipt.put("inventoryRestored", true); receipt.put("cleanupPending", false);
            receipt.put("status", failure == null ? "PASS" : "FAIL");
            if (failure != null) {
                receipt.put("failure", failure.toString());
                List<String> trace = new ArrayList<>();
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    trace.add(cause.toString()); for (StackTraceElement at : cause.getStackTrace()) trace.add(at.toString());
                }
                receipt.put("stack", trace);
            }
            Files.write(output.resolve("core-tooltip-interaction.json"), new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));
            finished = true; net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this); hooks.complete(receipt, failure); return;
        }
        if (++ticks > 3000) { cancel(new IllegalStateException("Tooltip phase deadline " + phase)); return; }
        Object player = field(mc, "thePlayer|field_71439_g");
        if (!ready || player == null || ((Number) field(player, "ticksExisted|field_70173_aa")).intValue() < 60) return;
        if (gui == null) {
            gui = type("net.minecraft.client.gui.inventory.GuiInventory").getConstructor(type("net.minecraft.entity.player.EntityPlayer")).newInstance(player);
            Object settings = field(mc, "gameSettings|field_71474_y");
            links = Boolean.TRUE.equals(field(settings, "chatLinks|field_74359_p")); prompt = Boolean.TRUE.equals(field(settings, "chatLinksPrompt|field_74358_q"));
            oldScale = ((Number) field(settings, "guiScale|field_74335_Z")).intValue(); savedSettings = true;
            call(mc, "displayGuiScreen|func_147108_a", gui); return;
        }
        Object current = field(mc, "currentScreen|field_71462_r");
        phaseTicks++;
        if (phase == 7 && phaseTicks > 8) {
            check("url.native-confirmation", type("net.minecraft.client.gui.GuiConfirmOpenLink").isInstance(current), current.getClass().getName());
            check("url.transition-clears-fixed", !pinned(), "C session ends with GUI");
            call(current, "keyTyped|func_73869_a", (char) 0, 1);
            check("url.escape-restores-parent", field(mc, "currentScreen|field_71462_r") == gui, "Native callback");
            next(8);
        } else if (phase == 10 && phaseTicks > 8) {
            check("material.actual-NEI-screen", current != null && current.getClass().getName().contains("GuiCraftingRecipe"), current == null ? "null" : current.getClass().getName());
            check("material.transition-clears-fixed", !pinned(), "NEI owns new GUI");
            call(mc, "displayGuiScreen|func_147108_a", gui); next(11);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void before(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (cancel || gui == null || event.gui != gui) return;
        try {
            restorePointer();
            x = ((Number) field(gui, "guiLeft|field_147003_i")).intValue() + 16;
            y = ((Number) field(gui, "guiTop|field_147009_r")).intValue() + 150;
            set(event, "mouseX", x); set(event, "mouseY", y);
            oldX = ((Number) field(type("org.lwjglx.input.Mouse"), "x")).intValue();
            oldY = ((Number) field(type("org.lwjglx.input.Mouse"), "y")).intValue();
            move(x, y); pointerOwned = true;
        } catch (Throwable cause) { cancel(cause); }
    }

    @SubscribeEvent public void painted(cpw.mods.fml.common.eventhandler.Event event) {
        if (cancel || !event.getClass().getName().equals("com.slprime.chromatictooltips.event.RenderTooltipEvent")) return;
        try {
            Object context = field(event, "context"), value = call(context, "getItem");
            if (value != null && call(value, "getItem|func_77973_b") == field(type("net.minecraft.init.Items"), "stick|field_151055_y")) lastContext = context;
        } catch (Throwable cause) { cancel(cause); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void after(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (cancel || gui == null || event.gui != gui) return;
        try { if (++frames >= 8 && lastContext != null) step(); restorePointer(); }
        catch (Throwable cause) { cancel(cause); }
    }

    private void step() throws Exception {
        switch (phase) {
            case 0:
                check("ordinary.not-fixed", !pinned(), "Natural GuiInventory item hover");
                check("controls.public-section", call(lastContext, "getSection", "miaokatzecore-controls") != null, "Actual context");
                check("ordinary.V-consumed", key(47), "Keyboard reading remains available");
                check("ordinary.V-changed", variant() == 1, variant()); next(1); break;
            case 1:
                hooks.screenshot("ordinary-large-tooltip");
                check("ordinary.shift-left-passes", !mouse(0, true, x + 18, y, true, true), "Real GUI fallback dispatched");
                check("ordinary.release-passes", !mouse(0, false, x + 18, y, false, true), "GUI owns release"); next(2); break;
            case 2:
                if (!paperMoved) return;
                check("ordinary.actual-server-shift-transfer", paperMoved, "Paper moved from hotbar to main inventory on owned server");
                check("ordinary.C-fixed", key(46) && pinned(), "Registered C binding"); next(3); break;
            case 3: {
                hooks.screenshot("fixed-chart");
                List<Object> hits = hits("NativeChartComponent");
                require(hits.size() >= 2, "Actually drawn chart label hits");
                int before = variant(); Object hit = hits.get(before == 0 ? hits.size() - 1 : 0);
                check("fixed.label-press", click(hit, true), "Accepted chart target");
                check("fixed.release-after-invalidation", click(hit, false), "Release survives geometry refresh");
                expectedVariant = 1 - before;
                check("fixed.label-state-changed", variant() == expectedVariant, variant()); next(4); break;
            }
            case 4:
                pinBinding = binding("miaokatzecore.keys.pin"); pinCode = ((Number) call(pinBinding, "getKeyCode|func_151463_i")).intValue();
                call(pinBinding, "setKeyCode|func_151462_b", -96);
                check("rebind.mouse-command", mouse(4, true, x, y, false, false) && !pinned(), "Button 5 replaces C");
                check("rebind.mouse-release", mouse(4, false, x, y, false, false), "Same accepted press");
                call(pinBinding, "setKeyCode|func_151462_b", pinCode); next(5); break;
            case 5: check("C.reopen", key(46) && pinned(), "Ordinary frame can start another session"); next(6); break;
            case 6:
                Object settings = field(mc, "gameSettings|field_71474_y"); set(settings, "chatLinks|field_74359_p", true); set(settings, "chatLinksPrompt|field_74358_q", true);
                check("url.hit", click(hits("NativeLinkComponent").get(0), true), "Actual URL region"); next(7); break;
            case 8:
                check("return.no-old-pin", !pinned(), "Returning does not revive old fixed window");
                check("material.C-reopen", key(46) && pinned(), "Fresh frame"); next(9); break;
            case 9:
                Object material = materialHit(); Rectangle2D bounds = (Rectangle2D) field(material, "bounds");
                move((int) bounds.getCenterX(), (int) bounds.getCenterY());
                int recipeKey = ((Number) call(type("codechicken.nei.KeyManager"), "getKeyCode", "recipe.recipe")).intValue();
                check("material.R-query", key(recipeKey), "Actual fixed ingredient target"); next(10); break;
            case 11:
                check("scale.C-reopen", key(46) && pinned(), "Prepare resize");
                set(field(mc, "gameSettings|field_71474_y"), "guiScale|field_74335_Z", oldScale == 2 ? 1 : 2);
                Object resolution = type("net.minecraft.client.gui.ScaledResolution").getConstructor(type("net.minecraft.client.Minecraft"), int.class, int.class)
                    .newInstance(mc, field(mc, "displayWidth|field_71443_c"), field(mc, "displayHeight|field_71440_d"));
                call(gui, "setWorldAndResolution|func_146280_a", mc, call(resolution, "getScaledWidth|func_78326_a"), call(resolution, "getScaledHeight|func_78328_b")); next(12); break;
            case 12:
                check("scale.actual-context", ((Number) call(lastContext, "getScaleFactor")).intValue() == ((Number) call(type("com.slprime.chromatictooltips.util.TooltipUtils"), "getTooltipScale")).intValue(), "Rebuilt scale");
                hooks.screenshot("fixed-rescaled"); call(mc, "refreshResources|func_110436_a"); next(13); break;
            case 13:
                check("reload.clears-fixed", !pinned(), "Standard resource callback");
                check("reload.controls-return", call(lastContext, "getSection", "miaokatzecore-controls") != null, "Fresh public section");
                check("render.GL-error", ((Number) call(type("org.lwjgl.opengl.GL11"), "glGetError")).intValue() == 0, "Observed after actual GUI rendering");
                hooks.screenshot("ordinary-after-reload"); cancel(null); break;
            default: break;
        }
    }

    private void next(int value) { phase = value; frames = 0; phaseTicks = 0; }
    private boolean pinned() throws Exception { return Boolean.TRUE.equals(call(type(CONTROL), "pinned")); }
    private int variant() throws Exception { return ((Number) call(call(type(PAGE), "forItem", stick), "variant", "qa.route", 2)).intValue(); }
    private Object binding(String name) throws Exception {
        for (Object binding : (Object[]) field(field(mc, "gameSettings|field_71474_y"), "keyBindings|field_74324_K"))
            if (name.equals(call(binding, "getKeyDescription|func_151464_g"))) return binding;
        throw new IllegalStateException("Missing binding " + name);
    }
    private List<Object> hits(String owner) throws Exception {
        List<Object> result = new ArrayList<>();
        Object root = call(type(PIN), "context");
        for (Object window : (List<?>) field(type("com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipMouse"), "painted")) {
            if (field(window, "context") != root) continue;
            for (Object hit : (List<?>) field(window, "hits")) {
                Object action = field(hit, "action");
                if (action != null && action.getClass().getName().contains(owner)) result.add(hit);
            }
        }
        require(!result.isEmpty(), "Actual drawn hits: " + owner); return result;
    }
    private Object materialHit() throws Exception {
        Object root = call(type(PIN), "context");
        for (Object window : (List<?>) field(type("com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipMouse"), "painted")) {
            if (field(window, "context") != root) continue;
            for (Object hit : (List<?>) field(window, "hits")) if (field(hit, "material") != null) return hit;
        }
        throw new IllegalStateException("No rendered material");
    }
    private boolean click(Object hit, boolean down) throws Exception {
        Rectangle2D bounds = (Rectangle2D) field(hit, "bounds");
        return mouse(0, down, (int) bounds.getCenterX(), (int) bounds.getCenterY(), false, false);
    }
    private void move(int mx, int my) throws Exception {
        int width = ((Number) field(gui, "width|field_146294_l")).intValue(), height = ((Number) field(gui, "height|field_146295_m")).intValue();
        set(type("org.lwjglx.input.Mouse"), "x", mx * ((Number) field(mc, "displayWidth|field_71443_c")).intValue() / width);
        set(type("org.lwjglx.input.Mouse"), "y", (height - my - 1) * ((Number) field(mc, "displayHeight|field_71440_d")).intValue() / height);
    }
    private void restorePointer() throws Exception {
        if (pointerOwned) { set(type("org.lwjglx.input.Mouse"), "x", oldX); set(type("org.lwjglx.input.Mouse"), "y", oldY); pointerOwned = false; }
    }
    private boolean mouse(int button, boolean down, int mx, int my, boolean shift, boolean nativeFallback) throws Exception {
        move(mx, my);
        Class<?> mouse = type("org.lwjglx.input.Mouse"), keyboard = type("org.lwjglx.input.Keyboard");
        int at = ((Number) call(field(mouse, "queue"), "getCurrentPos")).intValue();
        String[] names = { "buttonEvents", "buttonEventStates", "xEvents", "yEvents" };
        Object[] arrays = new Object[4], old = new Object[4];
        Object[] values = { button, down, field(mouse, "x"), field(mouse, "y") };
        ByteBuffer keys = (ByteBuffer) field(keyboard, "sdlKeyPressedArray");
        int scancode = ((Number) call(type("org.lwjglx.input.KeyCodes"), "lwjglToSdlScancode", 42)).intValue();
        byte prior = keys.get(scancode);
        try {
            keys.put(scancode, (byte) (shift ? 1 : 0));
            for (int i = 0; i < names.length; i++) { arrays[i] = field(mouse, names[i]); old[i] = java.lang.reflect.Array.get(arrays[i], at); java.lang.reflect.Array.set(arrays[i], at, values[i]); }
            Object event = type("com.cleanroommc.modularui.api.event.MouseInputEvent$Pre").getConstructor(type("net.minecraft.client.gui.GuiScreen")).newInstance(gui);
            call(net.minecraftforge.common.MinecraftForge.EVENT_BUS, "post", event);
            boolean canceled = Boolean.TRUE.equals(call(event, "isCanceled"));
            if (!canceled && nativeFallback) call(gui, "handleMouseInput|func_146274_d");
            return canceled;
        } finally {
            keys.put(scancode, prior);
            for (int i = 0; i < arrays.length; i++) if (arrays[i] != null) java.lang.reflect.Array.set(arrays[i], at, old[i]);
        }
    }
    private boolean key(int code) throws Exception {
        Class<?> keyboard = type("org.lwjglx.input.Keyboard");
        Object oldQueue = field(keyboard, "eventQueue");
        ByteBuffer prior = (ByteBuffer) field(keyboard, "sdlKeyPressedArray"), local = ByteBuffer.allocateDirect(prior.capacity());
        for (int i = 0; i < prior.capacity(); i++) local.put(i, prior.get(i));
        for (int modifier : new int[] { 42, 54, 56, 184, 29, 157 }) local.put(((Number) call(type("org.lwjglx.input.KeyCodes"), "lwjglToSdlScancode", modifier)).intValue(), (byte) 0);
        java.util.concurrent.ArrayBlockingQueue<Object> queue = new java.util.concurrent.ArrayBlockingQueue<>(2);
        Object press = Enum.valueOf((Class) type("org.lwjglx.input.Keyboard$KeyState"), "PRESS");
        queue.add(type("org.lwjglx.input.Keyboard$KeyEvent").getConstructor(int.class, int.class, int.class, type("org.lwjglx.input.Keyboard$KeyState"), long.class)
            .newInstance(code, code, 0, press, System.nanoTime()));
        try {
            set(keyboard, "sdlKeyPressedArray", local); set(keyboard, "eventQueue", queue);
            Object event = type("com.cleanroommc.modularui.api.event.KeyboardInputEvent$Pre").getConstructor(type("net.minecraft.client.gui.GuiScreen")).newInstance(gui);
            call(net.minecraftforge.common.MinecraftForge.EVENT_BUS, "post", event);
            return Boolean.TRUE.equals(call(event, "isCanceled"));
        } finally { set(keyboard, "eventQueue", oldQueue); set(keyboard, "sdlKeyPressedArray", prior); }
    }
}
