import java.util.Collections;

import com.miaokatze.miaokatzecore.api.animation.ResourceId;
import com.miaokatze.miaokatzecore.api.animation.runtime.FeatureAdmission;
import com.miaokatze.miaokatzecore.api.animation.runtime.MetricWindow;
import com.miaokatze.miaokatzecore.api.animation.runtime.RuntimeGenerations;

/** Independent presentation/diagnostic client; no authority, world or network mutations. */
public final class OutpostSdkConsumer {

    public static void main(String[] args) {
        ResourceId scalar = ResourceId.parse("core:scalar_v0");
        FeatureAdmission.Decision supported = FeatureAdmission.evaluate(Collections.singleton(scalar),
            Collections.<ResourceId>emptySet(), Collections.singleton(scalar));
        FeatureAdmission.Decision rejected = FeatureAdmission.evaluate(Collections.singleton(scalar),
            Collections.<ResourceId>emptySet(), Collections.<ResourceId>emptySet());
        if (supported.status != FeatureAdmission.Status.SUPPORTED
            || rejected.status != FeatureAdmission.Status.REJECTED) {
            throw new AssertionError("Required capability must reject rather than silently disappear");
        }
        RuntimeGenerations generations = new RuntimeGenerations();
        RuntimeGenerations.Ticket old = generations.ticket(RuntimeGenerations.Purpose.PRESENTATION);
        generations.replacedInstance();
        if (generations.accepts(old)) throw new AssertionError("Stale presentation accepted");
        MetricWindow window = new MetricWindow(1000, 8);
        if (window.snapshot(0).p95 != -1) throw new AssertionError("Missing sample reported as zero");
        window.record(1, 0);
        if (window.snapshot(1).p95 != 0) throw new AssertionError("True zero lost");
        generations.close();
        System.out.println("PASS Outpost SDK capability gate, generation fence and missing/zero distinction");
    }
}
