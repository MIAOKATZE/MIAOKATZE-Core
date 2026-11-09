package com.miaokatze.miaokatzecore.client.camera;

import java.util.concurrent.atomic.AtomicBoolean;

import com.gtnewhorizons.angelica.event.RenderHandEvent;

/** Native Angelica event dispatch, without an OpenGL context or Minecraft instance. */
public final class AngelicaHandContracts {

    public static void main(String[] args) {
        AtomicBoolean hidden = new AtomicBoolean();
        AngelicaHandBridge.install(hidden::get);
        check(!RenderHandEvent.post(), "inactive camera permits Iris hands");
        hidden.set(true);
        check(RenderHandEvent.post(), "camera hideHand cancels native Iris hand event");
        hidden.set(false);
        check(!RenderHandEvent.post(), "released camera permits Iris hands again");
        System.out.println("AngelicaHandContracts: PASS (native event pass, cancel, release)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
