import java.util.Arrays;
import java.util.Collections;

import com.miaokatze.miaokatzecore.api.animation.AnimationV1;
import com.miaokatze.miaokatzecore.api.animation.ResourceId;
import com.miaokatze.miaokatzecore.api.animation.SampledFrame;
import com.miaokatze.miaokatzecore.api.animation.Sampler;
import com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec;
import com.miaokatze.miaokatzecore.api.animation.TimelineTimebase;
import com.miaokatze.miaokatzecore.api.animation.VisualClip;
import com.miaokatze.miaokatzecore.api.animation.VisualClipCodec;
import com.miaokatze.miaokatzecore.api.animation.VisualFrame;
import com.miaokatze.miaokatzecore.api.animation.VisualPlan;

/** Independent editor/export client of the one SDK; no shadow Core types. */
public final class GtudkSdkConsumer {

    public static void main(String[] args) {
        AnimationV1.Track track = new AnimationV1.Track("opacity", "ring", AnimationV1.Property.OPACITY,
            Arrays.asList(new AnimationV1.Key(0, 0, AnimationV1.Interpolation.LINEAR),
                new AnimationV1.Key(1000, 1, AnimationV1.Interpolation.LINEAR)));
        AnimationV1.Document draft = new AnimationV1.Document("gtudk:smoke/ring", 0, 1000, Long.MAX_VALUE,
            Collections.singletonList(track), Collections.<AnimationV1.Cue>emptyList(),
            Collections.<ResourceId>emptySet());
        AnimationV1.Document reopened = StrictJsonCodec.decode(StrictJsonCodec.encode(draft));
        long exportTime = TimelineTimebase.toMillisecondsFloor(TimelineTimebase.frameTimeFloor(30, 60, 1));
        SampledFrame frame = Sampler.sample(reopened, exportTime);
        if (reopened.seed != Long.MAX_VALUE || frame.value("opacity") != 0.5
            || !frame.sampledFrom(reopened) || frame.sampledFrom(draft)) {
            throw new AssertionError("Editor/export identity, seed or absolute time changed");
        }
        if (!StrictJsonCodec.contentHash(draft).equals(StrictJsonCodec.contentHash(reopened))) {
            throw new AssertionError("Save/reopen changed canonical content");
        }
        VisualClip visual = new VisualClip(reopened,
            Collections.singletonList(new VisualClip.Target("ring", "paint",
                new VisualClip.Transform(0, 0, 0, 0, 0, 0, 1, 1, 1, 1))),
            Collections.singletonList(new VisualClip.Material("paint", 0x33aaff, 1.0)));
        VisualClip visualReopened = VisualClipCodec.decode(VisualClipCodec.encode(visual));
        if (!VisualClipCodec.contentHash(visual).equals(VisualClipCodec.contentHash(visualReopened))) {
            throw new AssertionError("Visual save/reopen changed canonical content");
        }
        VisualFrame visualFrame = VisualPlan.compile(visual).evaluate(frame);
        if (!visualFrame.sampledFrom(visual) || visualFrame.sampledFrom(visualReopened)) {
            throw new AssertionError("Visual frame lost exact complete definition identity");
        }
        System.out.println("PASS GTUDK SDK scalar/visual save/reopen, exact seed, absolute time and definition identity");
    }
}
