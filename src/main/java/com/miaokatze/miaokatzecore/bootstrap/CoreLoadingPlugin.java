package com.miaokatze.miaokatzecore.bootstrap;

import java.util.ArrayList;
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
        List<String> mixins = new ArrayList<>(
            Arrays.asList(
                "camera.MixinEntityRenderer",
                "camera.MixinRenderGlobal",
                "camera.MixinMinecraft",
                "camera.MixinMovementInputFromOptions",
                "camera.MixinKeyBinding",
                "camera.MixinEntity",
                "camera.MixinCeleritasViewport",
                "camera.MixinAngelicaRenderSectionManager",
                "camera.MixinAngelicaChunkRenderer",
                "camera.slice.shader.MixinSliceShaderReload"));
        // Probe the resource without loading an optional renderer class during early bootstrap.
        if (getClass().getClassLoader()
            .getResource("com/gtnewhorizons/angelica/rendering/celeritas/AngelicaRenderSectionManager.class") != null) {
            mixins.addAll(
                Arrays.asList(
                    "camera.slice.shader.MixinSliceShaderLoader",
                    "camera.slice.shader.MixinSliceVertexShaderGenerator",
                    "camera.slice.shader.MixinSliceFragmentShaderGenerator",
                    "camera.slice.shader.MixinSliceGeometryShaderGenerator",
                    "camera.slice.shader.MixinSliceShaderManager",
                    "camera.slice.shader.MixinSliceDefaultChunkShaderInterface",
                    "camera.slice.shader.MixinSliceTerrainDomain",
                    "camera.slice.shader.MixinSliceTileEntityDomain",
                    "camera.slice.shader.MixinSliceGlStateManager",
                    "camera.slice.shader.MixinSliceGlProgram",
                    "camera.slice.mesh.MixinAngelicaSliceManager",
                    "camera.slice.mesh.MixinAngelicaSliceTask",
                    "camera.slice.mesh.MixinSliceBuildOutput",
                    "camera.slice.mesh.MixinSliceUploadManager",
                    "camera.slice.mesh.MixinSliceRenderBlocks",
                    "camera.slice.mesh.MixinSliceGTWorldContext",
                    "camera.slice.mesh.MixinSliceSortTask"));
        }
        return mixins;
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
