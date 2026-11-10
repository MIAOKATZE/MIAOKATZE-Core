package com.miaokatze.miaokatzecore.client.render.internal;

import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/** Optional symbols remain lazy; compatibility-profile users do not require Angelica. No backend bindings. */
public final class AngelicaFfpBridge {

    private AngelicaFfpBridge() {}

    public static boolean supported() {
        return com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled()
            && com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.isEnabled()
            && com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.getInstance()
                .isActive()
            && com.gtnewhorizons.angelica.glsm.GLSMQueries.glGetInteger(GL20.GL_CURRENT_PROGRAM) == 0
            && !com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder.isActive();
    }

    public static void restore() {
        // Restore the logical FFP format through its own public preparation, never bind a generated program behind
        // GLSM's cache. The physical generated program is not the application's logical CURRENT_PROGRAM zero.
        com.gtnewhorizons.angelica.glsm.ffp.ShaderManager.getInstance()
            .preDraw(com.gtnewhorizons.angelica.glsm.GLStateManager.ctx());
    }

    public static int clientTexture() {
        // CLIENT_ACTIVE_TEXTURE is legacy state and is not routed by glGetInteger in the core profile.
        return GL13.GL_TEXTURE0 + com.gtnewhorizons.angelica.glsm.GLStateManager.ctx().clientActiveTextureUnit;
    }

    public static boolean textured() {
        // These are the texture targets represented by Angelica 2.2.43's FFP FragmentKey. Querying legacy
        // MAX_TEXTURE_UNITS or TEXTURE_CUBE_MAP via the core-profile driver is not a valid substitute.
        int units = com.gtnewhorizons.angelica.glsm.GLStateManager.MAX_TEXTURE_UNITS;
        if (units <= 0) throw new IllegalStateException("Angelica FFP texture-unit count is invalid");
        com.gtnewhorizons.angelica.glsm.states.TextureUnitArray textures = com.gtnewhorizons.angelica.glsm.GLStateManager
            .ctx().textures;
        for (int unit = 0; unit < units; unit++) if (textures.getTexture1DStates(unit)
            .isEnabled()
            || textures.getTextureUnitStates(unit)
                .isEnabled()
            || textures.getTexture3DStates(unit)
                .isEnabled())
            return true;
        return false;
    }
}
