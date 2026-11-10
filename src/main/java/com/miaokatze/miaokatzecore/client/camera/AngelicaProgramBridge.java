package com.miaokatze.miaokatzecore.client.camera;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceShaders;

/** Optional client-only adapter, reached only after the Angelica presence gate. */
final class AngelicaProgramBridge {

    private static boolean installed;

    private AngelicaProgramBridge() {}

    static void install() {
        if (installed) return;
        GLSMHooks.PROGRAM_DELETE.addListener(event -> SliceShaders.forgetProgram(event.program));
        installed = true;
    }
}
