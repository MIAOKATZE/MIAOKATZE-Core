// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.preview;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeBlendTessellator;
import com.miaokatze.miaokatzecore.client.render.TessellatorSlice;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Render-thread cache for a sparse, already populated preview world. The caller supplies a revision for world changes,
 * selects block renderers whose world pass only emits static geometry, and owns the renderer, GL batch and overlays.
 * Positions use GTNHLib's coordinate packing. Cached and live positions retain their original order within each pass.
 */
public final class PreviewGeometryCache {

    private static final int MAX_SEGMENTS = 4096;
    private static final int MAX_SLICE_POSITIONS = 2048;
    private static final int MAX_POSITIONS = 2_000_000;
    private static final int MAX_VERTEX_INTS = 16_777_216;
    private static WeakReference<PreviewGeometryCache> active = new WeakReference<>(null);
    private static boolean registered;
    private static long lifecycleGeneration;

    private final World world;
    private final LongSupplier sourceRevision;
    private final Predicate<Block> cacheableBlock;
    private long revision = Long.MIN_VALUE;
    private long generation = Long.MIN_VALUE;
    private final List<Segment> segments = new ArrayList<>();
    private LongCollection collection;
    private int retainedInts;
    private boolean overflow;

    public PreviewGeometryCache(World world, LongSupplier sourceRevision, Predicate<Block> cacheableBlock) {
        this.world = Objects.requireNonNull(world, "world");
        this.sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        this.cacheableBlock = Objects.requireNonNull(cacheableBlock, "cacheableBlock");
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new Lifecycle());
            registered = true;
        }
        PreviewGeometryCache previous = active.get();
        if (previous != null) previous.invalidate();
        active = new WeakReference<>(this);
    }

    public void invalidate() {
        revision = Long.MIN_VALUE;
        generation = Long.MIN_VALUE;
        collection = null;
        segments.clear();
        retainedInts = 0;
        overflow = false;
    }

    private void refresh() {
        PreviewGeometryCache previous = active.get();
        if (previous != this) {
            if (previous != null) previous.invalidate();
            active = new WeakReference<>(this);
        }
        long current = sourceRevision.getAsLong();
        if (revision == current && generation == lifecycleGeneration) return;
        invalidate();
        revision = current;
        generation = lifecycleGeneration;
    }

    /** Prepare the caller's stable, unmodified position set. Call invalidate if that set is edited in place. */
    public boolean prepare(LongCollection positions) {
        refresh();
        if (collection == positions) return !overflow;
        collection = positions;
        segments.clear();
        retainedInts = 0;
        overflow = false;
        if (positions.size() > MAX_POSITIONS) return !(overflow = true);
        Map<Block, Boolean> staticBlocks = new IdentityHashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            Segment tail = null;
            LongIterator iterator = positions.iterator();
            while (iterator.hasNext()) {
                long position = iterator.nextLong();
                Block block = block(position);
                if (block == Blocks.air) continue;
                boolean cacheable = staticBlocks.computeIfAbsent(block, cacheableBlock::test);
                if (cacheable && !block.canRenderInPass(pass)) continue;
                if (tail == null || tail.cacheable != cacheable
                    || cacheable && tail.positions.size() == MAX_SLICE_POSITIONS) {
                    tail = new Segment(pass, cacheable);
                    segments.add(tail);
                    if (segments.size() > MAX_SEGMENTS) {
                        segments.clear();
                        return !(overflow = true);
                    }
                }
                tail.positions.add(position);
            }
        }
        return true;
    }

    public void append(Tessellator tessellator, RenderBlocks renderer, boolean allFaces) {
        long renderRevision = revision;
        for (Segment segment : segments) {
            if (segment.cacheable && !segment.captureRejected
                && (segment.slice != null || retainedInts < MAX_VERTEX_INTS)
                && renderRevision == sourceRevision.getAsLong()
                && !renderer.hasOverrideBlockTexture() && !renderer.lockBlockBounds
                && supports(tessellator)) {
                setup(renderer, allFaces);
                if (segment.slice != null && segment.input.matches(renderer) && segment.slice.replay(tessellator)) {
                    segment.output.restore(renderer);
                    continue;
                }
                if (segment.slice != null) {
                    retainedInts -= segment.slice.retainedInts();
                    segment.slice = null;
                }
                RenderState input = new RenderState(renderer);
                TessellatorSlice captured = TessellatorSlice
                    .capture(tessellator, () -> render(segment, renderer, allFaces));
                if (captured != null && captured.retainedInts() <= MAX_VERTEX_INTS - retainedInts) {
                    segment.slice = captured;
                    segment.input = input;
                    segment.output = new RenderState(renderer);
                    retainedInts += captured.retainedInts();
                } else segment.captureRejected = true;
            } else render(segment, renderer, allFaces);
        }
    }

    private void render(Segment segment, RenderBlocks renderer, boolean allFaces) {
        LongIterator iterator = segment.positions.iterator();
        while (iterator.hasNext()) {
            long position = iterator.nextLong();
            Block block = block(position);
            if (block == Blocks.air || !block.canRenderInPass(segment.pass)) continue;
            setup(renderer, allFaces);
            renderer.renderBlockByRenderType(
                block,
                CoordinatePacker.unpackX(position),
                CoordinatePacker.unpackY(position),
                CoordinatePacker.unpackZ(position));
        }
    }

    private Block block(long position) {
        return world.getBlock(
            CoordinatePacker.unpackX(position),
            CoordinatePacker.unpackY(position),
            CoordinatePacker.unpackZ(position));
    }

    private void setup(RenderBlocks renderer, boolean allFaces) {
        renderer.blockAccess = world;
        renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
        renderer.renderAllFaces = allFaces;
    }

    public static boolean supports(Tessellator tessellator) {
        return tessellator.getClass() == Tessellator.class
            && (!Loader.isModLoaded("angelica") || Angelica.allows(tessellator));
    }

    private static final class Angelica {

        private static boolean allows(Tessellator tessellator) {
            return tessellator instanceof StateAwareTessellator
                && !((StateAwareTessellator) tessellator).angelica$isCeleritasMeshing()
                && tessellator instanceof BiomeBlendTessellator
                && ((BiomeBlendTessellator) tessellator).angelica$getBiomeBlender() == null;
        }
    }

    private static final class Segment {

        private final int pass;
        private final boolean cacheable;
        private final LongArrayList positions = new LongArrayList();
        private TessellatorSlice slice;
        private RenderState input, output;
        // Retry only after the collection/model/resource generation changes. Capture already drew this frame.
        private boolean captureRejected;

        private Segment(int pass, boolean cacheable) {
            this.pass = pass;
            this.cacheable = cacheable;
        }
    }

    // Static GT casings and Chisel can alter these renderer flags while emitting their vertices.
    private static final class RenderState {

        private final boolean ao, tint, allFaces;
        private final int forgePass;

        private RenderState(RenderBlocks renderer) {
            ao = renderer.enableAO;
            tint = renderer.useInventoryTint;
            allFaces = renderer.renderAllFaces;
            forgePass = ForgeHooksClient.getWorldRenderPass();
        }

        private void restore(RenderBlocks renderer) {
            renderer.enableAO = ao;
            renderer.useInventoryTint = tint;
            renderer.renderAllFaces = allFaces;
        }

        private boolean matches(RenderBlocks renderer) {
            return ao == renderer.enableAO && tint == renderer.useInventoryTint && allFaces == renderer.renderAllFaces
                && forgePass == ForgeHooksClient.getWorldRenderPass();
        }
    }

    public static final class Lifecycle {

        @SubscribeEvent
        public void guiChanged(GuiOpenEvent event) {
            invalidateAll();
        }

        @SubscribeEvent
        public void atlasChanged(TextureStitchEvent.Post event) {
            invalidateAll();
        }

        private static void invalidateAll() {
            lifecycleGeneration++;
            PreviewGeometryCache cache = active.get();
            if (cache != null) cache.invalidate();
        }
    }
}
