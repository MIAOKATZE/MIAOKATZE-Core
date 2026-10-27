package com.miaokatze.miaokatzecore.api.miaovision;

/** Read-only presentation channels, independent of physics activation and all inventory decisions. */
public interface ScenePresentation {

    ScenePresentation VISIBLE = new ScenePresentation() {};

    default boolean visible(String id) {
        return true;
    }

    /** Opacity never means successful transfer/removal. Renderer clamps finite values to [0,1]. */
    default float opacity(String id) {
        return 1;
    }
}
