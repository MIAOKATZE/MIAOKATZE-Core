// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
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
import com.miaokatze.miaokatzecore.api.tooltip.component.NativeTooltipPage;

import codechicken.nei.LayoutManager;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class TooltipController {

    private static final String CATEGORY = "miaokatzecore.keys.tooltip.category";
    private final KeyBinding details = new KeyBinding("miaokatzecore.keys.details", Keyboard.KEY_V, CATEGORY);
    private final KeyBinding select = new KeyBinding("miaokatzecore.keys.select", Keyboard.KEY_TAB, CATEGORY);
    private final KeyBinding pin = new KeyBinding("miaokatzecore.keys.pin", Keyboard.KEY_C, CATEGORY);
    private static TooltipController installed;
    private final boolean chromatic = Loader.isModLoaded("chromatictooltips");

    private GuiScreen screen;
    private String key = "";
    private int mouseX, mouseY;
    private long lastRender;
    private final boolean nei = Loader.isModLoaded("NotEnoughItems");
    private static java.util.function.BooleanSupplier tierHeld = () -> false;

    static boolean tierHeld() {
        return tierHeld.getAsBoolean();
    }

    public static void install() {
        TooltipController input = new TooltipController();
        installed = input;
        ClientRegistry.registerKeyBinding(input.details);
        ClientRegistry.registerKeyBinding(input.select);
        ClientRegistry.registerKeyBinding(input.pin);
        TooltipPages.bindLabels(() -> label(input.details), () -> "Alt+" + label(input.details));
        tierHeld = () -> held(input.details) && alt() && matches(input.details, input.details.getKeyCode(), true);
        MinecraftForge.EVENT_BUS.register(input);
        FMLCommonHandler.instance()
            .bus()
            .register(input);
        if (input.chromatic) {
            MinecraftForge.EVENT_BUS.register(new ChromaticHover(input));
            com.miaokatze.miaokatzecore.client.tooltip.NativeTooltipClient.install();
        }
    }

    private void hovered(GuiScreen gui, ItemStack stack) {
        if (pinned()) return;
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
        if (chromatic) {
            TooltipPinning.beginFrame(event.gui);
            NativeTooltipMouse.newFrame();
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (chromatic) TooltipPinning.screen(current);
        if (current == null || current != screen) {
            screen = null;
            key = "";
        }
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
        if (button < 0) return;
        if (chromatic) {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution size = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int x = Mouse.getEventX() * size.getScaledWidth() / mc.displayWidth;
            int y = size.getScaledHeight() - Mouse.getEventY() * size.getScaledHeight() / mc.displayHeight - 1;
            boolean consumed = Mouse.getEventButtonState() ? NativeTooltipMouse.click(event.gui, x, y, button)
                : NativeTooltipMouse.consumeRelease(event.gui, x, y, button);
            if (consumed) {
                event.setCanceled(true);
                return;
            }
        }
        if (!Mouse.getEventButtonState()) return;
        if (pressed(event.gui, button - 100)) event.setCanceled(true);
    }

    private static String label(KeyBinding binding) {
        if (binding.getKeyCode() == Keyboard.KEY_NONE)
            return StatCollector.translateToLocal("miaokatzecore.keys.unbound");
        if (Loader.isModLoaded("controlling") && Chords.available(binding)) return Chords.label(binding);
        return GameSettings.getKeyDisplayString(binding.getKeyCode());
    }

    public static String pinKeyLabel() {
        return installed == null ? "C" : label(installed.pin);
    }

    public static String selectKeyLabel() {
        return installed == null ? "Tab" : label(installed.select);
    }

    public static boolean selectHeld() {
        return installed != null && held(installed.select);
    }

    public static boolean pinned() {
        return installed != null && installed.chromatic && TooltipPinning.pinned();
    }

    public static boolean isPinned(String item) {
        return pinned() && item.equals(TooltipPinning.key());
    }

    private static boolean alt() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    private static boolean held(KeyBinding binding) {
        int code = binding.getKeyCode();
        return code > 0 ? Keyboard.isKeyDown(code) : code < 0 && Mouse.isButtonDown(code + 100);
    }

    private boolean pressed(GuiScreen gui, int code) {
        boolean fixed = pinned();
        String item = fixed ? TooltipPinning.key() : key;
        boolean page = NativeTooltipPage.supports(item);
        boolean choose = matches(select, code, false);
        boolean pinning = chromatic && matches(pin, code, false);
        boolean cycle = matches(details, code, true);
        if (code == Keyboard.KEY_NONE || (!choose && !pinning && !cycle)) return false;
        if (gui == null || (!fixed && gui != screen)
            || gui != Minecraft.getMinecraft().currentScreen
            || (fixed && !TooltipPinning.active(gui))
            || (!fixed && (item.isEmpty()
                || com.miaokatze.miaokatzecore.api.tooltip.TooltipAnimationClock.nowNanos() - lastRender > 300000000L)))
            return false;
        if (!fixed && (mouseX != Mouse.getX() || mouseY != Mouse.getY())
            && !(nei && gui instanceof GuiContainer && NeiInput.sameItem((GuiContainer) gui, item))) return false;
        ModularScreen modular = ModularScreen.getCurrent();
        if (modular != null && modular.getContext()
            .getFocusedWidget() != null || nei && NeiInput.focused() || typingInVanillaField(gui)) return false;
        if (pinning) return TooltipPinning.toggle(gui);
        if (page && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) return false;
        if (choose) {
            boolean changed = page && NativeTooltipPage.act(item, NativeTooltipPage.Action.SELECT);
            if (changed && chromatic) {
                if (fixed) TooltipPinning.followSelected();
                else ChromaticHover.follow(item);
            }
            return changed;
        }
        boolean changed = page
            ? NativeTooltipPage.act(item, alt() ? NativeTooltipPage.Action.TIER : NativeTooltipPage.Action.VARIANT)
            : TooltipPages.toggle(item, alt());
        if (changed && fixed) TooltipPinning.refresh();
        return changed;
    }

    private static boolean matches(KeyBinding binding, int code, boolean allowAlt) {
        if (binding.getKeyCode() != code || code == Keyboard.KEY_NONE) return false;
        if (Loader.isModLoaded("controlling") && Chords.available(binding))
            return Chords.matches(binding, code, allowAlt);
        return !GuiScreen.isCtrlKeyDown() && !GuiScreen.isShiftKeyDown() && (allowAlt || !alt());
    }

    // Optional GTNH Controlling adapter. Match exactly the chord configured in the Controls screen.
    private static final class Chords {

        static boolean available(KeyBinding key) {
            return key instanceof ComboKeyBinding;
        }

        static String label(KeyBinding binding) {
            return ((ComboKeyBinding) binding).controlling$getDisplayName();
        }

        static boolean matches(KeyBinding binding, int code, boolean allowAlt) {
            if (binding.getKeyCode() != code) return false;
            KeyModifier wanted = ((ComboKeyBinding) binding).controlling$getKeyModifier();
            for (KeyModifier modifier : KeyModifier.VALUES) {
                if (modifier == KeyModifier.NONE || modifier.matches(code)) continue;
                if (allowAlt && modifier == KeyModifier.ALT && wanted != KeyModifier.ALT) continue;
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
            TooltipPinning.rendered(event);
            input.hovered(Minecraft.getMinecraft().currentScreen, event.context.getItem());
            ChartPointer.rendered(event);
        }

        static void follow(String key) {
            ChartPointer.follow(key);
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
