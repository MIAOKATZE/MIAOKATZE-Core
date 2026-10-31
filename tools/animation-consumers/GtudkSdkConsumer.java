import java.util.Arrays;
import java.util.Collections;

import com.miaokatze.miaokatzecore.api.animation.AnimationV1;
import com.miaokatze.miaokatzecore.api.animation.ResourceId;
import com.miaokatze.miaokatzecore.api.animation.SampledFrame;
import com.miaokatze.miaokatzecore.api.animation.Sampler;
import com.miaokatze.miaokatzecore.api.animation.StrictJsonCodec;
import com.miaokatze.miaokatzecore.api.animation.TimelineTimebase;

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
        System.out.println("PASS GTUDK SDK save/reopen, exact seed, 60fps sample and definition identity");
    }
}
