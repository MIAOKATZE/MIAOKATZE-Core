package com.miaokatze.miaokatzecore.client.camera.slice;

import com.miaokatze.miaokatzecore.api.camera.HeightSlice;

/** Dependency-free native boundary and local invalidation contracts. */
public final class SliceMeshContracts {

    private static int checks;

    private SliceMeshContracts() {}

    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("slice mesh contract " + checks);
    }

    public static void main(String[] args) {
        nativeProgress();
        lighting();
        HeightSlice band = HeightSlice.between(-17, 16, 0);
        check(SliceMeshRules.opensFace(band, -17, -18, 0));
        check(SliceMeshRules.opensFace(band, 15, 16, 1));
        check(!SliceMeshRules.opensFace(band, 14, 15, 1));
        check(!SliceMeshRules.opensFace(band, -18, -19, 0));
        check(!SliceMeshRules.opensFace(band, 15, 16, 2));
        check(!SliceMeshRules.opensFace(band, 15, 17, 1));
        check(!SliceMeshRules.opensFace(HeightSlice.disabled(), 15, 16, 1));
        check(
            !SliceMeshRules
                .opensFace(HeightSlice.above(Integer.MIN_VALUE, 0), Integer.MIN_VALUE, Integer.MAX_VALUE, 0));
        check(SliceMeshRules.affectsSection(HeightSlice.below(16, 0), 0));
        check(SliceMeshRules.affectsSection(HeightSlice.below(16, 0), 16));
        check(!SliceMeshRules.affectsSection(HeightSlice.below(16, 0), -16));
        check(!SliceMeshRules.affectsSection(HeightSlice.below(16, 0), 32));
        check(SliceMeshRules.affectsSection(HeightSlice.above(-17, 0), -32));
        check(SliceMeshRules.affectsSection(HeightSlice.above(-17, 0), -16));
        check(!SliceMeshRules.affectsSection(HeightSlice.above(-17, 0), 0));
        check(SliceMeshRules.affectsSection(HeightSlice.below(16, 14.1), -16));
        check(SliceMeshRules.affectsSection(HeightSlice.below(16, 14.1), 32));
        check(!SliceMeshRules.affectsSection(HeightSlice.disabled(), 0));
        check(SliceMeshRules.affectsSection(HeightSlice.below(Integer.MAX_VALUE, Double.MAX_VALUE), Integer.MIN_VALUE));
        check(SliceMeshRules.sameGeometry(band, HeightSlice.between(-17, 16, 4)));
        check(SliceMeshRules.sameGeometry(HeightSlice.above(-17, 0), HeightSlice.above(-17, .25)));
        check(SliceMeshRules.sameGeometry(HeightSlice.below(16, 0), HeightSlice.below(16, 14.1)));
        check(!SliceMeshRules.sameGeometry(band, HeightSlice.between(-16, 16, 0)));
        check(!SliceMeshRules.sameGeometry(band, HeightSlice.between(-17, 17, 0)));
        check(!SliceMeshRules.sameGeometry(band, HeightSlice.above(-17, 0)));
        check(!SliceMeshRules.sameGeometry(HeightSlice.below(16, 0), HeightSlice.disabled()));
        HeightSlice translucent = HeightSlice.below(16, 1)
            .withFeatheredCaps(true);
        check(SliceMeshRules.opensFace(translucent, 15, 16, 1));
        check(SliceMeshRules.opensFace(translucent, 14, 15, 1));
        check(!SliceMeshRules.opensFace(translucent, 13, 14, 1));
        check(!SliceMeshRules.opensFace(translucent, 14, 15, 0));
        HeightSlice translucentBelow = HeightSlice.above(-17, 1)
            .withFeatheredCaps(true);
        check(SliceMeshRules.opensFace(translucentBelow, -17, -18, 0));
        check(SliceMeshRules.opensFace(translucentBelow, -16, -17, 0));
        check(!SliceMeshRules.opensFace(translucentBelow, -15, -16, 0));
        check(!SliceMeshRules.sameGeometry(translucent, HeightSlice.below(16, 1)));
        check(
            SliceMeshRules.sameGeometry(
                translucent,
                HeightSlice.below(16, .25)
                    .withFeatheredCaps(true)));
        check(
            !SliceMeshRules.sameGeometry(
                translucent,
                HeightSlice.below(16, 1.25)
                    .withFeatheredCaps(true)));
        check(SliceMeshRules.hidesNeighbor(translucent, 14, 15));
        check(SliceMeshRules.hidesNeighbor(translucent, 15, 16));
        check(!SliceMeshRules.hidesNeighbor(translucent, 15, 15));
        check(!SliceMeshRules.hidesNeighbor(translucent, 15, 14));
        check(SliceMeshRules.hidesNeighbor(translucentBelow, -16, -17));
        // The dependency key agrees with actual native face emission, including every side and feather endpoint.
        HeightSlice feathered = HeightSlice.between(-17, 16, 14.1);
        for (int y = -19; y <= 18; y++) {
            for (int neighborY = y - 1; neighborY <= y + 1; neighborY++) {
                for (int side = 0; side < 6; side++) {
                    check(
                        SliceMeshRules.opensFace(band, y, neighborY, side)
                            == SliceMeshRules.opensFace(feathered, y, neighborY, side));
                }
            }
        }
        System.out.println("Slice mesh contracts: " + checks + " checks passed");
    }

    private static void nativeProgress() {
        SliceMeshProgress<Object, Object> progress = new SliceMeshProgress<>();
        Object visible = new Object(), hidden = new Object(), first = new Object(), replacement = new Object();
        java.util.Set<Object> displayed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        displayed.add(visible);
        progress.target(first);
        progress.pending.add(visible);
        progress.pending.add(hidden);
        check(progress.hasRequired(displayed::contains));
        progress.building.put(visible, first);
        check(!progress.shouldSchedule(visible, true, false)); // Correct in-flight work must survive Core's old queue.
        check(progress.hasRequired(displayed::contains)); // Submission is not an upload acknowledgement.
        check(progress.shouldSchedule(visible, false, false)); // Cancelled task, remembered stamp, no output.
        check(!progress.shouldSchedule(visible, false, true)); // Retry budget cannot duplicate an existing queue item.
        check(!progress.acknowledge(visible, first, false)); // Same slice, older native build frame is insufficient.
        check(progress.pending.contains(visible) && progress.building.get(visible) == first);
        check(progress.acknowledge(visible, first, true));
        check(!progress.hasRequired(displayed::contains));
        check(progress.pending.contains(hidden)); // Invisible work remains dirty; it is not pretended uploaded.
        displayed.add(hidden);
        check(progress.hasRequired(displayed::contains)); // Newly visible geometry immediately fences picking again.
        progress.building.put(hidden, first);
        progress.target(replacement);
        check(progress.shouldSchedule(hidden, true, false)); // Obsolete job may be cancelled for the new target.
        check(!progress.acknowledge(hidden, first, true));
        check(progress.hasRequired(displayed::contains)); // A late old upload cannot make the new target ready.
        progress.building.put(hidden, replacement);
        check(progress.acknowledge(hidden, replacement, true));
        check(!progress.hasRequired(displayed::contains));
        progress.pending.add(visible); // Restoration also waits for a real matching native upload.
        Object restore = new Object();
        progress.target(restore);
        check(progress.hasRequired(displayed::contains));
        check(!progress.acknowledge(visible, replacement, true));
        check(progress.acknowledge(visible, restore, true));
        check(!progress.hasRequired(displayed::contains));
        progress.clear();
        check(progress.pending.isEmpty() && progress.building.isEmpty() && progress.installed.isEmpty());
    }

    private static void lighting() {
        HeightSlice original = HeightSlice.below(70, 1)
            .withFeatheredCaps(true);
        HeightSlice lit = original.withLightCompensation(true);
        check(!SliceMeshRules.sameGeometry(original, lit)); // Vertex light changes require a real native rebuild.
        check(
            !SliceMeshRules.sameGeometry(
                HeightSlice.below(70, 1)
                    .withLightCompensation(true),
                HeightSlice.below(70, 4)
                    .withLightCompensation(true)));
        check(SliceLightingRules.brightness(original, 70, 0x200070) == 0x200070);
        check(SliceLightingRules.brightness(lit, 70, 0x200070) == 0xF00070);
        check(SliceLightingRules.brightness(lit, 69, 0x0000A0) == 0xF000A0); // Preserve real block-light bits.
        check(SliceLightingRules.brightness(lit, 60, 0x200070) == 0x200070); // Interior/world light stays local.
        check(SliceLightingRules.removedSample(lit, 70));
        check(SliceLightingRules.removedSample(lit, 69));
        check(!SliceLightingRules.removedSample(lit, 68));
        HeightSlice lower = HeightSlice.above(-17, 1)
            .withFeatheredCaps(true)
            .withLightCompensation(true);
        check(SliceLightingRules.removedSample(lower, -18));
        check(SliceLightingRules.removedSample(lower, -17));
        check(!SliceLightingRules.removedSample(lower, -16));
        check(SliceLightingRules.brightness(lower, -18, 0x300010) == 0xF00010);
        check(
            SliceLightingRules.brightness(
                HeightSlice.disabled()
                    .withLightCompensation(true),
                0,
                0x200070) == 0x200070);
        check(
            !lit.withFeatheredCaps(false)
                .featheredCaps() && lit.withFeatheredCaps(false)
                    .lightCompensation());
        check(
            lit.withLightCompensation(false)
                .equals(original));
    }
}
