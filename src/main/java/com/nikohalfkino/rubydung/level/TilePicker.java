package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.HitResult;
import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.phys.AABB;

import java.util.List;

/**
 * Ray picking inside one cell, against the tile's real shape (Tile.getPickBoxes): a slab only
 * answers in its own half of the cell, a staircase in its steps. A ray that passes through the
 * empty part of the cell gets null and should simply carry on to the next cell.
 */
public final class TilePicker {

    private TilePicker() {}

    public static final class Result {
        /** Ray parameter of the hit (distance, if the direction is a unit vector). */
        public final double t;
        /** World position of the hit. */
        public final double x, y, z;
        /** The Tile.FACE_* of the struck box that the ray entered through. */
        public final int face;
        /** The box that was struck, in world coordinates. */
        public final AABB box;

        Result(double t, double x, double y, double z, int face, AABB box) {
            this.t = t;
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            this.box = box;
        }
    }

    /**
     * @param ox,oy,oz ray origin (the eye)
     * @param dx,dy,dz ray direction (need not be normalised)
     * @param maxT     furthest ray parameter that still counts (the reach, for a unit direction)
     * @return the nearest hit on the tile at (x, y, z), or null if the ray misses its shape
     */
    public static Result pick(Level level, int x, int y, int z,
                              double ox, double oy, double oz,
                              double dx, double dy, double dz, double maxT) {
        Tile tile = Tile.byId(level.getTile(x, y, z));
        if (tile == null) {
            return null;
        }

        Result best = null;
        List<AABB> boxes = tile.getPickBoxes(level, x, y, z);
        for (AABB box : boxes) {
            Result r = intersect(box, ox, oy, oz, dx, dy, dz);
            if (r != null && r.t <= maxT && (best == null || r.t < best.t)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Sharpens a hit that came from plain cell stepping, using the camera ray (CameraRay): fills in
     * the exact hit point and the box struck, and corrects the face. If the ray only crossed the empty
     * part of the cell (above a bottom slab, say), the hit moves on to whatever lies behind it.
     * Billboards and multi-tile objects are left exactly as they were. Does nothing if the camera
     * is unknown or the hit was already refined.
     */
    public static void refine(Level level, HitResult hit) {
        if (hit == null || hit.hasPoint || !CameraRay.isKnown()) {
            return;
        }
        Tile start = Tile.byId(level.getTile(hit.x, hit.y, hit.z));
        if (start == null || start == Tile.AIR || start.isBillboard()
                || level.getMultiTile(hit.x, hit.y, hit.z) != null) {
            return;
        }

        // A tile that fills its cell was already hit correctly by the cell stepping.
        List<AABB> shape = start.getPickBoxes(level, hit.x, hit.y, hit.z);
        if (shape.size() == 1 && isWholeCell(shape.get(0), hit.x, hit.y, hit.z)) {
            return;
        }

        double ox = CameraRay.eyeX(), oy = CameraRay.eyeY(), oz = CameraRay.eyeZ();
        double[] d = CameraRay.direction();
        double dx = d[0], dy = d[1], dz = d[2];

        // The camera is from the last drawn frame; if its ray doesn't even pass through the cell that
        // was hit, it is out of date (or wrong) and the original hit is the better answer.
        AABB cell = new AABB(hit.x - 0.05, hit.y - 0.05, hit.z - 0.05, hit.x + 1.05, hit.y + 1.05, hit.z + 1.05);
        if (intersect(cell, ox, oy, oz, dx, dy, dz) == null) {
            return;
        }

        Result direct = pick(level, hit.x, hit.y, hit.z, ox, oy, oz, dx, dy, dz, Double.MAX_VALUE);
        if (direct != null) {
            apply(hit, direct, hit.x, hit.y, hit.z);
            return;
        }

        // The ray went through the empty part of this cell: walk on along it (grid traversal).
        int cx = (int) Math.floor(ox), cy = (int) Math.floor(oy), cz = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double inf = Double.POSITIVE_INFINITY;
        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : inf;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : inf;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : inf;
        double tMaxX = dx > 0 ? (cx + 1 - ox) / dx : dx < 0 ? (ox - cx) / -dx : inf;
        double tMaxY = dy > 0 ? (cy + 1 - oy) / dy : dy < 0 ? (oy - cy) / -dy : inf;
        double tMaxZ = dz > 0 ? (cz + 1 - oz) / dz : dz < 0 ? (oz - cz) / -dz : inf;

        boolean passed = false;
        double limit = inf;
        for (int step = 0; step < 512; step++) {
            double next = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            if (!passed) {
                if (cx == hit.x && cy == hit.y && cz == hit.z) {
                    passed = true;
                    limit = next + 3.0; // only look a little way past the cell that was hit
                }
            } else {
                Tile tile = Tile.byId(level.getTile(cx, cy, cz));
                if (tile != null && tile != Tile.AIR && tile.isSolid()) {
                    if (tile.isBillboard() || level.getMultiTile(cx, cy, cz) != null) {
                        return; // not ours to judge: keep the original hit
                    }
                    Result r = pick(level, cx, cy, cz, ox, oy, oz, dx, dy, dz, limit);
                    if (r != null) {
                        apply(hit, r, cx, cy, cz);
                        return;
                    }
                }
            }

            if (passed && next > limit) {
                return;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cx += sx;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                cy += sy;
                tMaxY += tDeltaY;
            } else {
                cz += sz;
                tMaxZ += tDeltaZ;
            }
        }
    }

    private static boolean isWholeCell(AABB box, int x, int y, int z) {
        double e = 1.0E-4;
        return box.minX <= x + e && box.minY <= y + e && box.minZ <= z + e
                && box.maxX >= x + 1 - e && box.maxY >= y + 1 - e && box.maxZ >= z + 1 - e;
    }

    private static void apply(HitResult hit, Result r, int x, int y, int z) {
        hit.x = x;
        hit.y = y;
        hit.z = z;
        hit.cellX = x;
        hit.cellY = y;
        hit.cellZ = z;
        hit.face = r.face;
        hit.withPoint(r.x, r.y, r.z, r.box);
    }

    /** Slab test of a ray against one box. If the origin is inside, the hit is at t = 0. */
    static Result intersect(AABB box, double ox, double oy, double oz, double dx, double dy, double dz) {
        double[] origin = {ox, oy, oz};
        double[] dir = {dx, dy, dz};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};

        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;
        int axis = -1;

        for (int a = 0; a < 3; a++) {
            if (Math.abs(dir[a]) < 1.0E-12) {
                if (origin[a] < min[a] || origin[a] > max[a]) {
                    return null;
                }
                continue;
            }
            double t1 = (min[a] - origin[a]) / dir[a];
            double t2 = (max[a] - origin[a]) / dir[a];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            if (t1 > tMin) {
                tMin = t1;
                axis = a;
            }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        if (axis < 0 || tMax < 0.0) {
            return null;
        }

        // A ray moving +x enters through the box's west face, and so on.
        int face;
        switch (axis) {
            case 0:  face = dx > 0 ? Tile.FACE_WEST : Tile.FACE_EAST; break;
            case 1:  face = dy > 0 ? Tile.FACE_BOTTOM : Tile.FACE_TOP; break;
            default: face = dz > 0 ? Tile.FACE_NORTH : Tile.FACE_SOUTH; break;
        }

        double t = Math.max(tMin, 0.0);
        return new Result(t, ox + dx * t, oy + dy * t, oz + dz * t, face, box);
    }
}
