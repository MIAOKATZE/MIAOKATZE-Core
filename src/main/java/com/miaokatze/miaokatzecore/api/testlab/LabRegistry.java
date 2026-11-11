// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.testlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Passive process registry. Register enabled adapters during initialization; release them at server stop. */
public final class LabRegistry {

    private static final Map<String, LabModule> MODULES = new LinkedHashMap<String, LabModule>();

    private LabRegistry() {}

    public static synchronized void register(LabModule module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Invalid module id: " + id);
        if (!module.enabled()) return;
        LabModule previous = MODULES.get(id);
        if (previous != null && previous != module) throw new IllegalStateException("Duplicate lab module: " + id);
        MODULES.put(id, module);
    }

    public static synchronized LabModule find(String id) {
        return MODULES.get(id.toLowerCase(Locale.ROOT));
    }

    public static synchronized List<LabModule> modules() {
        return Collections.unmodifiableList(new ArrayList<LabModule>(MODULES.values()));
    }

    public static synchronized void unregister(String id) {
        MODULES.remove(id);
        LabEvents.clear(id);
    }

    public static synchronized void clear() {
        MODULES.clear();
        LabEvents.clearAll();
    }
}
