package com.miaokatze.miaokatzecore.api.testlab;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Captures the actual loaded code source, not a guessed mods-directory filename or working checkout. */
public final class ArtifactIdentity {

    private final Map<String, String> values;

    private ArtifactIdentity(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static ArtifactIdentity capture(Class<?> anchor) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("identitySchema", "miaotest-artifact-v1");
        values.put("anchorClass", anchor.getName());
        values.put("codeSource", "unknown");
        values.put("jarSha256", "unknown");
        values.put("sourceCommit", "unknown");
        values.put("sourceDirty", "unknown");
        values.put("implementationVersion", "unknown");
        values.put("artifactKind", "unknown");
        try {
            CodeSource source = anchor.getProtectionDomain()
                .getCodeSource();
            if (source == null || source.getLocation() == null) return new ArtifactIdentity(values);
            values.put(
                "codeSource",
                source.getLocation()
                    .toExternalForm());
            if (!"file".equals(
                source.getLocation()
                    .getProtocol()))
                return new ArtifactIdentity(values);
            Path path = Paths.get(
                source.getLocation()
                    .toURI());
            if (!Files.isRegularFile(path)) {
                values.put("artifactKind", "classes-directory");
                return new ArtifactIdentity(values);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            values.put("jarSha256", hex.toString());
            values.put("artifactKind", "jar");
            try (JarFile jar = new JarFile(path.toFile())) {
                Manifest manifest = jar.getManifest();
                if (manifest != null) {
                    Attributes attributes = manifest.getMainAttributes();
                    copy(values, "sourceCommit", attributes, "Miaokatze-Source-Commit");
                    copy(values, "sourceDirty", attributes, "Miaokatze-Source-Dirty");
                    copy(values, "implementationVersion", attributes, "Implementation-Version");
                }
            }
        } catch (Exception exception) {
            values.put(
                "identityError",
                exception.getClass()
                    .getSimpleName());
        }
        return new ArtifactIdentity(values);
    }

    private static void copy(Map<String, String> values, String key, Attributes attributes, String name) {
        String value = attributes.getValue(name);
        if (value != null && !value.trim()
            .isEmpty()) values.put(key, value);
    }

    public Map<String, String> asMap() {
        return values;
    }
}
