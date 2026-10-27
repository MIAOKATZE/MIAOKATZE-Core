package com.miaokatze.miaokatzecore.client.miaovision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;

/** Pure, stable ordering policy: native opaque geometry first, fades from far to near in the actual camera. */
final class SceneRenderOrder {

    private SceneRenderOrder() {}

    static List<Entry> prepare(SceneSnapshot snapshot, ScenePresentation presentation, SceneCamera camera) {
        List<Entry> opaque = new ArrayList<>(), faded = new ArrayList<>();
        for (SceneBody body : snapshot.bodies) {
            if (!presentation.visible(body.id)) continue;
            float alpha = presentation.opacity(body.id);
            if (!Float.isFinite(alpha)) throw new IllegalArgumentException("non-finite opacity");
            alpha = Math.max(0, Math.min(1, alpha));
            if (alpha == 0) continue;
            Entry entry = new Entry(body, alpha, eyeZ(body, camera));
            (alpha == 1 ? opaque : faded).add(entry);
        }
        // glOrtho(...,-1024,1024) maps greater eye Z nearer. List.sort keeps equal-depth order stable.
        faded.sort(Comparator.comparingDouble(entry -> entry.eyeZ));
        opaque.addAll(faded);
        return opaque;
    }

    static double eyeZ(SceneBody body, SceneCamera camera) {
        double yaw = Math.toRadians(camera.yawDegrees), pitch = Math.toRadians(camera.pitchDegrees);
        return -Math.cos(pitch) * Math.sin(yaw) * body.x + Math.sin(pitch) * body.y
            + Math.cos(pitch) * Math.cos(yaw) * body.z;
    }

    static final class Entry {

        final SceneBody body;
        final float opacity;
        final double eyeZ;

        Entry(SceneBody body, float opacity, double eyeZ) {
            this.body = body;
            this.opacity = opacity;
            this.eyeZ = eyeZ;
        }
    }
}
