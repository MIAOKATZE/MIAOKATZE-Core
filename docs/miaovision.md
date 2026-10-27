# MiaoVision 1 — additive local scene API

Package: `com.miaokatze.miaokatzecore.api.miaovision`. No previous Core API is replaced.

- `SceneBounds(minX,minY,minZ,maxX,maxY,maxZ)` defines a finite local room (each dimension ≤256).
- `SceneBody(id,halfX,halfY,halfZ,x,y,z)` creates an immutable display proxy; public scalar fields include normalized `qx,qy,qz,qw`, `vx,vy,vz`, `wx,wy,wz`, `sleeping`, and `quietTicks` (sleep hysteresis). The full constructor accepts all these in the listed order, followed by sleeping; an overload adds `quietTicks` as the final argument. Persist the latter for exact sleep-timing resume. `withPosition`, `withVelocity` (six components), `withRotation`, `withSleeping` return new values.
- `SceneSnapshot(layoutId,revision,tick,List<SceneBody>)` is a transport-neutral immutable, ID-sorted DTO. Alternate constructor starts with schema version. Schema 1, maximum 100 bodies, IDs/layout names ≤128 chars, no null/duplicate IDs, finite bounded numbers. No inventory, NBT, world or entities in the API.
- `SceneSimulation(layoutId,bounds)`: `put(body)`, `remove(id)`, `wake()`, `setEnabled(boolean)`, `isEnabled()`, `isSettled()`, `step()`, `snapshot()`, `restore(snapshot)`, `revision()`, `tick()`, `bounds()`, `size()`.

## Ownership and activation

Physics is **disabled by default**. Construction, loading, adding, removal and rendering never enable it. The machine owner explicitly enables after an event requiring motion, calls `step()` at most once per server tick, and may disable when `isSettled()` becomes true. Disabled/settled `step()` returns false without iterating bodies, allocating or advancing counters. Inventory, ME and delivery/presentation clocks are separate; do not stop them when physics is disabled.

`put` replaces physical state and wakes the bounded contact set. Quantity-only changes belong to the consumer and must not call `put`. `remove` wakes remaining bodies to release old supports without rearrangement. No global tick listener is installed.

`restore` validates layout and **all** poses before replacing anything, restores directly without emission, and always disables physics. The owner may explicitly resume afterwards. Body centers plus rotated extents must fit the room. Snapshot DTO validation is not authorization: GTIT owns machine identity/epoch, persistence, packet size, stale-revision checks and network authorization. `isNewerThan` is a convenience for a single already-validated epoch; it is not a cross-machine check.

## Bounded simulation

A tick has exactly three 1/60-second substeps, each at most four deterministic height/ID-ordered pair sweeps, with at most 100 bodies. Conservative rotation-aware enclosing-box contacts preserve quaternions but are not an exact arbitrary-shape/OBB rigid-body engine. A unilateral lower-to-upper support constraint avoids crushing tall stacks into the floor with a fixed iteration budget. Per-axis travel is limited to half the current contact extent per substep to prevent thin-proxy tunnelling; extreme requested launch speeds are intentionally clamped for display stability. Gravity, bounded velocities, restitution, friction and sleep provide credible display pile motion. There is no external solver or JavaScript runtime. All bodies are display proxies, never second inventories.

## Rendering contract (client package)

`MiaoVisionRenderer` renders supplied state only, never steps physics. Consumers own native MUI2 widget layout, input, snapshot interpolation, room art, animation channels and item identity resolution. No MUI synced widgets are introduced by Core; existing client/server slot trees remain unaffected. Renderers hold no real World/TileEntity or entity reference and do not register dimensions. Opaque-only rendering allocates no compositor GL handles. The first nonzero partial-opacity body lazily creates one instance-owned RGBA8/depth24 target, shader program and VAO; no per-body targets or global caches are retained. Within the supported single-color GUI target, native scopes restore caller state even on rendering failures. Multiple active draw buffers (MRT), or output zero disabled, are rejected before changing GL/Tessellator state. Heterogeneous indexed color masks/blend state on inactive draw buffers is explicitly outside this API contract; this is not a general MRT renderer. A consumer-provided model resolver may override rendering for audited custom models; state restoration cannot undo arbitrary third-party gameplay side effects.

## Consumer integration

GTIT owns dropId→real-slot mapping, quantities/NBT, delivery routes/phases/seeds, room/door art, ME acceptance, server storage and packets. Dirty active snapshots/deltas may be sent at 5 Hz; neither Core nor the renderer sends packets. Restoring a compatible pose takes priority over initial layout. Invalid scene data cannot remove real inventory. Client visibility and presentation timing are authoritative server values, not render acknowledgements.

Baseline sources: Core c60f32e9 (0.1.22), GTMB 7be4aeee (0.1.50), GTUDK 73d43a6e (0.5.17). GTMB detached read-only World and Core PreviewRenderScope inform the boundary; GTUDK timeline is not a server physics engine and its process-lifetime display-list cache is not copied.

### Exact client signatures

```java
new SceneViewport(int framebufferX, int framebufferY, int width, int height);
new SceneCamera(double yawDegrees, double pitchDegrees, double zoom);
new MiaoVisionRenderer().render(SceneSnapshot snapshot, SceneBounds bounds,
    SceneViewport viewport, SceneCamera camera, SceneModelResolver resolver, ScenePresentation presentation);
// resolver: ItemStack resolve(String id); optional render(SceneBody, ItemStack, float opacity) -> boolean;
// beforeBodies(SceneSnapshot), afterBodies(SceneSnapshot): scene-coordinate room art.
// presentation: boolean visible(String id); float opacity(String id).
```

Camera fitting uses the room's bounding sphere and viewport aspect. The consumer supplies already-interpolated poses; renderer access does not advance any time. `SceneViewport` uses bottom-left framebuffer pixels, NOT GUI-scaled top-left coordinates. Clamp/intersect to the actual framebuffer before construction. The renderer intersects enclosing scissor clips and clears depth only inside that region. It does not clear color. Call `close()` on widget disposal on the owning GL thread; close is idempotent and a new renderer is needed afterwards. Resize replaces the old target, with each edge bounded to 1024 pixels while preserving aspect. The target/program/VAO are independent of Minecraft atlas resources; current stacks and atlases are resolved on each draw, so atlas reload does not need a global invalidation listener.

## Verification boundary

`runMiaoVisionContracts` exercises the production Java-8 API: default-off/no implicit start, explicit repeated activation, disable/resume, atomic invalid restoration, exact mid-quiet replay (including hysteresis), stale revision helper, support removal, 100-body sleep/non-overlap/finite bounds, and small fast proxies. Native classes compile against the real Forge/MUI2 baseline. These are NOT evidence of actual Minecraft/OpenGL execution. A live client must still verify viewport/scissor at GUI scales, 3D blocks and multipass/custom items, alpha fade, exception restoration with `PreviewRenderScope.StateWitness`, room callbacks, resource reload and close/reopen. Per-model native scopes trade bounded extra state queries for isolation; performance in the target renderer must be measured rather than inferred from JVM simulation tests.

### Native inventory-helper adaptation

Forge 1.7.10 `ForgeHooksClient.renderInventoryItem` binds the stack sprite atlas and distinguishes `INVENTORY_BLOCK`. The scene follows that decision: helper models use block coordinates `[0,1]^3` translated to `[-.5,.5]^3`, while non-helper models use `[0,16]` GUI pixels scaled by `1/16` with Y inverted. The GUI helper's isometric rotations and 10-pixel scale are intentionally omitted because the scene already provides its own camera and body quaternion. Custom models receive pass-zero item tint and `RenderBlocks.useInventoryTint=true`, with the existing outer GL/pass/block-bounds guards unchanged.

The RC2 ArchitectureCraft 1.12.18 dispatcher requests the block helper and uses `Trans3.blockCenter`; the headless model contract covers that centered-mesh→block-coordinate→scene-coordinate transform, not actual GL drawing. ArchitectureCraft also changes its own blend function. Consequently scene fades no longer depend on constant-alpha or current-color state. Opaque bodies draw first; partially opaque bodies draw in stable camera-depth far-to-near order. Each faded native model is rendered at full presentation opacity into the bounded RGBA/depth target, then composited with sampled fragment depth against existing opaque room/body depth. The composite never writes destination depth. `SceneModelResolver.render` therefore receives opacity 1 during native capture; material transparency remains the native renderer's responsibility. Put opaque room art in `beforeBodies`; `afterBodies` is for overlays/effects whose ordering is intentional.

The compositor requires OpenGL 3.0 framebuffer/VAO support and GLSL 1.30 (GLSL 1.50 on OpenGL 3.2+). Its fullscreen triangle uses `gl_VertexID`, not compatibility-only immediate-mode vertices, and supports both core and compatibility contexts. Legacy enum availability uses the actual 3.2+ profile mask, 3.1 `ARB_compatibility`, or 3.0 forward-compatible flag. Owned FBO draw selection uses `glDrawBuffers`, avoiding Angelica's global `glDrawBuffer` cache while leaving the caller FBO selection untouched. An unsupported capability or setup failure retains native geometry as an opaque fallback; `MiaoVisionRenderer.opacityFailure()` exposes the reason. The native callback is never retried after it starts. Native models that deliberately suppress all depth writes or have complex internal transparency still need model-specific acceptance; a single captured depth layer is not order-independent transparency for intersecting/translucent meshes.

### Isolated native compositor evidence

`SceneOpacityNativeContracts` is an explicitly launched real-GL probe, not part of headless `check`. Mesa llvmpipe 4.5 compatibility/core and forced 3.0/3.1 forward-compatible profiles each passed 35 checks: actual pixel/depth readbacks after native material-state resets, opaque occlusion, far/near composition, no fade depth writes, scissor clipping, success/exception restoration, bounded resize/shrink and idempotent close. An actual two-color FBO with unequal indexed masks/enables/blend functions is rejected without allocation, callback invocation or indexed-state mutation (independent blend functions exercised where GL4 is available). The executable fixture uses the production compositor directly and never starts Minecraft. Receipts are under `build-support/native-runtime-readiness/miaovision-probe/`. This proves the compositor's isolated GL path, not actual GTNH/Angelica custom-item pixels, GUI-scale integration, resource reload or full-client rendering. Those remain live-client acceptance items. Pure model/order contracts additionally validate opaque-first ordering, stable ties, camera reversal and invalid-opacity rejection before touching GL.
