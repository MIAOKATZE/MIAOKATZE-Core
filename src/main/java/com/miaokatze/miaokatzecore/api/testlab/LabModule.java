// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.testlab;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.world.World;

/** Explicitly enabled adapter. Implementations own all business state and world identity checks. */
public interface LabModule {

    String id();

    boolean enabled();

    List<LabCase> cases();

    /** First argument is the normalized action; implementations preserve their existing command semantics. */
    void execute(ICommandSender sender, String[] args);

    /** Optional request correlation while retaining adapters written against the two-argument contract. */
    default void execute(ICommandSender sender, String[] args, String requestId) {
        execute(sender, args);
    }

    String status();

    /** Module-authoritative facts. Acceptance is not completion; unknown fields must stay unknown. */
    default java.util.Map<String, Object> snapshot() {
        java.util.Map<String, Object> facts = new java.util.LinkedHashMap<String, Object>();
        facts.put("legacyStatus", status());
        return java.util.Collections.unmodifiableMap(facts);
    }

    /** Must check the exact owned world, never a dimension number alone. */
    boolean ownsWorld(World world);
}
