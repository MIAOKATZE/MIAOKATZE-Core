// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.blamejared.controlling.keybinding.ComboKeyBinding;
import com.blamejared.controlling.keybinding.KeyModifier;
import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.RichTooltipEvent;
import com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent;
import com.miaokatze.miaokatzecore.api.tooltip.TooltipPages;

import codechicken.nei.LayoutManager;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class TooltipController {

    private static final String CATEGORY = "miaokatzecore.keys.category";
    private final KeyBinding details = new KeyBinding("miaokatzecore.keys.details", Keyboard.KEY_V, CATEGORY);
    private KeyBinding effects;

    private GuiScreen screen;
    private String key = "";
    private int mouseX, mouseY;
    private long lastRender;
    private final boolean nei = Loader.isModLoaded("NotEnoughItems");

    public static void install() {
        TooltipController input = new TooltipController();
        ClientRegistry.registerKeyBinding(input.details);
        if (Loader.isModLoaded("controlling") && Chords.available(input.details)) {
            input.effects = Chords.registerEffects();
        }
        TooltipPages.bindLabels(() -> input.label(false), () -> input.label(true));
        MinecraftForge.EVENT_BUS.register(input);
        if (Loader.isModLoaded("chromatictooltips")) {
            MinecraftForge.EVENT_BUS.register(new ChromaticHover(input));
            com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipClient.install();
        }
    }

    private void hovered(GuiScreen gui, ItemStack stack) {
        screen = gui;
        key = TooltipPages.identity(stack);
        TooltipPages.rendered(key);
        mouseX = Mouse.getX();
        mouseY = Mouse.getY();
        lastRender = com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock.nowNanos();
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
        if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent()) return;
        if (pressed(event.gui, Keyboard.getEventKey())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void mouse(MouseInputEvent.Pre event) {
        int button = Mouse.getEventButton();
        if (button >= 0 && Mouse.getEventButtonState() && pressed(event.gui, button - 100)) event.setCanceled(true);
    }

    private String label(boolean effect) {
        KeyBinding binding = effect && effects != null ? effects : details;
        if (binding.getKeyCode() == Keyboard.KEY_NONE)
            return StatCollector.translateToLocal("miaokatzecore.keys.unbound");
        if (effects != null) return Chords.label(binding);
        String name = GameSettings.getKeyDisplayString(binding.getKeyCode());
        return effect ? "Alt+" + name : name;
    }

    private boolean pressed(GuiScreen gui, int code) {
        if (code == Keyboard.KEY_NONE
            || (code != details.getKeyCode() && (effects == null || effects.getKeyCode() != code))) return false;
        if (gui == null || gui != screen
            || gui != Minecraft.getMinecraft().currentScreen
            || !TooltipPages.supports(key)
            || com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock.nowNanos() - lastRender > 300000000L)
            return false;
        if ((mouseX != Mouse.getX() || mouseY != Mouse.getY())
            && !(nei && gui instanceof GuiContainer && NeiInput.sameItem((GuiContainer) gui, key))) return false;
        ModularScreen modular = ModularScreen.getCurrent();
        if (modular != null && modular.getContext()
            .getFocusedWidget() != null || nei && NeiInput.focused() || typingInVanillaField(gui)) return false;
        boolean effect;
        if (effects != null) {
            effect = Chords.matches(effects, code);
            if (!effect && !Chords.matches(details, code)) return false;
        } else {
            if (GuiScreen.isCtrlKeyDown() || GuiScreen.isShiftKeyDown()) return false;
            effect = Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        }
        return TooltipPages.toggle(key, effect);
    }

    // Optional GTNH Controlling adapter. Match exactly the chord configured in the Controls screen.
    private static final class Chords {

        static boolean available(KeyBinding key) {
            return key instanceof ComboKeyBinding;
        }

        static KeyBinding registerEffects() {
            KeyBinding key = new KeyBinding("miaokatzecore.keys.effects", Keyboard.KEY_V, CATEGORY);
            ComboKeyBinding combo = (ComboKeyBinding) key;
            combo.controlling$setDefaultKeyModifier(KeyModifier.ALT);
            combo.controlling$setKeyModifier(KeyModifier.ALT);
            ClientRegistry.registerKeyBinding(key);
            return key;
        }

        static String label(KeyBinding binding) {
            return ((ComboKeyBinding) binding).controlling$getDisplayName();
        }

        static boolean matches(KeyBinding binding, int code) {
            if (binding.getKeyCode() != code) return false;
            KeyModifier wanted = ((ComboKeyBinding) binding).controlling$getKeyModifier();
            for (KeyModifier modifier : KeyModifier.VALUES) {
                if (modifier == KeyModifier.NONE || modifier.matches(code)) continue;
                if (modifier.isActive() != (modifier == wanted)) return false;
            }
            return true;
        }
    }

    // Many inventory screens (including AE terminals) own vanilla text fields without exposing focus.
    // Inspect only on an eligible tooltip shortcut. Never take the key if focus cannot be determined.
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

    // Kept in a separately loaded class: Core also runs without Chromatic Tooltips.
    public static final class ChromaticHover {

        private final TooltipController input;

        private ChromaticHover(TooltipController input) {
            this.input = input;
        }

        @SubscribeEvent
        public void rendered(com.slprime.chromatictooltips.event.RenderTooltipEvent event) {
            input.hovered(Minecraft.getMinecraft().currentScreen, event.context.getItem());
        }
    }

    private static final class NeiInput {

        static boolean sameItem(GuiContainer gui, String key) {
            return key.equals(TooltipPages.identity(GuiContainerManager.getStackMouseOver(gui)));
        }

        static boolean focused() {
            return LayoutManager.getInputFocused() != null;
        }
    }
}
