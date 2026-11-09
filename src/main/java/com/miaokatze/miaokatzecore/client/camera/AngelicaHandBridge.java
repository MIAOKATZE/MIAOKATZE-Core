package com.miaokatze.miaokatzecore.client.camera;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import com.gtnewhorizons.angelica.event.RenderHandEvent;

/** Optional client-only adapter for Iris's separate hand pass on the GTNH 2.9.0 baseline. */
final class AngelicaHandBridge {

    private AngelicaHandBridge() {}

    static void install() {
        install(WorldCameraHooks::hideHand);
    }

    static void install(BooleanSupplier hideHand) {
        RenderHandEvent.BUS.addListener((Predicate<RenderHandEvent>) event -> hideHand.getAsBoolean());
    }
}
