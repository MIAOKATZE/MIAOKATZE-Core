package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

/** Leave all block queries intact; only admit actual outward Y faces within a frozen meshing scope. */
@Mixin(RenderBlocks.class)
public abstract class MixinSliceRenderBlocks {

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z"))
    private boolean core$boundaryFace(Block block, IBlockAccess world, int x, int y, int z, int side,
        Operation<Boolean> original) {
        return SliceMeshes.opensFace(world, x, y, z, side) || original.call(block, world, x, y, z, side);
    }
}
