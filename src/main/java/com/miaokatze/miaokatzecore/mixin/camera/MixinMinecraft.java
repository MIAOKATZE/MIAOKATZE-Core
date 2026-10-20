package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.InventoryPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;
import com.miaokatze.miaokatzecore.client.camera.WorldCameras;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void core$lifecycle(CallbackInfo ci) {
        WorldCameraHooks.lifecycle((Minecraft) (Object) this);
    }

    @Inject(method = "displayGuiScreen", at = @At("RETURN"))
    private void core$gui(GuiScreen screen, CallbackInfo ci) {
        // Forge's cancellable GuiOpenEvent must run before ownership policy (e.g. Esc exits a camera first).
        if (((Minecraft) (Object) this).currentScreen != null) WorldCameraHooks.guiOpened();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void core$world(WorldClient world, String message, CallbackInfo ci) {
        WorldCameras.worldChanged(world);
    }

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    private void core$scroll(InventoryPlayer inventory, int direction) {
        if (!WorldCameraHooks.consumeHotbarScroll()) inventory.changeCurrentItem(direction);
    }

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;loadRenderers()V"))
    private void core$debugChunks(RenderGlobal renderer) {
        if (!WorldCameraHooks.blockDebugReloads()) renderer.loadRenderers();
    }

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;refreshResources()V"))
    private void core$debugResources(Minecraft mc) {
        if (!WorldCameraHooks.blockDebugReloads()) mc.refreshResources();
    }
}
