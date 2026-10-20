package com.miaokatze.miaokatzecore.mixin.camera.slice.mesh;

import java.util.ArrayList;
import java.util.Collection;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderSortTask;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshScheduler;
import com.miaokatze.miaokatzecore.client.camera.slice.SliceMeshes;

import it.unimi.dsi.fastutil.objects.ReferenceSet;

@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager", remap = false)
public abstract class MixinSliceUploadManager implements SliceMeshScheduler {

    @Shadow
    @Final
    protected ReferenceSet<RenderSection> sectionsRequestingUpdate;

    @Override
    public void core$scheduleSliceRebuild(RenderSection section) {
        RenderSectionManager manager = (RenderSectionManager) (Object) this;
        manager.scheduleRebuild(section.getChunkX(), section.getChunkY(), section.getChunkZ(), true);
        // requestUpdate can return false for a previously requested update whose queue entry was consumed/lost.
        if (section.getPendingUpdate() != null) sectionsRequestingUpdate.add(section);
    }

    @ModifyExpressionValue(
        method = "onSectionAdded",
        at = @At(
            value = "NEW",
            target = "(Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegion;III)Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;"),
        remap = false)
    private RenderSection core$trackNewBoundarySection(RenderSection section) {
        return SliceMeshes.sectionAdded((RenderSectionManager) (Object) this, section);
    }

    @Inject(method = "createSortTask", at = @At("RETURN"), remap = false)
    private void core$freezeSortGeometry(RenderSection section, int frame,
        CallbackInfoReturnable<ChunkBuilderSortTask> cir) {
        SliceMeshes.sortTaskCreated((RenderSectionManager) (Object) this, section, cir.getReturnValue());
    }

    @Inject(method = "processChunkBuildResults", at = @At("HEAD"), remap = false)
    private void core$discardObsoleteSliceResults(ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results,
        CallbackInfo ci) {
        SliceMeshes.filter((RenderSectionManager) (Object) this, results);
    }

    @WrapOperation(
        method = "processChunkBuildResults",
        at = @At(
            value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager;uploadMeshes(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Ljava/util/Collection;Ljava/lang/Runnable;)V"),
        remap = false)
    private void core$acknowledgeSliceUpload(RenderRegionManager regions, CommandList commands,
        Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable completion,
        Operation<Void> original) {
        original.call(regions, commands, results, completion);
        SliceMeshes.uploaded((RenderSectionManager) (Object) this, results);
    }

    @ModifyExpressionValue(
        method = "submitRebuildTasks",
        at = @At(
            value = "NEW",
            target = "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;Lorg/embeddedt/embeddium/impl/render/chunk/data/BuiltRenderSectionData;Lit/unimi/dsi/fastutil/objects/Reference2ReferenceMap;I)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;"),
        remap = false)
    private ChunkBuildOutput core$stampEmptySection(ChunkBuildOutput output) {
        return SliceMeshes.tagEmpty((RenderSectionManager) (Object) this, output);
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void core$detachSliceManager(CallbackInfo ci) {
        SliceMeshes.managerDestroyed((RenderSectionManager) (Object) this);
    }
}
