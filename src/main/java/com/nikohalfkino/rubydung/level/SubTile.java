package com.nikohalfkino.rubydung.level;

/**
 * a tile that shares a grid cell with other tiles instead of owning it (a wooden slab in the free half of a stone slab's cell, a thin post on a slab's top). a tile id + data byte like any cell, plus an offset (in blocks) that slides its shape around inside the cell
 *
 * immutable. id is unique within the level and is what picking, breaking and the selection outline use to say "this one", since several can live in one cell
 */
public final class SubTile {

    /** data-byte flags (above any model state): the tile only fills the lower / upper half of its cell */
    public static final int CLIP_BOTTOM = 1 << 16;
    public static final int CLIP_TOP    = 1 << 17;
    private static final int CLIP_MASK  = CLIP_BOTTOM | CLIP_TOP;

    /** data byte without the clip flags, i.e. the model state */
    public static int baseData(int data) {
        return data & ~CLIP_MASK;
    }

    /** CLIP_BOTTOM, CLIP_TOP or 0 */
    public static int clipOf(int data) {
        return data & CLIP_MASK;
    }

    public final int id;
    public final int tileId;
    public final int data;
    public final float ox, oy, oz;

    public SubTile(int id, int tileId, int data, float ox, float oy, float oz) {
        this.id = id;
        this.tileId = tileId;
        this.data = data;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    /** exactly that tile, data and offset (tells "myself" from "the others" while meshing) */
    public boolean isSameAs(int tileId, int data, float ox, float oy, float oz) {
        return this.tileId == tileId && baseData(this.data) == baseData(data)
                && this.ox == ox && this.oy == oy && this.oz == oz;
    }
}
