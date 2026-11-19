// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.client.preview;

import java.util.function.LongSupplier;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.miaokatzecore.mixin.render.TessellatorAccessor;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;

/** Owns the selection snapshot and static renderer policy for BlockRenderer's client preview. */
public final class BlockRendererPreviewCache {

    private final PreviewGeometryCache geometry;
    private final LongArrayList selection = new LongArrayList();

    public BlockRendererPreviewCache(World world, LongSupplier revision) {
        geometry = new PreviewGeometryCache(world, revision, BlockRendererPreviewCache::isStatic);
    }

    public static boolean supports(Tessellator tessellator, RenderBlocks renderer) {
        return tessellator instanceof TessellatorAccessor && PreviewGeometryCache.supports(tessellator)
            && renderer.getClass() == RenderBlocks.class
            && !renderer.hasOverrideBlockTexture()
            && !renderer.lockBlockBounds;
    }

    public void invalidate() {
        geometry.invalidate();
        selection.clear();
    }

    public boolean prepare(LongCollection positions) {
        if (positions.size() > PreviewGeometryCache.MAX_POSITIONS) {
            invalidate();
            return false;
        }
        // BlockRenderer exposes the set publicly and edits it in place when changing the search filter.
        if (!sameOrder(selection, positions)) {
            invalidate();
            selection.addAll(positions);
        }
        return geometry.prepare(selection) && geometry.hasStaticSegments();
    }

    public void append(Tessellator tessellator, RenderBlocks renderer, boolean allFaces) {
        geometry.append(tessellator, renderer, allFaces);
    }

    static boolean sameOrder(LongArrayList snapshot, LongCollection positions) {
        if (snapshot.size() != positions.size()) return false;
        LongIterator iterator = positions.iterator();
        for (int index = 0; index < snapshot.size(); index++) {
            if (snapshot.getLong(index) != iterator.nextLong()) return false;
        }
        return true;
    }

    private static boolean isStatic(Block block) {
        if (block == Blocks.stonebrick || block == Blocks.glowstone
            || block == Blocks.glass
            || block == Blocks.stained_glass) return true;
        // Exact classes from GTNH 2.9.0 whose preview model is determined by the tracked world and atlas.
        switch (block.getClass()
            .getName()) {
            case "gregtech.common.blocks.BlockCasings1":
            case "gregtech.common.blocks.BlockCasings2":
            case "gregtech.common.blocks.BlockCasings3":
            case "gregtech.common.blocks.BlockCasings4":
            case "team.chisel.block.BlockCarvable":
            case "team.chisel.block.BlockCarvableColor":
            case "team.chisel.block.BlockConcrete":
                return true;
            default:
                return false;
        }
    }
}
