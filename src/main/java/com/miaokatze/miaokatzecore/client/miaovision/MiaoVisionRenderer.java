package com.miaokatze.miaokatzecore.client.miaovision;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.miaokatze.miaokatzecore.api.miaovision.SceneBody;
import com.miaokatze.miaokatzecore.api.miaovision.SceneBounds;
import com.miaokatze.miaokatzecore.api.miaovision.ScenePresentation;
import com.miaokatze.miaokatzecore.api.miaovision.SceneSnapshot;
import com.miaokatze.miaokatzecore.client.preview.PreviewRenderScope;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Instance-owned native scene renderer. No simulation, global listeners, entities or world leases. Fade targets are
 * allocated lazily, bounded and instance-owned. Caller provides already-interpolated
 * immutable poses and exact framebuffer bounds. Close is idempotent and requires the owning GL thread after rendering.
 * Supports a single active color output zero with uniform indexed color/blend state, not heterogeneous MRT state.
 */
@SideOnly(Side.CLIENT)
public final class MiaoVisionRenderer implements AutoCloseable {

    private static final ResourceLocation GLINT = new ResourceLocation(
        "minecraft",
        "textures/misc/enchanted_item_glint.png");
    private boolean closed;
    private final SceneOpacityCompositor compositor = new SceneOpacityCompositor();

    /** Null unless fading is unavailable; unsupported contexts retain native models, rendered opaque. */
    public String opacityFailure() {
        return compositor.failure();
    }

    public void render(SceneSnapshot snapshot, SceneBounds bounds, SceneViewport viewport, SceneCamera camera,
        SceneModelResolver resolver, ScenePresentation presentation) {
        render(snapshot, bounds, bounds, viewport, camera, resolver, presentation);
    }

    /**
     * Separates camera framing from authoritative simulation bounds. Existing callers retain identical framing.
     * The caller must include every visible body and room surface in framingBounds, and use it for ray picking too.
     */
    public void render(SceneSnapshot snapshot, SceneBounds bounds, SceneBounds framingBounds, SceneViewport viewport,
        SceneCamera camera, SceneModelResolver resolver, ScenePresentation presentation) {
        if (closed) throw new IllegalStateException("renderer closed");
        if (snapshot == null || bounds == null
            || framingBounds == null
            || viewport == null
            || camera == null
            || resolver == null) throw new IllegalArgumentException("render arguments");
        if (presentation == null) presentation = ScenePresentation.VISIBLE;
        // All validation precedes changes to the caller's OpenGL/Tessellator state.
        for (SceneBody body : snapshot.bodies) bounds.validate(body);
        java.util.List<SceneRenderOrder.Entry> ordered = SceneRenderOrder.prepare(snapshot, presentation, camera);
        if (!SceneOpacityCompositor.singleColorTarget())
            throw new IllegalStateException("MiaoVision requires a single-color GUI target; MRT is unsupported");
        PreviewRenderScope.ClientTessState tess;
        try {
            tess = new PreviewRenderScope.ClientTessState(Tessellator.instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot isolate tessellator", e);
        }
        PreviewRenderScope.RenderState state = new PreviewRenderScope.RenderState();
        try {
            state.push();
            state.useProgram(0);
            state.cleanTextureUnits();
            GL11.glViewport(viewport.x, viewport.y, viewport.width, viewport.height);
            // Respect an enclosing MUI scissor rather than clearing/drawing outside its clip.
            IntBuffer previous = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previous);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int x = viewport.x, y = viewport.y, r = x + viewport.width, t = y + viewport.height;
            if (scissor) {
                x = Math.max(x, previous.get(0));
                y = Math.max(y, previous.get(1));
                r = Math.min(r, previous.get(0) + previous.get(2));
                t = Math.min(t, previous.get(1) + previous.get(3));
            }
            if (r <= x || t <= y) return;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, r - x, t - y);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthRange(0, 1);
            GL11.glClearDepth(1);
            GL11.glDepthMask(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, .01f);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            double radius = Math.sqrt(
                square(framingBounds.maxX - framingBounds.minX) + square(framingBounds.maxY - framingBounds.minY)
                    + square(framingBounds.maxZ - framingBounds.minZ))
                * .55;
            double aspect = (double) viewport.width / viewport.height;
            double halfH = radius / camera.zoom / Math.min(1, aspect), halfW = halfH * aspect;
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(-halfW, halfW, -halfH, halfH, -1024, 1024);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glRotated(camera.pitchDegrees, 1, 0, 0);
            GL11.glRotated(camera.yawDegrees, 0, 1, 0);
            GL11.glTranslated(
                -(framingBounds.minX + framingBounds.maxX) / 2,
                -(framingBounds.minY + framingBounds.maxY) / 2,
                -(framingBounds.minZ + framingBounds.maxZ) / 2);
            renderRoom(resolver, snapshot, true);
            for (SceneRenderOrder.Entry entry : ordered) {
                SceneBody body = entry.body;
                ItemStack source = resolver.resolve(body.id);
                if (source == null || source.getItem() == null) continue;
                ItemStack stack = source.copy();
                stack.stackSize = 1;
                Runnable nativeModel = () -> renderBody(body, stack, resolver);
                if (entry.opacity == 1 || !compositor
                    .draw(viewport.x, viewport.y, viewport.width, viewport.height, entry.opacity, nativeModel)) {
                    // Capability fallback never changes to GUI icons or invokes an already-drawn renderer twice.
                    nativeModel.run();
                }
            }
            renderRoom(resolver, snapshot, false);
        } finally {
            try {
                tess.restore();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot restore tessellator", e);
            } finally {
                state.restore();
            }
        }
    }

    private static void renderBody(SceneBody body, ItemStack stack, SceneModelResolver resolver) {
        int previousPass = MinecraftForgeClient.getRenderPass();
        PreviewRenderScope.RenderState modelState = new PreviewRenderScope.RenderState();
        try {
            modelState.push();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glTranslated(body.x, body.y, body.z);
            GL11.glMultMatrix(rotation(body));
            GL11.glScaled(body.halfX * 2, body.halfY * 2, body.halfZ * 2);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glDepthMask(true);
            // Native renderers own their material blend/color. Scene opacity is applied only after capture.
            if (!resolver.render(body, stack, 1)) drawNative(stack, 1, resolver.extrudeInventorySprites());
        } finally {
            ForgeHooksClient.setRenderPass(previousPass);
            modelState.restore();
        }
    }

    private static void renderRoom(SceneModelResolver resolver, SceneSnapshot snapshot, boolean before) {
        int previousPass = MinecraftForgeClient.getRenderPass();
        PreviewRenderScope.RenderState roomState = new PreviewRenderScope.RenderState();
        try {
            roomState.push();
            if (before) resolver.beforeBodies(snapshot);
            else resolver.afterBodies(snapshot);
        } finally {
            ForgeHooksClient.setRenderPass(previousPass);
            roomState.restore();
        }
    }

    private static double square(double n) {
        return n * n;
    }

    private static FloatBuffer rotation(SceneBody b) {
        double x = b.qx, y = b.qy, z = b.qz, w = b.qw;
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        matrix.put(
            new float[] { (float) (1 - 2 * (y * y + z * z)), (float) (2 * (x * y + z * w)),
                (float) (2 * (x * z - y * w)), 0, (float) (2 * (x * y - z * w)), (float) (1 - 2 * (x * x + z * z)),
                (float) (2 * (y * z + x * w)), 0, (float) (2 * (x * z + y * w)), (float) (2 * (y * z - x * w)),
                (float) (1 - 2 * (x * x + y * y)), 0, 0, 0, 0, 1 });
        matrix.flip();
        return matrix;
    }

    private static void drawNative(ItemStack stack, float opacity, boolean extrudeSprites) {
        Minecraft mc = Minecraft.getMinecraft();
        Block block = stack.getItem() instanceof ItemBlock ? Block.getBlockFromItem(stack.getItem()) : Blocks.air;
        double[] blockBounds = block == Blocks.air ? null
            : new double[] { block.getBlockBoundsMinX(), block.getBlockBoundsMinY(), block.getBlockBoundsMinZ(),
                block.getBlockBoundsMaxX(), block.getBlockBoundsMaxY(), block.getBlockBoundsMaxZ() };
        try {
            drawNativeModel(mc, stack, opacity, block, extrudeSprites);
        } finally {
            if (blockBounds != null) {
                block.setBlockBounds(
                    (float) blockBounds[0],
                    (float) blockBounds[1],
                    (float) blockBounds[2],
                    (float) blockBounds[3],
                    (float) blockBounds[4],
                    (float) blockBounds[5]);
                PreviewRenderScope.releaseNativeContext(block);
            }
        }
    }

    private static void drawNativeModel(Minecraft mc, ItemStack stack, float opacity, Block block,
        boolean extrudeSprites) {
        IItemRenderer custom = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
        if (custom != null && (!extrudeSprites || block != Blocks.air
            || custom.shouldUseRenderHelper(
                IItemRenderer.ItemRenderType.INVENTORY,
                stack,
                IItemRenderer.ItemRendererHelper.INVENTORY_BLOCK)
            || !hasNativeSprite(stack))) {
            // Match ForgeHooksClient's atlas and helper decision, without importing its GUI camera transform.
            // A block helper emits unit-block coordinates, not 16-pixel icon coordinates (e.g. ArchitectureCraft).
            mc.getTextureManager()
                .bindTexture(
                    InventoryModelTransform.blockAtlas(stack.getItemSpriteNumber()) ? TextureMap.locationBlocksTexture
                        : TextureMap.locationItemsTexture);
            InventoryModelTransform transform = InventoryModelTransform.forHelper(
                custom.shouldUseRenderHelper(
                    IItemRenderer.ItemRenderType.INVENTORY,
                    stack,
                    IItemRenderer.ItemRendererHelper.INVENTORY_BLOCK));
            GL11.glTranslated(transform.translateX, transform.translateY, transform.translateZ);
            GL11.glScaled(transform.scaleX, transform.scaleY, transform.scaleZ);
            int color = stack.getItem()
                .getColorFromItemStack(stack, 0);
            GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, opacity);
            GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderBlocks renderBlocks = new RenderBlocks();
            renderBlocks.useInventoryTint = true;
            custom.renderItem(IItemRenderer.ItemRenderType.INVENTORY, stack, renderBlocks);
            return;
        }
        if (block != Blocks.air && RenderBlocks.renderItemIn3d(block.getRenderType())) {
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
            new RenderBlocks().renderBlockAsItem(block, stack.getItemDamage(), 1);
            return;
        }
        mc.getTextureManager()
            .bindTexture(
                stack.getItem()
                    .getSpriteNumber() == 0 ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
        GL14.glBlendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Vanilla's native 1/16-pixel extrusion, normalized to the physical body's local thickness.
        // The final depth is exactly 2*body.halfZ; consumers choose a thin proxy, not a unit collision cube.
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL11.GL_NORMALIZE);
        final float nativeThickness = SceneSpriteTransform.NATIVE_DEPTH;
        GL11.glScaled(1, 1, SceneSpriteTransform.SCALE_Z);
        GL11.glTranslated(
            SceneSpriteTransform.OFFSET_XY,
            SceneSpriteTransform.OFFSET_XY,
            SceneSpriteTransform.OFFSET_Z);
        int passes = nativePasses(stack);
        boolean glint = false;
        for (int pass = 0; pass < passes; pass++) {
            ForgeHooksClient.setRenderPass(pass);
            IIcon icon = stack.getItem()
                .getIcon(stack, pass);
            if (icon == null) continue;
            glint |= stack.getItem()
                .hasEffect(stack, pass);
            int color = stack.getItem()
                .getColorFromItemStack(stack, pass);
            GL11.glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, opacity);
            ItemRenderer.renderItemIn2D(
                Tessellator.instance,
                icon.getMaxU(),
                icon.getMinV(),
                icon.getMinU(),
                icon.getMaxV(),
                Math.max(1, icon.getIconWidth()),
                Math.max(1, icon.getIconHeight()),
                nativeThickness);
        }
        if (glint) drawSpriteGlint(mc, nativeThickness);
    }

    private static int nativePasses(ItemStack stack) {
        return Math.min(
            16,
            Math.max(
                1,
                stack.getItem()
                    .requiresMultipleRenderPasses()
                        ? stack.getItem()
                            .getRenderPasses(stack.getItemDamage())
                        : 1));
    }

    private static boolean hasNativeSprite(ItemStack stack) {
        try {
            for (int pass = 0; pass < nativePasses(stack); pass++) if (stack.getItem()
                .getIcon(stack, pass) != null) return true;
        } catch (RuntimeException unsupportedIconAccess) {
            // This preflight is used only when a native custom renderer is available. Some custom-only
            // models have no usable icon API. Retain that renderer; its own render errors still propagate.
            return false;
        }
        return false;
    }

    private static void drawSpriteGlint(Minecraft mc, float thickness) {
        PreviewRenderScope.RenderState glint = new PreviewRenderScope.RenderState();
        try {
            glint.push();
            GL11.glDepthFunc(GL11.GL_EQUAL); // Only pixels already occupied by the actual native sprite.
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
            GL11.glColor4f(.38f, .22f, .62f, 1);
            mc.getTextureManager()
                .bindTexture(GLINT);
            for (int layer = 0; layer < 2; layer++) {
                GL11.glMatrixMode(GL11.GL_TEXTURE);
                GL11.glLoadIdentity();
                GL11.glScaled(.13, .13, .13);
                double phase = (System.currentTimeMillis() % (layer == 0 ? 3000L : 4873L))
                    / (double) (layer == 0 ? 3000L : 4873L);
                GL11.glTranslated((layer == 0 ? 1 : -1) * phase * 8, 0, 0);
                GL11.glRotated(layer == 0 ? -45 : 15, 0, 0, 1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                ItemRenderer.renderItemIn2D(Tessellator.instance, 1, 0, 0, 1, 16, 16, thickness);
            }
        } finally {
            glint.restore();
        }
    }

    /** Release this instance's bounded fade target on its owning GL thread; no global reload listener is registered. */
    @Override
    public void close() {
        if (!closed) {
            compositor.close();
            closed = true;
        }
    }
}
