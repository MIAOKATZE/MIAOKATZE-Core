// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.mixin.blockrenderer;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.preview.TrackedPreviewRevision;

/** An independent revision journal. Never reads or consumes the construction hasChanged flag. */
@Pseudo
@Mixin(targets = "blockrenderer6343.client.world.TrackedDummyWorld", remap = false)
public abstract class MixinTrackedPreviewWorld implements TrackedPreviewRevision {

    @Unique
    private long core$previewRevision;

    @Override
    public long miaokatze$previewRevision() {
        return core$previewRevision;
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void core$worldCleared(CallbackInfo callback) {
        core$previewRevision++;
    }

    // The optional jar uses SRG names in production and MCP names in the development classpath.
    @Inject(
        method = { "setBlock(IIILnet/minecraft/block/Block;II)Z", "func_147465_d(IIILnet/minecraft/block/Block;II)Z" },
        at = @At("HEAD"))
    private void core$blockWrite(int x, int y, int z, Block block, int metadata, int flags,
        CallbackInfoReturnable<Boolean> callback) {
        core$previewRevision++;
    }

    @Inject(method = { "setBlockMetadataWithNotify(IIIII)Z", "func_72921_c(IIIII)Z" }, at = @At("HEAD"))
    private void core$metadataWrite(int x, int y, int z, int metadata, int flags,
        CallbackInfoReturnable<Boolean> callback) {
        core$previewRevision++;
    }

    @Inject(
        method = { "setTileEntity(IIILnet/minecraft/tileentity/TileEntity;)V",
            "func_147455_a(IIILnet/minecraft/tileentity/TileEntity;)V" },
        at = @At("HEAD"))
    private void core$tileWrite(int x, int y, int z, TileEntity tile, CallbackInfo callback) {
        core$previewRevision++;
    }

    @Inject(method = { "removeTileEntity(III)V", "func_147475_p(III)V" }, at = @At("HEAD"))
    private void core$tileRemoved(int x, int y, int z, CallbackInfo callback) {
        core$previewRevision++;
    }

    @Inject(method = "setVisibleYLevel(I)V", at = @At("HEAD"))
    private void core$layerChanged(int level, CallbackInfo callback) {
        core$previewRevision++;
    }
}
