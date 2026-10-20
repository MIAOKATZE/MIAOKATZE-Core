package com.miaokatze.miaokatzecore.client.camera.slice;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Worker-local render view. Removed layers cannot darken a newly exposed cut face through native AO. */
final class SliceBlockAccess implements IBlockAccess {

    private final IBlockAccess original;
    private final HeightSlice slice;
    private final int renderedY;

    SliceBlockAccess(IBlockAccess original, HeightSlice slice, int renderedY) {
        this.original = original;
        this.slice = slice;
        this.renderedY = renderedY;
    }

    private boolean hidden(int y) {
        return SliceMeshRules.hidesNeighbor(slice, renderedY, y);
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return hidden(y) ? Blocks.air : original.getBlock(x, y, z);
    }

    @Override
    public TileEntity getTileEntity(int x, int y, int z) {
        return hidden(y) ? null : original.getTileEntity(x, y, z);
    }

    @Override
    public int getLightBrightnessForSkyBlocks(int x, int y, int z, int minimum) {
        return SliceLightingRules.brightness(slice, y, original.getLightBrightnessForSkyBlocks(x, y, z, minimum));
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
        return hidden(y) ? 0 : original.getBlockMetadata(x, y, z);
    }

    @Override
    public int isBlockProvidingPowerTo(int x, int y, int z, int side) {
        return hidden(y) ? 0 : original.isBlockProvidingPowerTo(x, y, z, side);
    }

    @Override
    public boolean isAirBlock(int x, int y, int z) {
        return hidden(y) || original.isAirBlock(x, y, z);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return original.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getHeight() {
        return original.getHeight();
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return original.extendedLevelsInChunkCache();
    }

    @Override
    public boolean isSideSolid(int x, int y, int z, ForgeDirection side, boolean defaultValue) {
        return !hidden(y) && original.isSideSolid(x, y, z, side, defaultValue);
    }
}
