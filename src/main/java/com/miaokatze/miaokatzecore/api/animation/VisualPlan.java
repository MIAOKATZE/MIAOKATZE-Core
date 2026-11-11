// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.miaokatzecore.api.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compiles fixed target/channel bindings once. Evaluation consumes the existing scalar sampler's exact frame. */
public strictfp final class VisualPlan {

    public final VisualClip clip;
    public final String contentHash;
    private final List<Binding> bindings;

    private static final class Binding {

        final VisualClip.Target target;
        final VisualClip.Material material;
        final Map<AnimationV1.Property, String> tracks = new EnumMap<>(AnimationV1.Property.class);

        Binding(VisualClip.Target target, VisualClip.Material material) {
            this.target = target;
            this.material = material;
        }

        double value(SampledFrame frame, AnimationV1.Property property, double defaultValue) {
            String track = tracks.get(property);
            return track == null ? defaultValue : frame.value(track);
        }
    }

    private VisualPlan(VisualClip clip) {
        this.clip = clip;
        contentHash = VisualClipCodec.contentHash(clip);
        Map<String, VisualClip.Material> materials = new HashMap<>();
        for (VisualClip.Material material : clip.materials) materials.put(material.id, material);
        Map<String, Binding> byTarget = new HashMap<>();
        List<VisualClip.Target> ordered = new ArrayList<>(clip.targets);
        Collections.sort(ordered, (a, b) -> a.id.compareTo(b.id));
        bindings = new ArrayList<>(ordered.size());
        for (VisualClip.Target target : ordered) {
            Binding binding = new Binding(target, materials.get(target.materialId));
            bindings.add(binding);
            byTarget.put(target.id, binding);
        }
        for (AnimationV1.Track track : clip.scalar.tracks)
            byTarget.get(track.targetId).tracks.put(track.property, track.id);
    }

    public static VisualPlan compile(VisualClip clip) {
        if (clip == null) throw new IllegalArgumentException("visual clip");
        return new VisualPlan(clip);
    }

    /** Pure binding evaluation. Seek, reverse and export have no event channel or host callback. */
    public VisualFrame evaluate(SampledFrame frame) {
        if (frame == null || !frame.sampledFrom(clip.scalar))
            throw new IllegalArgumentException("scalar frame belongs to another definition");
        List<VisualFrame.TargetPose> poses = new ArrayList<>(bindings.size());
        for (Binding binding : bindings) {
            VisualClip.Transform base = binding.target.transform;
            double x = binding.value(frame, AnimationV1.Property.X, base.x);
            double y = binding.value(frame, AnimationV1.Property.Y, base.y);
            double z = binding.value(frame, AnimationV1.Property.Z, base.z);
            double scale = binding.value(frame, AnimationV1.Property.SCALE, 1);
            double angle = binding.value(frame, AnimationV1.Property.ROTATION_DEGREES, 0);
            double halfAngle = angle * (StrictMath.PI / 360);
            double sine = StrictMath.sin(halfAngle), cosine = StrictMath.cos(halfAngle);
            // Quaternion product base * local-Z rotation; normalize accepted storage roundoff only at evaluation.
            double qx = base.qx * cosine + base.qy * sine;
            double qy = base.qy * cosine - base.qx * sine;
            double qz = base.qz * cosine + base.qw * sine;
            double qw = base.qw * cosine - base.qz * sine;
            double norm = StrictMath.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            double opacity = binding.material.opacity * binding.value(frame, AnimationV1.Property.OPACITY, 1);
            double scaleX = base.scaleX * scale, scaleY = base.scaleY * scale, scaleZ = base.scaleZ * scale;
            boolean visible = scaleX > 0 && scaleY > 0
                && scaleZ > 0
                && opacity > 0
                && binding.value(frame, AnimationV1.Property.VISIBILITY, 1) == 1;
            poses.add(
                new VisualFrame.TargetPose(
                    binding.target.id,
                    binding.target.materialId,
                    x,
                    y,
                    z,
                    qx / norm,
                    qy / norm,
                    qz / norm,
                    qw / norm,
                    scaleX,
                    scaleY,
                    scaleZ,
                    binding.material.rgb,
                    opacity,
                    visible));
        }
        return new VisualFrame(clip, frame.localTimeMs, poses);
    }
}
