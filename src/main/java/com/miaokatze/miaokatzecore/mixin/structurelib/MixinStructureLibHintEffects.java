// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.structurelib;

import net.minecraft.world.World;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizon.structurelib.ClientProxy;

@Mixin(value = ClientProxy.class, remap = false)
public abstract class MixinStructureLibHintEffects {

    @Shadow
    private static boolean allHintsDirty;

    /** Vanilla retains up to 4,000 effect particles in one layer. */
    @Unique
    private static final int CORE_HINT_EFFECT_BUDGET = 4000;

    @Unique
    private int core$remainingHintEffects = -1;

    @Inject(method = "startHinting", at = @At("TAIL"))
    private void core$startHintEffects(World world, CallbackInfo ci) {
        core$remainingHintEffects = CORE_HINT_EFFECT_BUDGET;
    }

    @Inject(
        method = "endHinting",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/structurelib/ClientProxy$HintGroup;setCreationTime(I)V",
            shift = At.Shift.AFTER))
    private void core$publishHintGroup(World world, CallbackInfo ci) {
        core$remainingHintEffects = -1;
        // Finishing a group can replace an old group even with no new hints to mark the list dirty.
        allHintsDirty = true;
    }

    @Inject(
        method = "hintParticleTinted(Lnet/minecraft/world/World;III[Lnet/minecraft/util/IIcon;[S)V",
        at = @At(
            value = "FIELD",
            target = "Lcom/gtnewhorizon/structurelib/ClientProxy;allHintsDirty:Z",
            opcode = Opcodes.PUTSTATIC,
            shift = At.Shift.AFTER),
        cancellable = true)
    private void core$limitDecorativeEffects(CallbackInfo ci) {
        // Registration, grouping, collision handling and tint are complete. Only the decorative EntityFX follows.
        if (core$remainingHintEffects == 0) ci.cancel();
        else if (core$remainingHintEffects > 0) core$remainingHintEffects--;
    }
}
