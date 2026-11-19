// SPDX-License-Identifier: AGPL-3.0-only

package com.miaokatze.miaokatzecore.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.miaokatze.miaokatzecore.client.preview.PreviewCacheHooks;

/** Couples the optional adapter to the native BlockRenderer 1.4.25 drawing and world contracts. */
public final class BlockRendererPreviewMixinPlugin implements IMixinConfigPlugin {

    private static final String PREVIEW_WORLD_MIXIN = "com.miaokatze.miaokatzecore.mixin.blockrenderer.MixinTrackedPreviewWorld";
    private static final String PREVIEW_SCENE_MIXIN = "com.miaokatze.miaokatzecore.mixin.blockrenderer.MixinPreviewSceneRenderer";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return BlockRendererPreviewShape.SUPPORTED;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Selection checks the original classes. Conflicting transformers can still remove an injection point.
        if (PREVIEW_WORLD_MIXIN.equals(mixinClassName)) {
            PreviewCacheHooks.setWorldReady(
                callsPreviewHook(targetClass, "core$worldCleared", "clear")
                    && callsPreviewHook(targetClass, "core$blockWrite", "setBlock", "func_147465_d")
                    && callsPreviewHook(targetClass, "core$metadataWrite", "setBlockMetadataWithNotify", "func_72921_c")
                    && callsPreviewHook(targetClass, "core$tileWrite", "setTileEntity", "func_147455_a")
                    && callsPreviewHook(targetClass, "core$tileRemoved", "removeTileEntity", "func_147475_p")
                    && callsPreviewHook(targetClass, "core$layerChanged", "setVisibleYLevel"));
        } else if (PREVIEW_SCENE_MIXIN.equals(mixinClassName)) {
            PreviewCacheHooks.setSceneReady(
                callsPreviewHook(targetClass, "core$rememberWorld", "<init>")
                    && callsPreviewHook(targetClass, "core$selectionChanged", "resetRenderedBlocks")
                    && callsPreviewHook(targetClass, "core$selectionChanged", "setRenderAllFaces")
                    && callsPreviewHook(targetClass, "core$appendCachedGeometry", "renderBlocks"));
        }
    }

    private static boolean callsPreviewHook(ClassNode target, String handler, String... methods) {
        for (MethodNode method : target.methods) {
            if (!Arrays.asList(methods)
                .contains(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(target.name) && call.name.endsWith(handler)) return true;
            }
        }
        return false;
    }

    /** The optional optimization is coupled to the inspected 1.4.25 scene/batch contract. */
    private static final class BlockRendererPreviewShape {

        private static final boolean SUPPORTED = hasPreviewShape();

        private static boolean hasPreviewShape() {
            ClassLoader loader = BlockRendererPreviewMixinPlugin.class.getClassLoader();
            try (
                InputStream sceneBytes = loader
                    .getResourceAsStream("blockrenderer6343/client/renderer/WorldSceneRenderer.class");
                InputStream worldBytes = loader
                    .getResourceAsStream("blockrenderer6343/client/world/TrackedDummyWorld.class")) {
                if (sceneBytes == null || worldBytes == null) return false;
                ClassNode scene = new ClassNode(), world = new ClassNode();
                new ClassReader(sceneBytes).accept(scene, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                new ClassReader(worldBytes).accept(world, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return method(
                    scene,
                    "renderBlocks",
                    "(Lnet/minecraft/client/renderer/Tessellator;Lit/unimi/dsi/fastutil/longs/LongCollection;Z)V")
                    && method(scene, "<init>", "(Lblockrenderer6343/client/world/TrackedDummyWorld;)V")
                    && field(scene, "bufferBuilder", "Lnet/minecraft/client/renderer/RenderBlocks;")
                    && field(scene, "renderAllFaces", "Z")
                    && field(scene, "renderedBlocks", "Lit/unimi/dsi/fastutil/longs/LongSet;")
                    && field(scene, "onRender", "Ljava/util/function/Consumer;")
                    && method(scene, "resetRenderedBlocks", "()V")
                    && method(scene, "setRenderAllFaces", "(Z)V")
                    && calls(
                        scene,
                        "renderBlocks",
                        "net/minecraft/client/renderer/Tessellator",
                        "func_78381_a",
                        "()I",
                        "draw") == 2
                    && calls(
                        scene,
                        "drawWorld",
                        "it/unimi/dsi/fastutil/longs/Long2ObjectMaps",
                        "fastIterable",
                        "(Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;)Lit/unimi/dsi/fastutil/objects/ObjectIterable;") == 1
                    && method(world, "clear", "()V")
                    && (method(world, "setBlock", "(IIILnet/minecraft/block/Block;II)Z")
                        || method(world, "func_147465_d", "(IIILnet/minecraft/block/Block;II)Z"))
                    && (method(world, "setBlockMetadataWithNotify", "(IIIII)Z")
                        || method(world, "func_72921_c", "(IIIII)Z"))
                    && (method(world, "setTileEntity", "(IIILnet/minecraft/tileentity/TileEntity;)V")
                        || method(world, "func_147455_a", "(IIILnet/minecraft/tileentity/TileEntity;)V"))
                    && (method(world, "removeTileEntity", "(III)V") || method(world, "func_147475_p", "(III)V"))
                    && method(world, "setVisibleYLevel", "(I)V");
            } catch (IOException | IllegalArgumentException unreadable) {
                return false;
            }
        }

        private static boolean method(ClassNode node, String name, String descriptor) {
            for (MethodNode method : node.methods)
                if (method.name.equals(name) && method.desc.equals(descriptor)) return true;
            return false;
        }

        private static boolean field(ClassNode node, String name, String descriptor) {
            for (org.spongepowered.asm.lib.tree.FieldNode field : node.fields)
                if (field.name.equals(name) && field.desc.equals(descriptor)) return true;
            return false;
        }

        private static int calls(ClassNode node, String methodName, String owner, String name, String descriptor,
            String... aliases) {
            int matches = 0;
            for (MethodNode method : node.methods) {
                if (!method.name.equals(methodName)) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invocation = (MethodInsnNode) instruction;
                    if (invocation.owner.equals(owner) && (invocation.name.equals(name) || Arrays.asList(aliases)
                        .contains(invocation.name)) && invocation.desc.equals(descriptor)) matches++;
                }
            }
            return matches;
        }
    }
}
