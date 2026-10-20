package com.miaokatze.miaokatzecore.api.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Instance-local registration with retryable removal and reentrant callback protection.
 * Callbacks execute under this catalog monitor; never wait for another thread that needs this catalog.
 */
public final class ExtensionCatalog<D> {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<String> installing = new HashSet<>();

    public synchronized List<D> snapshot() {
        List<D> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) result.add(entry.descriptor);
        return Collections.unmodifiableList(result);
    }

    public synchronized Handle<D> install(String id, D descriptor, Supplier<? extends Runnable> installer) {
        if (id == null || !ID.matcher(id)
            .matches()) throw new IllegalArgumentException("Invalid extension id: " + id);
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(installer, "installer");
        if (entries.containsKey(id) || !installing.add(id))
            throw new IllegalArgumentException("Extension id already in use: " + id);
        try {
            Runnable remove = Objects.requireNonNull(installer.get(), "installer returned null");
            Entry entry = new Entry(id, descriptor, remove);
            entries.put(id, entry);
            return entry;
        } finally {
            installing.remove(id);
        }
    }

    public interface Handle<D> extends AutoCloseable {

        D descriptor();

        boolean active();

        @Override
        void close();
    }

    private final class Entry implements Handle<D> {

        private final String id;
        private final D descriptor;
        private final Runnable remove;
        private boolean active = true;
        private boolean closing;

        private Entry(String id, D descriptor, Runnable remove) {
            this.id = id;
            this.descriptor = descriptor;
            this.remove = remove;
        }

        @Override
        public D descriptor() {
            return descriptor;
        }

        @Override
        public boolean active() {
            synchronized (ExtensionCatalog.this) {
                return active;
            }
        }

        @Override
        public void close() {
            synchronized (ExtensionCatalog.this) {
                if (!active || closing) return;
                closing = true;
                try {
                    remove.run();
                    if (entries.get(id) == this) entries.remove(id);
                    active = false;
                } finally {
                    closing = false;
                }
            }
        }
    }
}
