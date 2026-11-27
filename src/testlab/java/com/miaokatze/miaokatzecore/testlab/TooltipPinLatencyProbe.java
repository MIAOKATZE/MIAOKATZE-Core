// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.testlab;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Keyboard;

import com.google.gson.GsonBuilder;
import com.miaokatze.miaokatzecore.api.testlab.ArtifactIdentity;
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;
import com.miaokatze.miaokatzecore.client.tooltip.TooltipController;
import com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning;
import com.slprime.chromatictooltips.TooltipHandler;
import com.slprime.chromatictooltips.api.TooltipContext;
import com.slprime.chromatictooltips.api.TooltipRequest;
import com.slprime.chromatictooltips.api.TooltipTarget;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.ItemList;

public final class TooltipPinLatencyProbe {
    private final Map<String, Object> result = new LinkedHashMap<>();
    private final Path out = Paths.get(System.getProperty("miaotest.host.receiptDir"));
    private final String run = java.util.UUID.fromString(System.getProperty("miaotest.host.runId")).toString();
    private ItemStack machine;
    private GuiScreen screen;
    private TooltipContext hover;
    private int reads, fixedReads, fixedReadsBefore, idleFrames, paintedFrames, rounds, stage;
    private long start, keyAt;
    private final java.util.List<Map<String, Object>> samples = new java.util.ArrayList<>();
    private Map<String, Object> sample;
    private boolean finished;

    public static void install() {
        if (!"gtmb".equals(System.getProperty("miaotest.host.module"))
            || !Boolean.getBoolean("miaotest.host.environment")) return;
        TooltipPinLatencyProbe probe = new TooltipPinLatencyProbe();
        MinecraftForge.EVENT_BUS.register(probe);
        FMLCommonHandler.instance().bus().register(probe);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (screen == null) {
                MinecraftServer server = mc.getIntegratedServer();
                if (server == null || mc.thePlayer == null || mc.thePlayer.ticksExisted < 4 || mc.currentScreen != null) return;
                Path world = server.worldServerForDimension(0).getSaveHandler().getWorldDirectory().toPath();
                if (!world.getFileName().toString().equals("MiaoHost-" + run)
                    || !Files.isRegularFile(world.resolve(".miaotest-host-owner.json"))
                    || !LabWorldPolicy.owns(server.worldServerForDimension(0))) return;
                machine = ItemList.Machine_Multi_Furnace.get(1);
                screen = new GuiScreen() {
                    @Override
                    public void drawScreen(int x, int y, float time) {
                        TooltipHandler.drawHoveringText(new TooltipRequest(null, TooltipTarget.ofItem(machine), null, null));
                    }
                    @Override
                    public boolean doesGuiPauseGame() { return false; }
                };
                start = System.nanoTime();
                mc.displayGuiScreen(screen);
            } else if (System.nanoTime() - start > 25_000_000_000L) {
                throw new IllegalStateException("Timed out at stage " + stage);
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void authored(ItemTooltipEvent event) {
        if (machine == null || finished || Minecraft.getMinecraft().currentScreen != screen
            || machine.getItem() != event.itemStack.getItem() || machine.getItemDamage() != event.itemStack.getItemDamage()) return;
        reads++;
        for (StackTraceElement entry : Thread.currentThread().getStackTrace()) {
            if ("com.miaokatze.miaokatzecore.client.tooltip.TooltipPinning$Frame".equals(entry.getClassName())
                && "update".equals(entry.getMethodName())) {
                fixedReads++;
                break;
            }
        }
        NativeTooltipPage page = NativeTooltipPage.forItem(event.itemStack);
        for (int i = 1; i <= 40; i++) page.line("Independent authored machine data " + i);
        if (!page.appendTo(event.itemStack, event.toolTip)) throw new IllegalStateException("Authored page fixture could not bind");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void rendered(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (finished || screen == null || event.gui != screen) return;
        try {
            if (stage == 0) {
                if (++paintedFrames < 8) return;
                hover = TooltipHandler.getLastTooltipContext();
                if (hover == null || reads == 0 || hover.getItem() == null || !hover.getItem().isItemEqual(machine)) return;
                fixedReadsBefore = fixedReads;
                sample = new LinkedHashMap<>();
                samples.add(sample);
                if (rounds == 0) capture("core-pin-floating.png");
                keyAt = System.nanoTime();
                boolean consumed = press();
                sample.put("pressHandlingMicros", (System.nanoTime()-keyAt)/1000L);
                require(consumed && TooltipController.pinned(), "First C did not pin the already visible tooltip");
                sample.put("firstKeyAccepted", true);
                stage = 1;
            } else if (stage == 1) {
                if (++idleFrames == 1) {
                    sample.put("firstPinnedFrameMillis", (System.nanoTime()-keyAt)/1_000_000L);
                    if (rounds == 0) capture("core-pin-fixed.png");
                }
                if (idleFrames < 3) return;
                int rebuilds = fixedReads - fixedReadsBefore;
                sample.put("readsDuringInitialPinnedFrames", rebuilds);
                sample.put("reusedExactContext", hover == TooltipPinning.context());
                java.lang.reflect.Method isPinned=TooltipPinning.class.getDeclaredMethod("isPinned", TooltipContext.class);
                isPinned.setAccessible(true);
                sample.put("painterSeesPin", isPinned.invoke(null, TooltipPinning.context()));
                require(rebuilds == 0 && hover == TooltipPinning.context(), "Pin rebuilt the already visible context");
                require(Boolean.TRUE.equals(sample.get("painterSeesPin")), "Pin paint condition not set");
                TooltipPinning.refresh();
                stage = 2;
            } else {
                sample.put("readsOnExplicitRefresh", fixedReads - fixedReadsBefore);
                require(fixedReads > fixedReadsBefore && hover != TooltipPinning.context(), "Explicit refresh must still re-enrich the fixed tooltip");
                if (++rounds == 5) finish(null);
                else {
                    require(press() && !TooltipController.pinned(), "Second C must unpin the same tooltip");
                    stage = 0;
                    idleFrames = 0;
                    paintedFrames = 0;
                }
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }

    private boolean press() throws Exception {
        Field control = TooltipController.class.getDeclaredField("installed");
        control.setAccessible(true);
        java.lang.reflect.Method pressed = TooltipController.class.getDeclaredMethod("pressed", GuiScreen.class, int.class);
        pressed.setAccessible(true);
        return Boolean.TRUE.equals(pressed.invoke(control.get(null), screen, Keyboard.KEY_C));
    }

    private void capture(String file) throws java.io.IOException {
        Path screenshots=out.resolve("screenshots");
        Files.createDirectories(screenshots);
        Minecraft mc=Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(screenshots.toFile(),file,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
    }

    private void finish(Throwable failure) {
        finished = true;
        MinecraftForge.EVENT_BUS.unregister(this);
        FMLCommonHandler.instance().bus().unregister(this);
        Minecraft mc = Minecraft.getMinecraft();
        if (screen != null && mc.currentScreen == screen) {
            mc.displayGuiScreen(new GuiScreen() {});
            result.put("pinClearedOnScreenChange", !TooltipController.pinned());
            mc.displayGuiScreen(null);
        }
        result.put("runId", run);
        result.put("samples", samples);
        result.put("pass", failure == null);
        result.put("failure", failure == null ? null : failure.toString());
        result.put("coreArtifact", ArtifactIdentity.capture(TooltipPinning.class).asMap());
        result.put("inputScope", "in-JVM call to production shortcut dispatcher after a native DrawScreen frame; no OS input");
        try {
            Files.write(out.resolve("core-pin-latency.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)
                .getBytes(StandardCharsets.UTF_8));
        } catch (Exception problem) {
            throw new IllegalStateException("Could not record tooltip probe", problem);
        }
    }

    private static void require(boolean match, String message) {
        if (!match) throw new IllegalStateException(message);
    }
}
