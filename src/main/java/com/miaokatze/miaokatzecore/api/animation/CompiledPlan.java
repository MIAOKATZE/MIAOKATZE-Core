package com.miaokatze.miaokatzecore.api.animation;

/**
 * A portable scalar plan. Compilation verifies canonical encoding fits the
 * interchange budget and freezes its digest. Scalar tracks are already
 * validated and ordered in the document; no resource resolution, graph
 * execution, simulation or side effect is performed here.
 */
public final class CompiledPlan {

    public final AnimationV1.Document document;
    public final String contentHash;

    private CompiledPlan(AnimationV1.Document document, String contentHash) {
        this.document = document;
        this.contentHash = contentHash;
    }

    public static CompiledPlan compile(AnimationV1.Document document) {
        return new CompiledPlan(document, StrictJsonCodec.contentHash(document));
    }

    /**
     * Pure point evaluation; no clock advance or cue selection, including export
     * and reverse.
     */
    public SampledFrame sample(EvaluationInput input) {
        if (input == null) throw new IllegalArgumentException("evaluation input");
        return Sampler.sample(document, input.localTimeMs);
    }
}
