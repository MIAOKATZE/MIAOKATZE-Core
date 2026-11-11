// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.items;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.RichTooltipEvent;
import com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;

import codechicken.nei.LayoutManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class TooltipInput {

    private GuiScreen screen;
    private String key = "";
    private int mouseX, mouseY;
    private long lastRender;
    private final boolean nei = Loader.isModLoaded("NotEnoughItems");

    public static void install() {
        MinecraftForge.EVENT_BUS.register(new TooltipInput());
    }

    private void hovered(GuiScreen gui, ItemStack stack) {
        screen = gui;
        key = TooltipPages.identity(stack);
        mouseX = Mouse.getX();
        mouseY = Mouse.getY();
        lastRender = System.nanoTime();
    }

    @SubscribeEvent
    public void newFrame(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre event) {
        key = "";
    }

    @SubscribeEvent
    public void vanilla(RenderTooltipEvent event) {
        hovered(event.gui, event.itemStack);
    }

    @SubscribeEvent
    public void modular(RichTooltipEvent.PostText event) {
        hovered(Minecraft.getMinecraft().currentScreen, event.getItemStack());
    }

    @SubscribeEvent
    public void keyboard(KeyboardInputEvent.Pre event) {
        if (Keyboard.getEventKey() != Keyboard.KEY_C || !Keyboard.getEventKeyState()
            || Keyboard.isRepeatEvent()
            || GuiScreen.isCtrlKeyDown()
            || GuiScreen.isShiftKeyDown()) return;
        if (event.gui != screen || !(screen instanceof GuiContainer)
            || screen instanceof GuiRepair
            || screen instanceof GuiContainerCreative && ((GuiContainerCreative) screen).func_147056_g()
                == net.minecraft.creativetab.CreativeTabs.tabAllSearch.getTabIndex()
            || mouseX != Mouse.getX()
            || mouseY != Mouse.getY()
            || System.nanoTime() - lastRender > 300000000L
            || !TooltipPages.supports(key)) return;
        ModularScreen modular = ModularScreen.getCurrent();
        if (modular != null && modular.getContext()
            .getFocusedWidget() != null || nei && NeiInput.focused()) return;
        if (typingInVanillaField(screen)) return;
        if (TooltipPages.toggle(key, Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)))
            event.setCanceled(true);
    }

    // Many inventory screens (including AE terminals) own vanilla text fields without exposing focus.
    // Inspect only on an eligible C press. Never take the key if focus cannot be determined.
    private static boolean typingInVanillaField(GuiScreen gui) {
        for (Class<?> type = gui.getClass(); type != GuiScreen.class && type != null; type = type.getSuperclass()) {
            java.lang.reflect.Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (LinkageError | SecurityException unavailable) {
                return true;
            }
            for (java.lang.reflect.Field field : fields) {
                if (!net.minecraft.client.gui.GuiTextField.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    net.minecraft.client.gui.GuiTextField text = (net.minecraft.client.gui.GuiTextField) field.get(gui);
                    if (text != null && text.isFocused()) return true;
                } catch (ReflectiveOperationException | SecurityException ex) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class NeiInput {

        static boolean focused() {
            return LayoutManager.getInputFocused() != null;
        }
    }
}
