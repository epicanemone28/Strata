package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.player.HitResult;
import com.nikohalfkino.rubydung.level.tiles.PaneTile;
import com.nikohalfkino.rubydung.level.tiles.SlabTile;
import com.nikohalfkino.rubydung.level.tiles.Tile;
import com.nikohalfkino.rubydung.phys.AABB;

import java.util.List;

/**
 * Decides where a tile goes when it is placed INTO a cell that already has something in it, and
 * whether it fits there.
 *
 * <ol>
 *   <li>The cell is the one holding the spot just outside the surface that was clicked. That is the
 *       next cell over for a face on a cell boundary, but the clicked cell itself for a face inside
 *       it (the top of a bottom slab).</li>
 *   <li>Along the clicked axis the tile's shape is pushed flush against that surface.</li>
 *   <li>Across the surface it is centred on the click point, snapped to 1/16 block and kept
 *       inside the cell.</li>
 *   <li>If the result overlaps anything already in the cell, or the player, nothing is placed.</li>
 * </ol>
 */
public final class SubTilePlacement {

    /** Offsets snap to this (one texture pixel on a 16px texture). */
    public static final double GRID = 1.0 / 16.0;

    private static final double EPS = SubTiles.EPSILON;

    private static final int[][] NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private SubTilePlacement() {}

    /** A placement that passed every check. */
    public static final class Plan {
        public final int x, y, z;
        public final int data;
        public final float ox, oy, oz;

        Plan(int x, int y, int z, int data, float ox, float oy, float oz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.data = data;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        public SubTile commit(Level level, Tile tile) {
            return level.getSubTiles().add(this.x, this.y, this.z, tile.id, this.data, this.ox, this.oy, this.oz);
        }
    }

    /** The cell {x, y, z} that holds the spot just outside the clicked surface. */
    public static int[] targetCell(HitResult hit) {
        int[] n = NORMALS[hit.face];
        if (!hit.hasPoint) {
            return new int[]{hit.cellX + n[0], hit.cellY + n[1], hit.cellZ + n[2]};
        }
        final double nudge = 1.0E-3;
        return new int[]{
                (int) Math.floor(hit.hitX + n[0] * nudge),
                (int) Math.floor(hit.hitY + n[1] * nudge),
                (int) Math.floor(hit.hitZ + n[2] * nudge)
        };
    }

    /**
     * @param data      the data byte the tile would get here (the caller works it out from where in
     *                  {@code cell} the player pointed)
     * @param playerBox the player's box, or null to ignore the player
     * @return the placement, or null if the tile can't share, or doesn't fit in, that cell
     */
    public static Plan plan(Level level, HitResult hit, Tile tile, int data, int[] cell, AABB playerBox) {
        if (hit == null || !hit.hasPoint || tile == null || !(tile.canShareCell() || tile.canClipToHalf())) {
            return null;
        }
        int cx = cell[0], cy = cell[1], cz = cell[2];
        if (cx < 0 || cy < 0 || cz < 0 || cx >= level.width || cy >= level.depth || cz >= level.height) {
            return null;
        }
        SubTiles store = level.getSubTiles();
        if (store.at(cx, cy, cz).size() >= SubTiles.MAX_PER_CELL) {
            return null;
        }

        // Ordinary blocks go into the free half of a cell as a half block.
        if (tile.canClipToHalf() && !tile.canShareCell()) {
            return planHalfBlock(level, hit, tile, data, cell, playerBox);
        }

        // Panes keep their authored place (centred, full height) and may cross slabs.
        if (tile instanceof PaneTile) {
            return planPane(level, (PaneTile) tile, data, cell, playerBox);
        }

        // Slabs are placed as exact halves (no shifting), so different kinds mix freely in one cell.
        if (tile instanceof SlabTile) {
            return planSlab(level, hit, (SlabTile) tile, cell, playerBox);
        }

        // The tile's shape as authored, relative to the cell corner.
        List<AABB> shape = tile.getOccupancyBoxes(0, 0, 0, data);
        if (shape.isEmpty()) {
            return null;
        }
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (AABB b : shape) {
            min[0] = Math.min(min[0], b.minX);
            min[1] = Math.min(min[1], b.minY);
            min[2] = Math.min(min[2], b.minZ);
            max[0] = Math.max(max[0], b.maxX);
            max[1] = Math.max(max[1], b.maxY);
            max[2] = Math.max(max[2], b.maxZ);
        }

        int[] n = NORMALS[hit.face];
        int axis = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2;
        boolean positive = n[axis] > 0;
        double[] local = {hit.hitX - cx, hit.hitY - cy, hit.hitZ - cz};

        double[] offset = new double[3];

        // Along the clicked axis: flush against the surface.
        double plane = snap(local[axis]);
        offset[axis] = positive ? plane - min[axis] : plane - max[axis];

        // Across it: centred on the click, snapped, inside the cell.
        for (int t = 0; t < 3; t++) {
            if (t == axis) {
                continue;
            }
            double lowest = -min[t];
            double highest = 1.0 - max[t];
            if (highest < lowest - EPS) {
                return null;
            }
            double wanted = local[t] - (min[t] + max[t]) * 0.5;
            offset[t] = Math.min(Math.max(snap(wanted), lowest), highest);
        }

        // Must stay inside the cell along the clicked axis too (no room left otherwise).
        if (min[axis] + offset[axis] < -EPS || max[axis] + offset[axis] > 1.0 + EPS) {
            return null;
        }

        float ox = (float) offset[0];
        float oy = (float) offset[1];
        float oz = (float) offset[2];

        // Overlap with whatever is already there, and with the player.
        List<AABB> placed = new java.util.ArrayList<>(shape.size());
        for (AABB b : shape) {
            placed.add(new AABB(cx + b.minX + ox, cy + b.minY + oy, cz + b.minZ + oz,
                    cx + b.maxX + ox, cy + b.maxY + oy, cz + b.maxZ + oz));
        }
        if (SubTiles.overlapsAny(placed, store.cellOccupancyFor(cx, cy, cz, tile))) {
            return null;
        }
        if (playerBox != null) {
            for (AABB b : placed) {
                if (SubTiles.overlaps(b, playerBox)) {
                    return null;
                }
            }
        }
        return new Plan(cx, cy, cz, data, ox, oy, oz);
    }

    /** Which half of the cell the player means: the upper one (true) or the lower. */
    private static boolean chooseTop(HitResult hit, int[] cell) {
        boolean insideClickedCell = cell[0] == hit.cellX && cell[1] == hit.cellY && cell[2] == hit.cellZ;
        double localY = hit.hitY - cell[1];
        if (insideClickedCell) {
            // The surface clicked is inside the cell (the top of a bottom slab): the new half sits on it.
            return hit.face == Tile.FACE_TOP ? true
                    : hit.face == Tile.FACE_BOTTOM ? false
                    : localY >= 0.5;
        }
        return hit.face == Tile.FACE_BOTTOM ? true
                : hit.face == Tile.FACE_TOP ? false
                : localY >= 0.5;
    }

    /**
     * A full block placed into a cell that already holds something (a slab) fills the free half only:
     * it becomes a sub-tile flagged as the lower or upper half of itself, so it is drawn, collided
     * with and picked as a half block. Fails if that half is not free.
     */
    private static Plan planHalfBlock(Level level, HitResult hit, Tile tile, int data, int[] cell, AABB playerBox) {
        int cx = cell[0], cy = cell[1], cz = cell[2];
        Tile host = Tile.byId(level.getTile(cx, cy, cz));
        if (host != null && host.isBillboard()) {
            return null;                // a half block does not go into a sprite's cell
        }
        SubTiles store = level.getSubTiles();
        if (store.at(cx, cy, cz).size() >= SubTiles.MAX_PER_CELL) {
            return null;
        }

        int clip = chooseTop(hit, cell) ? SubTile.CLIP_TOP : SubTile.CLIP_BOTTOM;
        List<AABB> placed = SubTiles.clipBoxes(tile.getOccupancyBoxes(cx, cy, cz, data), cy, clip);
        if (placed.isEmpty() || SubTiles.overlapsAny(placed, store.cellOccupancyFor(cx, cy, cz, tile))) {
            return null;
        }
        if (playerBox != null) {
            for (AABB b : placed) {
                if (SubTiles.overlaps(b, playerBox)) {
                    return null;
                }
            }
        }
        return new Plan(cx, cy, cz, data | clip, 0.0F, 0.0F, 0.0F);
    }

    /**
     * A slab goes into a cell as one exact half (bottom or top, no offset), next to whatever else
     * is there: a wooden bottom slab with a metal grate on top, two different slabs in one cell.
     * The half comes from what was clicked; it only fits if that half is free.
     */
    private static Plan planSlab(Level level, HitResult hit, SlabTile slab, int[] cell, AABB playerBox) {
        int cx = cell[0], cy = cell[1], cz = cell[2];
        boolean top = chooseTop(hit, cell);

        int data = slab.halfData(top);
        List<AABB> placed = slab.getOccupancyBoxes(cx, cy, cz, data);
        if (placed.isEmpty()) {
            return null;
        }
        SubTiles store = level.getSubTiles();
        if (SubTiles.overlapsAny(placed, store.cellOccupancyFor(cx, cy, cz, slab))) {
            return null;
        }
        if (playerBox != null) {
            for (AABB b : placed) {
                if (SubTiles.overlaps(b, playerBox)) {
                    return null;
                }
            }
        }
        return new Plan(cx, cy, cz, data, 0.0F, 0.0F, 0.0F);
    }

    /**
     * A pane goes into the cell exactly where it was authored (no offset: it is a full-height plane
     * through the middle, so there is nothing to slide). It fits if nothing but things it may cross
     * (slabs) is in its way.
     */
    private static Plan planPane(Level level, PaneTile pane, int data, int[] cell, AABB playerBox) {
        int cx = cell[0], cy = cell[1], cz = cell[2];
        List<AABB> placed = pane.getOccupancyBoxes(cx, cy, cz, data);
        if (placed.isEmpty()) {
            return null;
        }
        if (SubTiles.overlapsAny(placed, level.getSubTiles().cellOccupancyFor(cx, cy, cz, pane))) {
            return null;
        }
        if (playerBox != null) {
            for (AABB b : placed) {
                if (SubTiles.overlaps(b, playerBox)) {
                    return null;
                }
            }
        }
        return new Plan(cx, cy, cz, data, 0.0F, 0.0F, 0.0F);
    }

    private static double snap(double value) {
        return Math.round(value / GRID) * GRID;
    }
}
