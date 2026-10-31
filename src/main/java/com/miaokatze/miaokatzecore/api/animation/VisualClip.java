package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closed visual profile: unit solid boxes, static TRS, solid unlit materials and explicitly bound scalar channels.
 * No world, model loader, parent graph or editor history is represented here.
 */
public strictfp final class VisualClip {

    public static final int MAX_TARGETS = 100, MAX_MATERIALS = 100;
    public static final double MAX_SCALE = 32;
    public static final String PRIMITIVE = "SOLID_BOX", MATERIAL_KIND = "SOLID_COLOR_UNLIT";

    public final AnimationV1.Document scalar;
    public final List<Target> targets;
    public final List<Material> materials;

    /** Static local pose. Quaternion components are preserved in storage; evaluation normalizes accepted roundoff. */
    public static final class Transform {

        public final double x, y, z, qx, qy, qz, qw, scaleX, scaleY, scaleZ;

        public Transform(double x, double y, double z, double qx, double qy, double qz, double qw,
            double scaleX, double scaleY, double scaleZ) {
            bounded(x, -1000000, 1000000, "position x");
            bounded(y, -1000000, 1000000, "position y");
            bounded(z, -1000000, 1000000, "position z");
            bounded(qx, -1.00001, 1.00001, "quaternion x");
            bounded(qy, -1.00001, 1.00001, "quaternion y");
            bounded(qz, -1.00001, 1.00001, "quaternion z");
            bounded(qw, -1.00001, 1.00001, "quaternion w");
            AnimationV1.require(Math.abs(qx * qx + qy * qy + qz * qz + qw * qw - 1) <= 0.00001,
                "quaternion must be normalized");
            bounded(scaleX, 0, MAX_SCALE, "scale x");
            bounded(scaleY, 0, MAX_SCALE, "scale y");
            bounded(scaleZ, 0, MAX_SCALE, "scale z");
            AnimationV1.require(scaleX > 0 && scaleY > 0 && scaleZ > 0, "static scale must be positive");
            this.x = x;
            this.y = y;
            this.z = z;
            this.qx = qx;
            this.qy = qy;
            this.qz = qz;
            this.qw = qw;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }
    }

    /** Persistent identity and material binding of one unit box centered at the local origin. */
    public static final class Target {

        public final String id, materialId;
        public final Transform transform;

        public Target(String id, String materialId, Transform transform) {
            this.id = AnimationV1.id(id);
            this.materialId = AnimationV1.id(materialId);
            AnimationV1.require(transform != null, "target transform");
            this.transform = transform;
        }
    }

    /** RGB bytes map directly to normalized unlit vertex color. Opacity uses straight alpha, never brightness. */
    public static final class Material {

        public final String id;
        public final int rgb;
        public final double opacity;

        public Material(String id, int rgb, double opacity) {
            this.id = AnimationV1.id(id);
            AnimationV1.require(rgb >= 0 && rgb <= 0xffffff, "RGB must be in [0,16777215]");
            bounded(opacity, 0, 1, "material opacity");
            this.rgb = rgb;
            this.opacity = opacity;
        }
    }

    public VisualClip(AnimationV1.Document scalar, List<Target> targets, List<Material> materials) {
        AnimationV1.require(scalar != null, "scalar document");
        AnimationV1.require(scalar.cues.isEmpty() && scalar.resources.isEmpty(),
            "visual profile does not support cues or external resources");
        AnimationV1.require(targets != null && targets.size() <= MAX_TARGETS, "target count");
        AnimationV1.require(materials != null && materials.size() <= MAX_MATERIALS, "material count");
        List<Target> targetCopy = new ArrayList<>(targets);
        List<Material> materialCopy = new ArrayList<>(materials);
        Map<String, Material> materialIds = new LinkedHashMap<>();
        for (Material material : materialCopy) {
            AnimationV1.require(material != null && !materialIds.containsKey(material.id), "duplicate/null material");
            materialIds.put(material.id, material);
        }
        Map<String, Target> targetIds = new LinkedHashMap<>();
        for (Target target : targetCopy) {
            AnimationV1.require(target != null && !targetIds.containsKey(target.id), "duplicate/null target");
            AnimationV1.require(materialIds.containsKey(target.materialId), "undeclared target material");
            targetIds.put(target.id, target);
        }
        for (AnimationV1.Track track : scalar.tracks) {
            Target target = targetIds.get(track.targetId);
            AnimationV1.require(target != null, "undeclared scalar target");
            AnimationV1.require(supports(track.property), "unsupported visual property: " + track.property);
            if (track.property == AnimationV1.Property.SCALE) {
                double largestAxis = Math.max(target.transform.scaleX,
                    Math.max(target.transform.scaleY, target.transform.scaleZ));
                // All V0 interpolators remain within endpoint extrema, so keys bound the complete curve.
                for (AnimationV1.Key key : track.keys)
                    AnimationV1.require(largestAxis * key.value <= MAX_SCALE, "sampled scale exceeds visual bound");
            }
        }
        this.scalar = scalar;
        this.targets = Collections.unmodifiableList(targetCopy);
        this.materials = Collections.unmodifiableList(materialCopy);
    }

    public static boolean supports(AnimationV1.Property property) {
        if (property == null) return false;
        switch (property) {
            case X:
            case Y:
            case Z:
            case SCALE:
            case ROTATION_DEGREES:
            case OPACITY:
            case VISIBILITY:
                return true;
            default:
                return false;
        }
    }

    private static void bounded(double value, double minimum, double maximum, String name) {
        AnimationV1.require(Double.isFinite(value) && value >= minimum && value <= maximum, name + " range");
    }
}
