package com.miaokatze.miaokatzecore.mixin.camera;

import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.miaokatze.miaokatzecore.client.camera.WorldCameraHooks;

@Mixin(MovementInputFromOptions.class)
public abstract class MixinMovementInputFromOptions extends MovementInput {

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"))
    private void core$movement(CallbackInfo ci) {
        WorldCameraHooks.filterMovement(this);
    }
}
