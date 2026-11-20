// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.client.renderer.Tessellator;

import com.miaokatze.miaokatzecore.client.render.TessellatorSlice;

/** Replays complete quad batches in caller order, retaining only bounded, reusable geometry. */
public final class MiaoVisonLiteSubmission<B extends MiaoVisonLiteSubmission.Batch> implements AutoCloseable {

    // Raw ints exclude the additional scalar state and range objects.
    private static final int MAX_RETAINED_INTS = 16 * 1024 * 1024;
    // Bound resident vertices to roughly 24 MiB, recording at most 768 KiB per frame.
    private static final int MAX_RESIDENT_BATCHES = 128;
    private static final int MAX_FRAME_RECORDINGS = 4;

    public static final int QUADS_PER_BATCH = 1023;

    private final List<B> batches;
    private final BiConsumer<Tessellator, B> emit;
    private final BiConsumer<MiaoVisonLiteMesh.Builder, B> emitMesh;
    private boolean unsupported, drawn;
    private int retainedInts, residentBatches;
    private int sliceCaptures, sliceHits, sliceReplayedQuads, sliceUnsupported, sliceCapturedInts;
    private int residentCaptures, residentDraws, directMeshUploads, directMeshDraws;

    public MiaoVisonLiteSubmission(List<B> batches, BiConsumer<Tessellator, B> emit,
        BiConsumer<MiaoVisonLiteMesh.Builder, B> emitMesh) {
        this.batches = batches;
        this.emit = emit;
        this.emitMesh = emitMesh;
    }

    public boolean draw(Tessellator tess, boolean allowCapture, boolean allowResident) {
        sliceCaptures = sliceHits = sliceReplayedQuads = sliceUnsupported = sliceCapturedInts = 0;
        residentCaptures = residentDraws = directMeshUploads = directMeshDraws = 0;
        MiaoVisonLiteMaterialBatch.count("stationary-material-pass");
        if (batches.isEmpty()) MiaoVisonLiteMaterialBatch.count("empty-material-pass");
        if (!allowCapture) MiaoVisonLiteMaterialBatch.count("capture-deferred");
        if (!allowResident) MiaoVisonLiteMaterialBatch.count("transient-material-pass");
        MiaoVisonLiteMaterialBatch.Frame residentFrame = !allowResident || !drawn || batches.isEmpty() ? null
            : MiaoVisonLiteMaterialBatch.currentFrame(tess);
        boolean direct = MiaoVisonLiteMesh.supported(residentFrame);
        int recordings = 0;
        for (int at = 0; at < batches.size(); at++) {
            B batch = batches.get(at);
            if (residentFrame != null && batch.hasResident()
                && (batch.mesh != null ? !batch.mesh.belongsTo(residentFrame)
                    : !batch.resident.belongsTo(residentFrame))) {
                batch.releaseResident();
                residentBatches--;
            }
            tess.startDrawingQuads();
            if (direct && batch.mesh != null && batch.mesh.drawInEmptyBatch(tess, residentFrame)) {
                residentDraws++;
                directMeshDraws++;
                continue;
            }
            if (batch.resident != null && batch.resident.replay(tess, residentFrame)) {
                residentDraws++;
                continue;
            }
            boolean stable = batch.stable && batch.valid();
            boolean canRecord = allowCapture && stable
                && !batch.hasResident()
                && residentBatches < MAX_RESIDENT_BATCHES
                && recordings < MAX_FRAME_RECORDINGS;
            if (direct && canRecord) {
                MiaoVisonLiteMesh.Builder vertices = MiaoVisonLiteMesh.liveBuilder(tess, batch.quads * 4);
                emitMesh.accept(vertices, batch);
                MiaoVisonLiteMesh mesh = MiaoVisonLiteMesh
                    .upload(residentFrame, vertices, MiaoVisonLiteMesh.Quads.LIVE);
                try {
                    if (mesh.drawInEmptyBatch(tess, residentFrame)) {
                        batch.captureVersions();
                        batch.mesh = mesh;
                        discardSlice(batch);
                        recordings++;
                        residentBatches++;
                        residentCaptures++;
                        residentDraws++;
                        directMeshUploads++;
                        directMeshDraws++;
                        continue;
                    }
                } finally {
                    if (batch.mesh != mesh) mesh.close();
                }
            }
            if (stable && !unsupported) {
                if (batch.slice != null && batch.slice.replay(tess)) {
                    sliceHits++;
                    sliceReplayedQuads += batch.quads;
                } else if (allowCapture
                    && retainedInts - (batch.slice == null ? 0 : batch.slice.retainedInts()) + batch.quads * 32
                        <= MAX_RETAINED_INTS) {
                            // Capture executes the append-only callback once, including on unsupported layouts.
                            discardSlice(batch);
                            batch.slice = TessellatorSlice.capture(tess, () -> emit.accept(tess, batch));
                            if (batch.slice == null) {
                                unsupported = true;
                                MiaoVisonLiteMaterialBatch.count("unsupported-cpu-slice");
                                sliceUnsupported++;
                            } else {
                                retainedInts += batch.slice.retainedInts();
                                batch.captureVersions();
                                sliceCaptures++;
                                sliceCapturedInts += batch.slice.retainedInts();
                            }
                        } else
                    emit.accept(tess, batch);
            } else emit.accept(tess, batch);
            if (!stable) MiaoVisonLiteMaterialBatch.count("live-material-batch");
            if (residentFrame != null && canRecord) {
                batch.resident = MiaoVisonLiteMaterialBatch.record(tess, residentFrame, batch.quads * 4);
                if (batch.resident != null) {
                    batch.captureVersions();
                    discardSlice(batch);
                    recordings++;
                    residentBatches++;
                    residentCaptures++;
                    batch.resident.draw();
                    residentDraws++;
                    continue;
                }
            }
            tess.draw();
        }
        drawn = !batches.isEmpty();
        return drawn;
    }

    private void discardSlice(B batch) {
        if (batch.slice == null) return;
        retainedInts -= batch.slice.retainedInts();
        batch.slice = null;
    }

    /** Call after refreshing the adapter's atlas dependencies, before replaying any retained batches. */
    public void invalidateChangedDependencies() {
        for (B batch : batches) if ((batch.slice != null || batch.hasResident()) && !batch.current()) {
            discardSlice(batch);
            if (batch.releaseResident()) residentBatches--;
        }
    }

    public int sliceCaptures() {
        return sliceCaptures;
    }

    public int sliceHits() {
        return sliceHits;
    }

    public int sliceReplayedQuads() {
        return sliceReplayedQuads;
    }

    public int sliceUnsupported() {
        return sliceUnsupported;
    }

    public int sliceCapturedInts() {
        return sliceCapturedInts;
    }

    public int residentCaptures() {
        return residentCaptures;
    }

    public int residentDraws() {
        return residentDraws;
    }

    public int directMeshUploads() {
        return directMeshUploads;
    }

    public int directMeshDraws() {
        return directMeshDraws;
    }

    @Override
    public void close() {
        for (B batch : batches) {
            batch.releaseResident();
            batch.slice = null;
        }
        retainedInts = residentBatches = 0;
        drawn = false;
    }

    /** A range of whole quads and the consumer's conditions for reusing its atlas coordinates. */
    public abstract static class Batch {

        final int quads;
        final boolean stable;
        TessellatorSlice slice;
        MiaoVisonLiteMaterialBatch resident;
        MiaoVisonLiteMesh mesh;

        protected Batch(int quads, boolean stable) {
            if (quads <= 0 || quads > QUADS_PER_BATCH) throw new IllegalArgumentException("Invalid quad batch size");
            this.quads = quads;
            this.stable = stable;
        }

        protected abstract boolean valid();

        protected abstract boolean current();

        protected abstract void captureVersions();

        final boolean hasResident() {
            return mesh != null || resident != null;
        }

        final boolean releaseResident() {
            boolean present = hasResident();
            if (mesh != null) {
                mesh.close();
                mesh = null;
            }
            if (resident != null) {
                resident.close();
                resident = null;
            }
            return present;
        }
    }
}
