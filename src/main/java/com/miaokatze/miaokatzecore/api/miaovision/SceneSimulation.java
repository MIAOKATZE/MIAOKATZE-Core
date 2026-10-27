package com.miaokatze.miaokatzecore.api.miaovision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

/**
 * Explicitly enabled, bounded local display simulation. No global listener, clock, entities, world writes or
 * networking.
 * Three 1/60-second substeps per caller-owned 20 Hz tick, four conservative AABB contact sweeps per substep.
 * Rotations are preserved/integrated, while contacts use their enclosing boxes (not an exact OBB rigid-body solver).
 * Methods are owner-thread confined. Inventory and presentation clocks must never depend on step() returning true.
 */
public strictfp final class SceneSimulation {

    private static final double DT = 1.0 / 60.0;
    private final String layoutId;
    private final SceneBounds bounds;
    private final TreeMap<String, Moving> bodies = new TreeMap<>();
    private long revision, tick;
    private boolean enabled, settled = true;

    public SceneSimulation(String layoutId, SceneBounds bounds) {
        new SceneSnapshot(layoutId, 0, 0, new ArrayList<SceneBody>());
        if (bounds == null) throw new IllegalArgumentException("bounds");
        this.layoutId = layoutId;
        this.bounds = bounds;
    }

    /** Enabling is an explicit owner decision. Adding/restoring/rendering a body never enables physics. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSettled() {
        return settled;
    }

    public int size() {
        return bodies.size();
    }

    public long revision() {
        return revision;
    }

    public long tick() {
        return tick;
    }

    public SceneBounds bounds() {
        return bounds;
    }

    public void put(SceneBody body) {
        if (body == null) throw new IllegalArgumentException("body");
        bounds.validate(body);
        if (!bodies.containsKey(body.id) && bodies.size() >= SceneSnapshot.MAX_BODIES)
            throw new IllegalArgumentException("scene capacity");
        checkCounter(revision);
        bodies.put(body.id, new Moving(body));
        wakeAll();
        revision++;
    }

    public boolean remove(String id) {
        if (!bodies.containsKey(id)) return false;
        checkCounter(revision);
        bodies.remove(id);
        wakeAll(); // A removed support can affect the whole bounded contact island.
        revision++;
        return true;
    }

    /** Explicit wake request; still does not enable physics. */
    public void wake() {
        if (bodies.isEmpty()) return;
        checkCounter(revision);
        wakeAll();
        revision++;
    }

    private void wakeAll() {
        for (Moving b : bodies.values()) {
            b.sleeping = false;
            b.quiet = 0;
        }
        settled = bodies.isEmpty();
    }

    /** Zero body iteration/allocation when disabled or already settled. */
    public boolean step() {
        if (!enabled || settled) return false;
        checkCounter(revision);
        checkCounter(tick);
        Moving[] values = bodies.values()
            .toArray(new Moving[bodies.size()]);
        for (int sub = 0; sub < 3; sub++) {
            for (Moving b : values) if (!b.sleeping) {
                b.integrate();
                wall(b);
            }
            Arrays.sort(values, (a, b) -> {
                int order = Double.compare(a.y, b.y);
                return order != 0 ? order : a.id.compareTo(b.id);
            });
            for (int sweep = 0; sweep < 4; sweep++) {
                for (int i = 0; i < values.length; i++)
                    for (int j = i + 1; j < values.length; j++) collide(values[i], values[j]);
                for (Moving b : values) if (!b.sleeping) wall(b);
            }
        }
        settled = true;
        for (Moving b : values) {
            if (!b.sleeping) {
                if (b.supported && b.vx * b.vx + b.vy * b.vy + b.vz * b.vz < 0.003
                    && b.wx * b.wx + b.wy * b.wy + b.wz * b.wz < 0.003) b.quiet = Math.min(20, b.quiet + 1);
                else b.quiet = 0;
                if (b.quiet >= 20) {
                    b.sleeping = true;
                    b.vx = b.vy = b.vz = b.wx = b.wy = b.wz = 0;
                }
            }
            if (!b.sleeping) settled = false;
        }
        revision++;
        tick++;
        return true;
    }

    public SceneSnapshot snapshot() {
        ArrayList<SceneBody> result = new ArrayList<>(bodies.size());
        for (Moving b : bodies.values()) result.add(b.freeze());
        return new SceneSnapshot(layoutId, revision, tick, result);
    }

    /**
     * Atomic load, exact stored poses, no replay. Always leaves physics disabled, including when previously enabled.
     */
    public void restore(SceneSnapshot snapshot) {
        if (snapshot == null || !layoutId.equals(snapshot.layoutId))
            throw new IllegalArgumentException("scene layout mismatch");
        TreeMap<String, Moving> next = new TreeMap<>();
        boolean sleeping = true;
        for (SceneBody b : snapshot.bodies) {
            bounds.validate(b);
            next.put(b.id, new Moving(b));
            sleeping &= b.sleeping;
        }
        bodies.clear();
        bodies.putAll(next);
        revision = snapshot.revision;
        tick = snapshot.tick;
        settled = sleeping;
        enabled = false;
    }

    private static void checkCounter(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("scene counter exhausted");
    }

    private void wall(Moving b) {
        double[] e = b.extents();
        if (e[0] * 2 > bounds.maxX - bounds.minX || e[1] * 2 > bounds.maxY - bounds.minY
            || e[2] * 2 > bounds.maxZ - bounds.minZ) {
            // Rotation cannot fit: roll back this substep's orientation, never shrink or teleport a body.
            b.qx = b.oldQx;
            b.qy = b.oldQy;
            b.qz = b.oldQz;
            b.qw = b.oldQw;
            b.wx = b.wy = b.wz = 0;
            b.updateExtents();
            e = b.extents();
        }
        if (b.x < bounds.minX + e[0]) {
            b.x = bounds.minX + e[0];
            b.vx = Math.abs(b.vx) * .15;
        }
        if (b.x > bounds.maxX - e[0]) {
            b.x = bounds.maxX - e[0];
            b.vx = -Math.abs(b.vx) * .15;
        }
        if (b.z < bounds.minZ + e[2]) {
            b.z = bounds.minZ + e[2];
            b.vz = Math.abs(b.vz) * .15;
        }
        if (b.z > bounds.maxZ - e[2]) {
            b.z = bounds.maxZ - e[2];
            b.vz = -Math.abs(b.vz) * .15;
        }
        if (b.y < bounds.minY + e[1]) {
            b.y = bounds.minY + e[1];
            b.vy = Math.abs(b.vy) > .7 ? Math.abs(b.vy) * .12 : 0;
            b.supported = true;
            b.friction();
        }
        if (b.y > bounds.maxY - e[1]) {
            b.y = bounds.maxY - e[1];
            b.vy = -Math.abs(b.vy) * .15;
        }
    }

    private static void collide(Moving a, Moving b) {
        if (a.sleeping && b.sleeping) return;

        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double ox = a.ex + b.ex - Math.abs(dx), oy = a.ey + b.ey - Math.abs(dy), oz = a.ez + b.ez - Math.abs(dz);
        if (ox <= 0 || oy <= 0 || oz <= 0) return;
        int axis = oy <= ox && oy <= oz ? 1 : ox <= oz ? 0 : 2;
        if (axis == 1) {
            Moving low = dy >= 0 ? a : b, high = dy >= 0 ? b : a;
            // A display pile uses a deterministic unilateral support constraint: lower bodies are not driven
            // into the floor by stack weight. This avoids unbounded solver iterations for tall 100-body piles.
            if (!high.sleeping) {
                high.y = low.y + low.ey + high.ey;
                if (high.vy < low.vy) high.vy = low.vy;
                if (Math.abs(high.vy) < .2) high.vy = 0;
                high.supported = true;
                high.friction();
            } else if (!low.sleeping) {
                low.y = high.y - high.ey - low.ey;
                if (low.vy > 0) low.vy = 0;
            }
            return;
        }
        double overlap = axis == 0 ? ox : oz;
        double sign = (axis == 0 ? dx : axis == 1 ? dy : dz) >= 0 ? 1 : -1;
        // Existing sleeping supports remain static; impacts wake them only for meaningful motion.
        double rel = axis == 0 ? b.vx - a.vx : axis == 1 ? b.vy - a.vy : b.vz - a.vz;
        if (Math.abs(rel) > .8) {
            a.sleeping = false;
            b.sleeping = false;
            a.quiet = b.quiet = 0;
        }
        double wa = a.sleeping ? 0 : 1, wb = b.sleeping ? 0 : 1, total = wa + wb;
        if (total == 0) return;
        double da = overlap * wa / total, db = overlap * wb / total;
        if (axis == 0) {
            a.x -= sign * da;
            b.x += sign * db;
        } else if (axis == 1) {
            a.y -= sign * da;
            b.y += sign * db;
            if (sign > 0) b.supported = true;
            else a.supported = true;
        } else {
            a.z -= sign * da;
            b.z += sign * db;
        }
        if (rel * sign < 0) {
            double impulse = -rel / total;
            if (axis == 0) {
                a.vx -= impulse * wa;
                b.vx += impulse * wb;
            } else if (axis == 1) {
                a.vy -= impulse * wa;
                b.vy += impulse * wb;
            } else {
                a.vz -= impulse * wa;
                b.vz += impulse * wb;
            }
        }
        a.friction();
        b.friction();
    }

    private static final class Moving {

        final String id;
        final double hx, hy, hz;
        double ex, ey, ez;
        double x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz, oldQx, oldQy, oldQz, oldQw;
        boolean sleeping, supported;
        int quiet;

        Moving(SceneBody b) {
            id = b.id;
            hx = b.halfX;
            hy = b.halfY;
            hz = b.halfZ;
            x = b.x;
            y = b.y;
            z = b.z;
            qx = oldQx = b.qx;
            qy = oldQy = b.qy;
            qz = oldQz = b.qz;
            qw = oldQw = b.qw;
            vx = b.vx;
            vy = b.vy;
            vz = b.vz;
            wx = b.wx;
            wy = b.wy;
            wz = b.wz;
            sleeping = b.sleeping;
            quiet = b.quietTicks;
            updateExtents();
        }

        void integrate() {
            supported = false;
            oldQx = qx;
            oldQy = qy;
            oldQz = qz;
            oldQw = qw;
            double nx = qx + DT * .5 * (wx * qw + wy * qz - wz * qy),
                ny = qy + DT * .5 * (-wx * qz + wy * qw + wz * qx);
            double nz = qz + DT * .5 * (wx * qy - wy * qx + wz * qw), nw = qw - DT * .5 * (wx * qx + wy * qy + wz * qz);
            double norm = Math.sqrt(nx * nx + ny * ny + nz * nz + nw * nw);
            qx = nx / norm;
            qy = ny / norm;
            qz = nz / norm;
            qw = nw / norm;
            updateExtents();
            vy = Math.max(-32, vy - 9.81 * DT);
            // Bounded travel prevents small visual proxies tunnelling through one another without extra substeps.
            vx = limit(vx, ex * .5 / DT);
            vy = limit(vy, ey * .5 / DT);
            vz = limit(vz, ez * .5 / DT);
            x += vx * DT;
            y += vy * DT;
            z += vz * DT;
            vx *= .998;
            vz *= .998;
            wx *= .995;
            wy *= .995;
            wz *= .995;
        }

        static double limit(double value, double max) {
            return Math.max(-max, Math.min(max, value));
        }

        void friction() {
            vx *= .85;
            vz *= .85;
            wx *= .75;
            wy *= .75;
            wz *= .75;
        }

        double[] extents() {
            return new double[] { ex, ey, ez };
        }

        void updateExtents() {
            ex = Math.abs(1 - 2 * (qy * qy + qz * qz)) * hx + Math.abs(2 * (qx * qy - qz * qw)) * hy
                + Math.abs(2 * (qx * qz + qy * qw)) * hz;
            ey = Math.abs(2 * (qx * qy + qz * qw)) * hx + Math.abs(1 - 2 * (qx * qx + qz * qz)) * hy
                + Math.abs(2 * (qy * qz - qx * qw)) * hz;
            ez = Math.abs(2 * (qx * qz - qy * qw)) * hx + Math.abs(2 * (qy * qz + qx * qw)) * hy
                + Math.abs(1 - 2 * (qx * qx + qy * qy)) * hz;
        }

        SceneBody freeze() {
            return new SceneBody(id, hx, hy, hz, x, y, z, qx, qy, qz, qw, vx, vy, vz, wx, wy, wz, sleeping, quiet);
        }
    }
}
