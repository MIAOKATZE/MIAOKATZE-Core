package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;
import com.miaokatze.miaokatzecore.client.camera.WorldCameras;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void core$lifecycle(CallbackInfo ci) {
        WorldCameraHooks.lifecycle((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void core$pointerQueueDrained(CallbackInfo ci) {
        WorldCameraHooks.endTick();
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

    @WrapOperation(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;postMouseEvent()Z",
            remap = false))
    private boolean core$cameraPointer(Operation<Boolean> original) {
        return WorldCameraHooks.rawMouseEvent((Minecraft) (Object) this) || original.call();
    }

    @WrapOperation(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z", remap = false))
    private boolean core$cameraKeyboard(Operation<Boolean> original) {
        while (original.call()) {
            if (WorldCameraHooks.allowRawKey(Keyboard.getEventKey())) return true;
        }
        return false;
    }

    @WrapOperation(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"))
    private void core$scroll(InventoryPlayer inventory, int direction, Operation<Void> original) {
        // Preserve other mods' redirects/wrappers (e.g. Bogosorter's hotbar-swap cancellation).
        if (!WorldCameraHooks.consumeHotbarScroll()) original.call(inventory, direction);
    }

    @WrapOperation(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;loadRenderers()V"))
    private void core$debugChunks(RenderGlobal renderer, Operation<Void> original) {
        if (!WorldCameraHooks.blockDebugReloads()) original.call(renderer);
    }

    @WrapOperation(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;refreshResources()V"))
    private void core$debugResources(Minecraft mc, Operation<Void> original) {
        if (!WorldCameraHooks.blockDebugReloads()) original.call(mc);
    }
}
