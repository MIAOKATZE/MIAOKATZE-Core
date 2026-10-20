package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

/** GT205 owns these tests outside RenderBlocks; keep its texture layers, AO, and nextUp/nextDown offsets. */
@Pseudo
@Mixin(targets = "gregtech.common.render.SBRWorldContext", remap = false)
public abstract class MixinSliceGTWorldContext {

    @WrapOperation(
        method = { "renderNegativeYFacing", "renderPositiveYFacing" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            remap = true),
        remap = false)
    private boolean core$gtBoundaryFace(Block block, IBlockAccess world, int x, int y, int z, int side,
        Operation<Boolean> original) {
        return SliceMeshes.opensFace(world, x, y, z, side) || original.call(block, world, x, y, z, side);
    }
}
