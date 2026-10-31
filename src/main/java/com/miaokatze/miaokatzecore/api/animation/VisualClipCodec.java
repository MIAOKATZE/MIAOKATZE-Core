package com.miaokatze.miaokatzecore.api.animation;

import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.array;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.decimal;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.error;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.fields;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.integer;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.object;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.quote;
import static com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec.string;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Strict visual 1.0 envelope. The single bounded parser preserves all scalar numeric tokens and duplicate keys. */
public final class VisualClipCodec {

    public static final String FORMAT = "core.animation.visual";
    public static final int MAX_BYTES = StrictJsonCodec.MAX_BYTES;

    private VisualClipCodec() {}

    /** Caller owns the stream and timeouts. At most MAX_BYTES plus one overflow-detection byte is read. */
    public static VisualClip decode(InputStream input) throws IOException {
        return decode(StrictJsonCodec.readBounded(input));
    }

    public static VisualClip decode(byte[] bytes) {
        Map<String, Object> root = object(StrictJsonCodec.parse(bytes), "$");
        fields(root, "$", "format", "schemaVersion", "scalar", "targets", "materials");
        if (!FORMAT.equals(string(root.get("format"), "$.format")))
            throw error("$.format", "FORMAT", "not the visual format");
        Map<String, Object> version = object(root.get("schemaVersion"), "$.schemaVersion");
        fields(version, "$.schemaVersion", "major", "minor");
        if (integer(version.get("major"), "$.schemaVersion.major") != 1)
            throw error("$.schemaVersion.major", "MAJOR", "unsupported visual major");
        if (integer(version.get("minor"), "$.schemaVersion.minor") != 0)
            throw error("$.schemaVersion.minor", "MINOR", "unsupported visual minor");
        AnimationV1.Document scalar;
        try {
            scalar = StrictJsonCodec.decodeParsed(root.get("scalar"));
        } catch (StrictJsonCodec.CodecException invalid) {
            throw error("$.scalar" + invalid.path.substring(1), invalid.code, "invalid embedded scalar document");
        }
        if (!scalar.cues.isEmpty() || !scalar.resources.isEmpty())
            throw error("$.scalar", "UNSUPPORTED", "visual 1.0 requires empty cues and resources");
        for (int i = 0; i < scalar.tracks.size(); i++) {
            if (!VisualClip.supports(scalar.tracks.get(i).property))
                throw error("$.scalar.tracks[" + i + "].property", "UNSUPPORTED", "unsupported visual property");
        }

        List<VisualClip.Material> materials = new ArrayList<>();
        List<Object> materialValues = array(root.get("materials"), "$.materials", VisualClip.MAX_MATERIALS);
        for (int i = 0; i < materialValues.size(); i++) {
            String path = "$.materials[" + i + "]";
            Map<String, Object> material = object(materialValues.get(i), path);
            fields(material, path, "id", "kind", "rgb", "opacity");
            if (!VisualClip.MATERIAL_KIND.equals(string(material.get("kind"), path + ".kind")))
                throw error(path + ".kind", "UNSUPPORTED", "only solid unlit materials are supported");
            long rgb = integer(material.get("rgb"), path + ".rgb");
            if (rgb < 0 || rgb > 0xffffff) throw error(path + ".rgb", "RANGE", "RGB outside [0,16777215]");
            try {
                materials.add(new VisualClip.Material(string(material.get("id"), path + ".id"),
                    (int) rgb, decimal(material.get("opacity"), path + ".opacity")));
            } catch (StrictJsonCodec.CodecException invalid) {
                throw invalid;
            } catch (IllegalArgumentException invalid) {
                throw error(path, "MATERIAL", invalid.getMessage());
            }
        }
        List<VisualClip.Target> targets = new ArrayList<>();
        List<Object> targetValues = array(root.get("targets"), "$.targets", VisualClip.MAX_TARGETS);
        for (int i = 0; i < targetValues.size(); i++) {
            String path = "$.targets[" + i + "]";
            Map<String, Object> target = object(targetValues.get(i), path);
            fields(target, path, "id", "primitive", "materialId", "transform");
            if (!VisualClip.PRIMITIVE.equals(string(target.get("primitive"), path + ".primitive")))
                throw error(path + ".primitive", "UNSUPPORTED", "only SOLID_BOX is supported");
            Map<String, Object> transform = object(target.get("transform"), path + ".transform");
            fields(transform, path + ".transform", "position", "rotationQuaternion", "scale");
            double[] position = vector(transform.get("position"), path + ".transform.position", 3);
            double[] rotation = vector(transform.get("rotationQuaternion"), path + ".transform.rotationQuaternion", 4);
            double[] scale = vector(transform.get("scale"), path + ".transform.scale", 3);
            VisualClip.Transform pose;
            try {
                pose = new VisualClip.Transform(position[0], position[1], position[2],
                    rotation[0], rotation[1], rotation[2], rotation[3], scale[0], scale[1], scale[2]);
            } catch (IllegalArgumentException invalid) {
                throw error(path + ".transform", "TRANSFORM", invalid.getMessage());
            }
            try {
                targets.add(new VisualClip.Target(string(target.get("id"), path + ".id"),
                    string(target.get("materialId"), path + ".materialId"), pose));
            } catch (StrictJsonCodec.CodecException invalid) {
                throw invalid;
            } catch (IllegalArgumentException invalid) {
                throw error(path, "TARGET", invalid.getMessage());
            }
        }
        try {
            return new VisualClip(scalar, targets, materials);
        } catch (IllegalArgumentException invalid) {
            throw error("$", "CLIP", invalid.getMessage());
        }
    }

    public static byte[] encode(VisualClip clip) {
        if (clip == null) throw new IllegalArgumentException("visual clip");
        StringBuilder out = new StringBuilder();
        out.append("{\"format\":");
        quote(out, FORMAT);
        out.append(",\"schemaVersion\":{\"major\":1,\"minor\":0},\"scalar\":");
        out.append(new String(StrictJsonCodec.encode(clip.scalar), StandardCharsets.UTF_8));
        out.append(",\"targets\":[");
        List<VisualClip.Target> targets = new ArrayList<>(clip.targets);
        Collections.sort(targets, (a, b) -> a.id.compareTo(b.id));
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) out.append(',');
            VisualClip.Target target = targets.get(i);
            VisualClip.Transform transform = target.transform;
            out.append("{\"id\":");
            quote(out, target.id);
            out.append(",\"primitive\":");
            quote(out, VisualClip.PRIMITIVE);
            out.append(",\"materialId\":");
            quote(out, target.materialId);
            out.append(",\"transform\":{\"position\":");
            vector(out, transform.x, transform.y, transform.z);
            out.append(",\"rotationQuaternion\":");
            vector(out, transform.qx, transform.qy, transform.qz, transform.qw);
            out.append(",\"scale\":");
            vector(out, transform.scaleX, transform.scaleY, transform.scaleZ);
            out.append("}}");
        }
        out.append("],\"materials\":[");
        List<VisualClip.Material> materials = new ArrayList<>(clip.materials);
        Collections.sort(materials, (a, b) -> a.id.compareTo(b.id));
        for (int i = 0; i < materials.size(); i++) {
            if (i > 0) out.append(',');
            VisualClip.Material material = materials.get(i);
            out.append("{\"id\":");
            quote(out, material.id);
            out.append(",\"kind\":");
            quote(out, VisualClip.MATERIAL_KIND);
            out.append(",\"rgb\":").append(material.rgb).append(",\"opacity\":").append(material.opacity).append('}');
        }
        out.append("]}");
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw error("$", "BYTE_LIMIT", "canonical visual clip exceeds portable limit");
        return bytes;
    }

    public static String contentHash(VisualClip clip) {
        return StrictJsonCodec.sha256(encode(clip));
    }

    private static double[] vector(Object value, String path, int length) {
        List<Object> values = array(value, path, length);
        if (values.size() != length) throw error(path, "COUNT", "wrong vector length");
        double[] result = new double[length];
        for (int i = 0; i < length; i++) result[i] = decimal(values.get(i), path + "[" + i + "]");
        return result;
    }

    private static void vector(StringBuilder out, double... values) {
        out.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        out.append(']');
    }
}
