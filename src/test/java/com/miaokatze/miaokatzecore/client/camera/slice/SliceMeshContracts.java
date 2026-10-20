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
}
