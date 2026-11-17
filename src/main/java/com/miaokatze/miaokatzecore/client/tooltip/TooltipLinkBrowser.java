// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.miaokatzecore.client.tooltip;

import java.net.URI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/** Explicit HTTP(S) clicks follow Minecraft's link and confirmation preferences. */
final class TooltipLinkBrowser {

    static void open(GuiScreen parent, String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != parent || !mc.gameSettings.chatLinks || !TooltipTextLinks.valid(url)) return;
        if (!mc.gameSettings.chatLinksPrompt) {
            browse(url);
            return;
        }
        GuiYesNoCallback callback = (confirmed, id) -> {
            if (confirmed) browse(url);
            mc.displayGuiScreen(parent);
        };
        mc.displayGuiScreen(new GuiConfirmOpenLink(callback, url, 0, false) {

            @Override
            protected void keyTyped(char character, int code) {
                if (code == Keyboard.KEY_ESCAPE) callback.confirmClicked(false, 0);
                else super.keyTyped(character, code);
            }
        });
    }

    private static void browse(String url) {
        try {
            Class<?> desktop = Class.forName("java.awt.Desktop");
            desktop.getMethod("browse", URI.class)
                .invoke(
                    desktop.getMethod("getDesktop")
                        .invoke(null),
                    URI.create(url));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LogManager.getLogger("MIAOKATZE-Core")
                .warn("Cannot open tooltip website: " + url, failure);
        }
    }
}
