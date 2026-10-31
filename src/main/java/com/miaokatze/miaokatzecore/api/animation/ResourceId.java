package com.miaokatze.miaokatzecore.api.animation;

/**
 * Namespaced logical identity. Syntax validation does not grant resource
 * access.
 */
public final class ResourceId {
    private final String text;

    private ResourceId(String text) {
        this.text = text;
    }

    public static ResourceId parse(String text) {
        if (text == null || text.length() > 192 || !text.matches("[a-z0-9_]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid namespaced resource ID");
        String path = text.substring(text.indexOf(':') + 1);
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//"))
            throw new IllegalArgumentException("invalid resource path");
        for (String part : path.split("/"))
            if (part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("resource traversal");
        return new ResourceId(text);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ResourceId && text.equals(((ResourceId) other).text);
    }
}
