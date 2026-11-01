package com.miaokatze.miaokatzecore.api.animation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict scalar V0 JSON with bounded input, canonical encoding and no resource
 * loading.
 */
public final class StrictJsonCodec {

    public static final int MAX_BYTES = 2097152, MAX_DEPTH = 32, MAX_STRING = 512, MAX_NUMBER = 32, MAX_VALUES = 262144;
    public static final String FORMAT = "core.animation.scalar";

    private StrictJsonCodec() {}

    public static final class CodecException extends IllegalArgumentException {

        public final String path, code;

        CodecException(String path, String code, String message) {
            super(bounded(path, 512) + " [" + code + "] " + bounded(message, 256));
            this.path = bounded(path, 512);
            this.code = code;
        }
    }

    private static String bounded(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    static CodecException error(String path, String code, String message) {
        return new CodecException(path, code, message);
    }

    /**
     * Reads at most MAX_BYTES plus one overflow-detection byte before parsing. The
     * caller owns the stream and any I/O timeout; this method never closes it or
     * allocates DTOs before EOF.
     */
    public static AnimationV1.Document decode(InputStream input) throws IOException {
        return decode(readBounded(input));
    }

    static byte[] readBounded(InputStream input) throws IOException {
        if (input == null) throw error("$", "BYTE_LIMIT", "missing input");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int total = 0;
        while (true) {
            int read = input.read(chunk, 0, Math.min(chunk.length, MAX_BYTES - total + 1));
            if (read == -1) return bytes.toByteArray();
            if (read == 0) throw new IOException("input stream made no progress");
            total += read;
            if (total > MAX_BYTES) throw error("$", "BYTE_LIMIT", "oversized input");
            bytes.write(chunk, 0, read);
        }
    }

    public static AnimationV1.Document decode(byte[] bytes) {
        return decodeParsed(parse(bytes));
    }

    static Object parse(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw error("$", "BYTE_LIMIT", "missing or oversized input");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw error("$", "UTF8", "malformed UTF-8");
        }
        return new Parser(text).parse();
    }

    static AnimationV1.Document decodeParsed(Object parsed) {
        Map<String, Object> root = object(parsed, "$");
        fields(root, "$", "format", "schemaVersion", "id", "durationMs", "seed", "resources", "tracks", "cues");
        if (!FORMAT.equals(string(root.get("format"), "$.format")))
            throw error("$.format", "FORMAT", "not the V0 scalar format");
        Map<String, Object> version = object(root.get("schemaVersion"), "$.schemaVersion");
        fields(version, "$.schemaVersion", "major", "minor");
        if (integer(version.get("major"), "$.schemaVersion.major") != 1)
            throw error("$.schemaVersion.major", "MAJOR", "unsupported major");
        long minor = integer(version.get("minor"), "$.schemaVersion.minor");
        if (minor != 0) throw error("$.schemaVersion.minor", "MINOR", "unsupported minor; no silent feature loss");
        Set<ResourceId> resources = new LinkedHashSet<ResourceId>();
        List<Object> rs = array(root.get("resources"), "$.resources", AnimationV1.MAX_RESOURCES);
        for (int i = 0; i < rs.size(); i++) {
            String path = "$.resources[" + i + "]";
            ResourceId resource = resource(string(rs.get(i), path), path);
            if (!resources.add(resource)) throw error(path, "DUPLICATE_RESOURCE", "duplicate identity");
        }
        List<AnimationV1.Track> tracks = new ArrayList<AnimationV1.Track>();
        List<Object> ts = array(root.get("tracks"), "$.tracks", AnimationV1.MAX_TRACKS);
        int total = 0;
        for (int i = 0; i < ts.size(); i++) {
            String p = "$.tracks[" + i + "]";
            Map<String, Object> t = object(ts.get(i), p);
            fields(t, p, "id", "targetId", "property", "keys");
            AnimationV1.Property property = enumeration(AnimationV1.Property.class, t.get("property"), p + ".property");
            List<Object> ks = array(t.get("keys"), p + ".keys", AnimationV1.MAX_KEYS_PER_TRACK);
            total += ks.size();
            if (total > AnimationV1.MAX_KEYS) throw error(p + ".keys", "TOTAL_KEYS", "too many keys");
            List<AnimationV1.Key> keys = new ArrayList<AnimationV1.Key>();
            for (int k = 0; k < ks.size(); k++) {
                String q = p + ".keys[" + k + "]";
                Map<String, Object> key = object(ks.get(k), q);
                fields(key, q, "timeMs", "value", "interpolation");
                try {
                    keys.add(
                        new AnimationV1.Key(
                            integer(key.get("timeMs"), q + ".timeMs"),
                            decimal(key.get("value"), q + ".value"),
                            enumeration(
                                AnimationV1.Interpolation.class,
                                key.get("interpolation"),
                                q + ".interpolation")));
                } catch (CodecException e) {
                    throw e;
                } catch (IllegalArgumentException e) {
                    throw error(q, "KEY", e.getMessage());
                }
            }
            try {
                tracks.add(
                    new AnimationV1.Track(
                        string(t.get("id"), p + ".id"),
                        string(t.get("targetId"), p + ".targetId"),
                        property,
                        keys));
            } catch (CodecException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                throw error(p, "TRACK", e.getMessage());
            }
        }
        List<AnimationV1.Cue> cues = new ArrayList<AnimationV1.Cue>();
        List<Object> cs = array(root.get("cues"), "$.cues", AnimationV1.MAX_CUES);
        for (int i = 0; i < cs.size(); i++) {
            String p = "$.cues[" + i + "]";
            Map<String, Object> cue = object(cs.get(i), p);
            fields(cue, p, "id", "timeMs", "kind", "latePolicy", "validForMs", "resource");
            ResourceId resource = cue.get("resource") == null ? null
                : resource(string(cue.get("resource"), p + ".resource"), p + ".resource");
            try {
                cues.add(
                    new AnimationV1.Cue(
                        string(cue.get("id"), p + ".id"),
                        integer(cue.get("timeMs"), p + ".timeMs"),
                        enumeration(AnimationV1.CueKind.class, cue.get("kind"), p + ".kind"),
                        enumeration(AnimationV1.LatePolicy.class, cue.get("latePolicy"), p + ".latePolicy"),
                        integer(cue.get("validForMs"), p + ".validForMs"),
                        resource));
            } catch (CodecException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                throw error(p, "CUE", e.getMessage());
            }
        }
        try {
            return new AnimationV1.Document(
                string(root.get("id"), "$.id"),
                0,
                integer(root.get("durationMs"), "$.durationMs"),
                integer(root.get("seed"), "$.seed"),
                tracks,
                cues,
                resources);
        } catch (CodecException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw error("$", "DOCUMENT", e.getMessage());
        }
    }

    public static byte[] encode(AnimationV1.Document document) {
        if (document == null) throw new IllegalArgumentException("document");
        StringBuilder out = new StringBuilder();
        out.append("{\"format\":");
        quote(out, FORMAT);
        out.append(",\"schemaVersion\":{\"major\":1,\"minor\":0},\"id\":");
        quote(out, document.id);
        out.append(",\"durationMs\":")
            .append(document.durationMs)
            .append(",\"seed\":")
            .append(document.seed)
            .append(",\"resources\":[");
        List<String> resources = new ArrayList<String>();
        for (ResourceId r : document.resources) resources.add(r.toString());
        Collections.sort(resources);
        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) out.append(',');
            quote(out, resources.get(i));
        }
        out.append("],\"tracks\":[");
        List<AnimationV1.Track> tracks = new ArrayList<AnimationV1.Track>(document.tracks);
        Collections.sort(tracks, new Comparator<AnimationV1.Track>() {

            public int compare(AnimationV1.Track a, AnimationV1.Track b) {
                return a.id.compareTo(b.id);
            }
        });
        for (int i = 0; i < tracks.size(); i++) {
            if (i > 0) out.append(',');
            AnimationV1.Track t = tracks.get(i);
            out.append("{\"id\":");
            quote(out, t.id);
            out.append(",\"targetId\":");
            quote(out, t.targetId);
            out.append(",\"property\":");
            quote(out, t.property.name());
            out.append(",\"keys\":[");
            for (int k = 0; k < t.keys.size(); k++) {
                if (k > 0) out.append(',');
                AnimationV1.Key key = t.keys.get(k);
                out.append("{\"timeMs\":")
                    .append(key.timeMs)
                    .append(",\"value\":")
                    .append(Double.toString(key.value))
                    .append(",\"interpolation\":");
                quote(out, key.interpolation.name());
                out.append('}');
            }
            out.append("]}");
        }
        out.append("],\"cues\":[");
        List<AnimationV1.Cue> cues = new ArrayList<AnimationV1.Cue>(document.cues);
        Collections.sort(cues, new Comparator<AnimationV1.Cue>() {

            public int compare(AnimationV1.Cue a, AnimationV1.Cue b) {
                return a.id.compareTo(b.id);
            }
        });
        for (int i = 0; i < cues.size(); i++) {
            if (i > 0) out.append(',');
            AnimationV1.Cue c = cues.get(i);
            out.append("{\"id\":");
            quote(out, c.id);
            out.append(",\"timeMs\":")
                .append(c.timeMs)
                .append(",\"kind\":");
            quote(out, c.kind.name());
            out.append(",\"latePolicy\":");
            quote(out, c.latePolicy.name());
            out.append(",\"validForMs\":")
                .append(c.validForMs)
                .append(",\"resource\":");
            if (c.resource == null) out.append("null");
            else quote(out, c.resource.toString());
            out.append('}');
        }
        out.append("]}");
        byte[] bytes = out.toString()
            .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw error("$", "BYTE_LIMIT", "canonical document exceeds portable limit");
        return bytes;
    }

    public static String contentHash(AnimationV1.Document document) {
        return sha256(encode(document));
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes);
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) {
                out.append(Character.forDigit((b >>> 4) & 15, 16));
                out.append(Character.forDigit(b & 15, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\')
                .append(c);
            else if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else out.append(c);
        }
        out.append('"');
    }

    private static ResourceId resource(String value, String path) {
        try {
            return ResourceId.parse(value);
        } catch (IllegalArgumentException e) {
            throw error(path, "RESOURCE_ID", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map)) throw error(path, "TYPE", "object required");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value, String path, int max) {
        if (!(value instanceof List)) throw error(path, "TYPE", "array required");
        List<Object> a = (List<Object>) value;
        if (a.size() > max) throw error(path, "COUNT", "array count exceeded");
        return a;
    }

    static String string(Object value, String path) {
        if (!(value instanceof String)) throw error(path, "TYPE", "string required");
        return (String) value;
    }

    static void fields(Map<String, Object> map, String path, String... fields) {
        Set<String> expected = new HashSet<String>(Arrays.asList(fields));
        for (String key : map.keySet())
            if (!expected.contains(key)) throw error(path + "." + key, "UNKNOWN_FIELD", "unsupported field");
        for (String field : fields)
            if (!map.containsKey(field)) throw error(path + "." + field, "MISSING_FIELD", "required field");
    }

    private static <T extends Enum<T>> T enumeration(Class<T> type, Object value, String path) {
        try {
            return Enum.valueOf(type, string(value, path));
        } catch (CodecException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw error(path, "ENUM", "unsupported value");
        }
    }

    static long integer(Object value, String path) {
        if (!(value instanceof Numeric)) throw error(path, "TYPE", "integer required");
        String token = ((Numeric) value).text;
        if (!token.matches("-?(0|[1-9][0-9]*)")) throw error(path, "INTEGER", "integer token required");
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            throw error(path, "INTEGER_RANGE", "integer overflow");
        }
    }

    static double decimal(Object value, String path) {
        if (!(value instanceof Numeric)) throw error(path, "TYPE", "number required");
        double n = Double.parseDouble(((Numeric) value).text);
        if (Double.isNaN(n) || Double.isInfinite(n)) throw error(path, "FINITE", "non-finite number");
        return n;
    }

    private static final class Numeric {

        final String text;

        Numeric(String text) {
            this.text = text;
        }
    }

    private static final class Parser {

        final String text;
        int at, values;

        Parser(String text) {
            this.text = text;
        }

        Object parse() {
            Object value = value("$", 0);
            space();
            if (at != text.length()) throw fail("$", "TRAILING", "trailing bytes");
            return value;
        }

        CodecException fail(String p, String c, String m) {
            return error(p, c, m + " at character " + at);
        }

        void space() {
            while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\n'
                || text.charAt(at) == '\r'
                || text.charAt(at) == '\t')) at++;
        }

        Object value(String path, int depth) {
            space();
            if (depth > MAX_DEPTH) throw fail(path, "DEPTH", "nesting limit");
            if (++values > MAX_VALUES) throw fail(path, "VALUES", "total values limit");
            if (at >= text.length()) throw fail(path, "EOF", "missing value");
            char c = text.charAt(at);
            if (c == '{') {
                at++;
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                space();
                if (take('}')) return map;
                do {
                    space();
                    if (at >= text.length() || text.charAt(at) != '"')
                        throw fail(path, "KEY", "quoted object key required");
                    String key = string(path);
                    space();
                    expect(':', path);
                    if (map.containsKey(key)) throw fail(path + "." + key, "DUPLICATE_KEY", "duplicate object key");
                    map.put(key, value(path + "." + key, depth + 1));
                    space();
                    if (take('}')) return map;
                    expect(',', path);
                } while (true);
            }
            if (c == '[') {
                at++;
                List<Object> list = new ArrayList<Object>();
                space();
                if (take(']')) return list;
                do {
                    if (list.size() >= 65536) throw fail(path, "ARRAY_LIMIT", "generic array limit");
                    list.add(value(path + "[" + list.size() + "]", depth + 1));
                    space();
                    if (take(']')) return list;
                    expect(',', path);
                } while (true);
            }
            if (c == '"') return string(path);
            if (c == 't') {
                literal("true", path);
                return Boolean.TRUE;
            }
            if (c == 'f') {
                literal("false", path);
                return Boolean.FALSE;
            }
            if (c == 'n') {
                literal("null", path);
                return null;
            }
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) {
                at++;
                if (at - start > MAX_NUMBER) throw fail(path, "NUMBER_LIMIT", "numeric token limit");
            }
            String token = text.substring(start, at);
            if (!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
                throw fail(path, "NUMBER", "invalid JSON number");
            return new Numeric(token);
        }

        void literal(String token, String p) {
            if (!text.startsWith(token, at)) throw fail(p, "LITERAL", "invalid literal");
            at += token.length();
        }

        boolean take(char c) {
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        void expect(char c, String p) {
            if (!take(c)) throw fail(p, "SYNTAX", "expected " + c);
        }

        String string(String p) {
            expect('"', p);
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') {
                    for (int i = 0; i < out.length(); i++) {
                        char x = out.charAt(i);
                        if (Character.isHighSurrogate(x)) {
                            if (++i >= out.length() || !Character.isLowSurrogate(out.charAt(i)))
                                throw fail(p, "SURROGATE", "unpaired surrogate");
                        } else if (Character.isLowSurrogate(x)) throw fail(p, "SURROGATE", "unpaired surrogate");
                    }
                    return out.toString();
                }
                if (c < 32) throw fail(p, "CONTROL", "unescaped control character");
                if (c == '\\') {
                    if (at >= text.length()) throw fail(p, "ESCAPE", "incomplete escape");
                    c = text.charAt(at++);
                    switch (c) {
                        case '"':
                        case '\\':
                        case '/':
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'u':
                            if (at + 4 > text.length()) throw fail(p, "ESCAPE", "short unicode escape");
                            int n = 0;
                            for (int k = 0; k < 4; k++) {
                                char hex = text.charAt(at++);
                                int v = hex >= '0' && hex <= '9' ? hex - '0'
                                    : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                                        : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                                if (v < 0) throw fail(p, "ESCAPE", "bad unicode escape");
                                n = (n << 4) | v;
                            }
                            c = (char) n;
                            break;
                        default:
                            throw fail(p, "ESCAPE", "unsupported escape");
                    }
                }
                out.append(c);
                if (out.length() > MAX_STRING) throw fail(p, "STRING_LIMIT", "string limit");
            }
            throw fail(p, "EOF", "unclosed string");
        }
    }
}
