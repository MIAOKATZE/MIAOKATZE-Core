package com.miaokatze.miaokatzecore.bootstrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** Early bootstrap intentionally has no client class references. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("MIAOKATZE Core Camera")
@IFMLLoadingPlugin.TransformerExclusions("com.miaokatze.miaokatzecore.bootstrap")
public final class CoreLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.miaokatzecore.camera.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        if (!FMLLaunchHandler.side()
            .isClient()) return Collections.emptyList();
        return Arrays.asList(
            "camera.MixinEntityRenderer",
            "camera.MixinRenderGlobal",
            "camera.MixinMinecraft",
            "camera.MixinMovementInputFromOptions",
            "camera.MixinKeyBinding",
            "camera.MixinEntity",
            "camera.MixinCeleritasViewport",
            "camera.MixinAngelicaRenderSectionManager",
            "camera.MixinAngelicaChunkRenderer");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
