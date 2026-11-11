// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.testlab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in observation only; callers must never use publication to decide a production result. */
public final class LabEvents {

    public static final int CAPACITY_PER_MODULE = 256;
    private static final Map<String, ArrayDeque<LabEvent>> EVENTS = new LinkedHashMap<String, ArrayDeque<LabEvent>>();
    private static final Map<String, Long> PUBLISHED = new LinkedHashMap<String, Long>();
    private static long sequence;

    private LabEvents() {}

    public static void publish(String module, String run, String task, String phase, String check, LabResult result,
        String cleanup) {
        LabModule adapter = LabRegistry.find(module);
        if (adapter == null || !adapter.enabled()) {
            clear(module);
            return;
        }
        synchronized (LabEvents.class) {
            Long published = PUBLISHED.get(module);
            PUBLISHED.put(module, published == null ? 1L : published + 1L);
            ArrayDeque<LabEvent> events = EVENTS.get(module);
            if (events == null) {
                events = new ArrayDeque<LabEvent>();
                EVENTS.put(module, events);
            }
            if (events.size() == CAPACITY_PER_MODULE) events.removeFirst();
            events.addLast(new LabEvent(++sequence, module, run, task, phase, check, result, cleanup));
        }
    }

    public static synchronized List<LabEvent> snapshot(String module) {
        ArrayDeque<LabEvent> events = EVENTS.get(module);
        return events == null ? Collections.<LabEvent>emptyList()
            : Collections.unmodifiableList(new ArrayList<LabEvent>(events));
    }

    /** Counts accepted publications, currently retained events and oldest-event evictions since clear. */
    public static synchronized Map<String, Long> stats(String module) {
        long published = PUBLISHED.containsKey(module) ? PUBLISHED.get(module) : 0L;
        long retained = EVENTS.containsKey(module) ? EVENTS.get(module)
            .size() : 0L;
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        values.put("published", published);
        values.put("retained", retained);
        values.put("dropped", published - retained);
        return Collections.unmodifiableMap(values);
    }

    public static synchronized void clear(String module) {
        EVENTS.remove(module);
        PUBLISHED.remove(module);
    }

    public static synchronized void clearAll() {
        EVENTS.clear();
        PUBLISHED.clear();
    }
}
