// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.client.miaovision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** No graphics context: exercises the production admission decision with observable query boundaries. */
public final class SceneTargetPolicyContracts {

    private SceneTargetPolicyContracts() {}

    public static int run() {
        SceneTargetPolicy.Target target = SceneTargetPolicy.inspect(
            () -> true,
            () -> { throw new AssertionError("SDL must be rejected before fabricated capabilities are read"); },
            name -> { throw new AssertionError("SDL must not issue unsupported integer queries"); });
        check(target == SceneTargetPolicy.Target.UNAVAILABLE, "SDL is unknown, not proven MRT or GL_NONE");
        check(target.reason != null && target.reason.contains("SDL GPU"), "skipped scene has a backend diagnostic");

        // The actual selected OpenGL backend takes this path even if SDL was requested in configuration.
        List<Integer> queries = new ArrayList<>();
        IntUnaryOperator single = name -> {
            queries.add(name);
            if (name == 0x8824) return 8;
            return name == 0x8825 ? 0x8ce3 /* COLOR_ATTACHMENT3 routed to output zero */ : 0;
        };
        check(
            SceneTargetPolicy.inspect(() -> false, () -> true, single) == SceneTargetPolicy.Target.SINGLE_COLOR,
            "OpenGL admits a single active output without assuming attachment zero");
        check(
            queries.equals(Arrays.asList(0x8825, 0x8824, 0x8826, 0x8827, 0x8828, 0x8829, 0x882a, 0x882b, 0x882c)),
            "all advertised output slots are checked, including the last");

        target = SceneTargetPolicy.inspect(() -> false, () -> true, name -> {
            if (name != 0x8825) throw new AssertionError("disabled output zero is rejected immediately");
            return 0;
        });
        check(target == SceneTargetPolicy.Target.UNSUPPORTED, "real GL_NONE remains rejected");
        check(target.reason != null, "known unsupported target retains a diagnostic");

        check(SceneTargetPolicy.inspect(() -> false, () -> true, name -> {
            if (name == 0x8824) return 8;
            return name == 0x8825 || name == 0x882c ? 0x8ce0 : 0;
        }) == SceneTargetPolicy.Target.UNSUPPORTED, "MRT at the last output remains rejected");
        check(
            SceneTargetPolicy.inspect(
                () -> false,
                () -> false,
                name -> { throw new AssertionError("pre-GL20 contexts do not support indexed draw-buffer queries"); })
                == SceneTargetPolicy.Target.SINGLE_COLOR,
            "legacy single-output admission remains unchanged");
        return 8;
    }

    public static void main(String[] args) {
        System.out.println("SCENE_TARGET_POLICY=PASS checks=" + run());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
