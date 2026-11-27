// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.testlab;

import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.google.gson.GsonBuilder;
import com.miaokatze.miaokatzecore.api.testlab.ArtifactIdentity;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage.Tier;
import com.miaokatze.miaokatzecore.client.tooltip.TooltipController;
import com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;
import com.slprime.chromatictooltips.event.RenderTooltipEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.ItemList;

public final class TooltipTargetSwitchProbe {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final String run = java.util.UUID.fromString(System.getProperty("miaotest.host.runId")).toString();
    private final Path output = Paths.get(System.getProperty("miaotest.host.receiptDir"), "core-tooltip-target-switch.json");
    private final List<Map<String, Object>> checks = new ArrayList<>();
    private final List<ItemStack> items = new ArrayList<>();
    private final Map<String, Tier> built = new LinkedHashMap<>();
    private final List<String> afterSwitch = new ArrayList<>();
    private GuiScreen screen;
    private Point cursor = new Point(30, 30);
    private Rectangle lastBounds;
    private Field mouseX, mouseY;
    private int savedX, savedY;
    private boolean pointerOwned;
    private String painted;
    private boolean done;
    private int target, stage, stageFrames, visits;
    private Tier expectedTier, otherTier;
    private long started;

    static void install() {
        if (!"gtmb".equals(System.getProperty("miaotest.host.module"))
            || !Boolean.getBoolean("miaotest.host.environment")) return;
        TooltipTargetSwitchProbe probe = new TooltipTargetSwitchProbe();
        MinecraftForge.EVENT_BUS.register(probe);
        FMLCommonHandler.instance().bus().register(probe);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            if (screen != null) {
                require(System.nanoTime() - started < 25_000_000_000L, "Timed out in stage " + stage);
                return;
            }
            MinecraftServer server = mc.getIntegratedServer();
            if (server == null || mc.thePlayer == null || mc.thePlayer.ticksExisted < 4 || mc.currentScreen != null) return;
            Path world = server.worldServerForDimension(0).getSaveHandler().getWorldDirectory().toPath();
            if (!world.getFileName().toString().equals("MiaoHost-" + run)
                || !Files.isRegularFile(world.resolve(".miaotest-host-owner.json"))
                || !LabWorldPolicy.owns(server.worldServerForDimension(0))) return;
            items.add(ItemList.Machine_Multi_Furnace.get(1));
            items.add(ItemList.Machine_Multi_BlastFurnace.get(1));
            Class<?> mouse = Class.forName("org.lwjglx.input.Mouse");
            mouseX = field(mouse, "x");
            mouseY = field(mouse, "y");
            screen = new GuiScreen() {
                @Override
                public void drawScreen(int x, int y, float time) {
                    TooltipHandler.drawHoveringText(new TooltipRequest(null, TooltipTarget.ofItem(items.get(target)), null, null));
                }

                @Override
                public boolean doesGuiPauseGame() {
                    return false;
                }
            };
            started = System.nanoTime();
            mc.displayGuiScreen(screen);
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void author(ItemTooltipEvent event) {
        if (done || screen == null || mc.currentScreen != screen) return;
        String key = TooltipPages.identity(event.itemStack);
        if (items.stream().noneMatch(stack -> key.equals(TooltipPages.identity(stack)))) return;
        NativeTooltipPage page = NativeTooltipPage.forItem(event.itemStack);
        for (int line = 1; line <= 5; line++) page.line(page.tier().name() + " hover regression row " + line);
        require(page.appendTo(event.itemStack, event.toolTip), "Unable to bind the native tier page");
        built.put(key, page.tier());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeFrame(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (done || event.gui != screen) return;
        try {
            restorePointer();
            painted = null;
            ScaledResolution scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            savedX = mouseX.getInt(null);
            savedY = mouseY.getInt(null);
            pointerOwned = true;
            mouseX.setInt(null, cursor.x * mc.displayWidth / scale.getScaledWidth());
            mouseY.setInt(null, (scale.getScaledHeight() - cursor.y - 1) * mc.displayHeight / scale.getScaledHeight());
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    @SubscribeEvent
    public void onPaint(RenderTooltipEvent event) {
        if (done || screen == null || mc.currentScreen != screen) return;
        painted = TooltipPages.identity(event.context.getItem());
        lastBounds = new Rectangle(event.x, event.y, event.width, event.height);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterFrame(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (done || event.gui != screen) return;
        try {
            String key = TooltipPages.identity(items.get(target));
            if (++stageFrames > 90) throw new IllegalStateException(
                "Paint or tier did not advance: stage=" + stage + " target=" + key + " painted=" + painted
                    + " built=" + built + " afterSwitch=" + afterSwitch);
            if (stage == 0) {
                if (visits > 0 && afterSwitch.size() < 6) afterSwitch.add(painted);
                if (!key.equals(painted) || !built.containsKey(key)) return;
                Tier before = tier(target);
                otherTier = tier(1 - target);
                expectedTier = Tier.values()[(before.ordinal() + 1) % Tier.values().length];
                require(key(Keyboard.KEY_V, true), "Alt+V did not consume the input at " + key);
                require(tier(target) == expectedTier && tier(1 - target) == otherTier, "Alt+V changed the wrong item");
                record("Alt+V", key, before.name() + " -> " + expectedTier.name());
                advance(1);
            } else if (stage == 1) {
                if (!key.equals(painted) || built.get(key) != expectedTier) return;
                require(key(Keyboard.KEY_C, false) && TooltipController.pinned(), "C did not pin " + key);
                require(key.equals(TooltipPinning.key()), "C pinned a stale item");
                record("C", key, "pinned");
                advance(2);
            } else if (stage == 2) {
                if (!key.equals(painted)) return;
                require(key(Keyboard.KEY_C, false) && !TooltipController.pinned(), "C could not unpin " + key);
                if (++visits == 4) finish(null);
                else advance(3);
            } else if (key.equals(painted)) {
                require(lastBounds.width > 12 && lastBounds.height > 12, "Missing painted root rectangle");
                cursor = new Point(lastBounds.x + 8, lastBounds.y + 8);
                require(lastBounds.contains(cursor), "Next pointer must land inside previous tooltip");
                target = 1 - target;
                afterSwitch.clear();
                advance(0);
            }
        } catch (Throwable failure) {
            finish(failure);
        } finally {
            restorePointer();
        }
    }

    private void record(String action, String key, String result) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("visit", visits);
        check.put("action", action);
        check.put("target", key);
        check.put("result", result);
        check.put("earlyPaintsAfterSwitch", new ArrayList<>(afterSwitch));
        checks.add(check);
    }

    private void advance(int next) {
        stage = next;
        stageFrames = 0;
    }

    private Tier tier(int index) {
        return NativeTooltipPage.forItem(items.get(index)).tier();
    }

    private boolean key(int code, boolean alt) throws ReflectiveOperationException {
        Class<?> keyboard = Class.forName("org.lwjglx.input.Keyboard");
        Field queue = field(keyboard, "eventQueue");
        Field pressed = field(keyboard, "sdlKeyPressedArray");
        ByteBuffer old = (ByteBuffer) pressed.get(null);
        ByteBuffer local = ByteBuffer.allocateDirect(old.capacity());
        for (int i = 0; i < old.capacity(); i++) local.put(i, old.get(i));
        java.lang.reflect.Method scancode = Class.forName("org.lwjglx.input.KeyCodes")
            .getMethod("lwjglToSdlScancode", int.class);
        for (int modifier : new int[] {42, 54, 56, 184, 29, 157}) {
            int index = (int) scancode.invoke(null, modifier);
            local.put(index, (byte) (alt && modifier == Keyboard.KEY_LMENU ? 1 : 0));
        }
        Class<?> state = Class.forName("org.lwjglx.input.Keyboard$KeyState");
        Object press = state.getField("PRESS").get(null);
        Object event = Class.forName("org.lwjglx.input.Keyboard$KeyEvent")
            .getConstructor(int.class, int.class, int.class, state, long.class)
            .newInstance(code, code, 0, press, System.nanoTime());
        ArrayBlockingQueue<Object> owned = new ArrayBlockingQueue<>(2);
        owned.add(event);
        Object oldQueue = queue.get(null);
        try {
            pressed.set(null, local);
            queue.set(null, owned);
            return MinecraftForge.EVENT_BUS.post(new KeyboardInputEvent.Pre(screen));
        } finally {
            queue.set(null, oldQueue);
            pressed.set(null, old);
        }
    }

    private void restorePointer() {
        if (!pointerOwned) return;
        try {
            mouseX.setInt(null, savedX);
            mouseY.setInt(null, savedY);
            pointerOwned = false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to restore local pointer", exception);
        }
    }

    private void finish(Throwable failure) {
        if (done) return;
        done = true;
        restorePointer();
        MinecraftForge.EVENT_BUS.unregister(this);
        FMLCommonHandler.instance().bus().unregister(this);
        if (mc.currentScreen == screen) mc.displayGuiScreen(null);
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("runId", run);
        receipt.put("status", failure == null && visits == 4 ? "PASS" : "FAIL");
        receipt.put("failure", failure == null ? null : failure.toString());
        receipt.put("checks", checks);
        receipt.put("clearedOnExit", !TooltipController.pinned());
        receipt.put("coreArtifact", ArtifactIdentity.capture(TooltipPinning.class).asMap());
        receipt.put("inputScope", "MUI2 keyboard pre-event and native Chromatic drawing in an owned GUI; no OS input");
        try {
            Files.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(receipt).getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to write tooltip regression receipt", error);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
