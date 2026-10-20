package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import net.minecraft.client.multiplayer.WorldClient;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager", remap = false)
public abstract class MixinAngelicaSliceManager {

    @Shadow
    @Final
    private WorldClient world;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void core$attachSliceManager(CallbackInfo ci) {
        SliceMeshes.managerCreated(world, (RenderSectionManager) (Object) this);
    }

    @Inject(method = "createRebuildTask", at = @At("RETURN"), remap = false)
    private void core$freezeTaskSlice(RenderSection section, int frame,
        CallbackInfoReturnable<ChunkBuilderTask<ChunkBuildOutput>> cir) {
        SliceMeshes.taskCreated((RenderSectionManager) (Object) this, section, cir.getReturnValue());
    }
}
