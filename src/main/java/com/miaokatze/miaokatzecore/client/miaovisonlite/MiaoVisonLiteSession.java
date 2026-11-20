// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovisonlite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.ContextCapabilities;

import com.miaokatze.miaokatzecore.client.render.ScreenLineStream;

/** Owns one viewport's retained geometry and background preparation. Use on the viewport's render thread. */
public final class MiaoVisonLiteSession<K, R> implements AutoCloseable {

    private final Map<K, R> regions = new LinkedHashMap<>();
    private final MiaoVisonLiteOutline outline = new MiaoVisonLiteOutline();
    private ScreenLineStream lines;
    private MiaoVisonLiteSubmission<?> submission;
    private MiaoVisonLiteTopology<K, ?, ?> topology;
    private MiaoVisonLiteTarget target;
    private ContextCapabilities context;

    /** A change requires the adapter to retire its region payloads before recording or drawing any more frames. */
    public boolean contextChanged() {
        ContextCapabilities current = MiaoVisonLiteMaterialBatch.boundContext();
        if (current == null) return false;
        boolean changed = context != null && current != context;
        context = current;
        return changed;
    }

    /** The adapter stores its region payloads in source order and may invalidate affected regions independently. */
    public Map<K, R> regions() {
        return regions;
    }

    public <C, F> MiaoVisonLiteTopology<K, C, F> prepare(Map<K, List<C>> source,
        MiaoVisonLiteTopology.View<C, F> view) {
        cancelPreparation();
        MiaoVisonLiteTopology<K, C, F> next = MiaoVisonLiteTopology.start(source, view);
        topology = next;
        return next;
    }

    public void cancelPreparation() {
        if (topology != null) topology.close();
        topology = null;
    }

    public void replaceSubmission(MiaoVisonLiteSubmission<?> next) {
        if (submission != null) submission.close();
        submission = next;
    }

    public ScreenLineStream lines() {
        if (lines == null) lines = new ScreenLineStream();
        return lines;
    }

    public MiaoVisonLiteOutline outline() {
        return outline;
    }

    public MiaoVisonLiteTarget target(int width, int height) {
        if (target == null || !target.sizeMatches(width, height)) {
            releaseTarget();
            target = new MiaoVisonLiteTarget(width, height);
        }
        return target;
    }

    public void releaseTarget() {
        if (target != null) target.close();
        target = null;
    }

    /** Retire the submitted frame while retaining detached region input for an inexpensive reopen. */
    public void clearPresentation() {
        replaceSubmission(null);
        outline.close();
        if (lines != null) lines.close();
        lines = null;
    }

    @Override
    public void close() {
        cancelPreparation();
        clearPresentation();
        releaseTarget();
        regions.clear();
    }
}
