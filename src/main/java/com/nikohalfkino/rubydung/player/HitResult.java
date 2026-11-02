package com.nikohalfkino.rubydung.player;

import com.nikohalfkino.rubydung.phys.AABB;

public class HitResult {

    public int x;
    public int y;
    public int z;

    public int type;
    public int face;

    /**
     * The cell the ray actually entered. Equal to x/y/z for ordinary tiles; for a multi-tile
     * object x/y/z is the object's origin (so breaking or highlighting acts on the whole object)
     * while this is the specific cell that was hit (so placing against the face lands correctly).
     */
    public int cellX;
    public int cellY;
    public int cellZ;

    /**
     * Where exactly the ray struck the tile, in world coordinates. Only set (hasPoint) when the
     * picker produced the hit; a plain cell-stepping hit leaves these at 0.
     */
    public boolean hasPoint;
    public double hitX;
    public double hitY;
    public double hitZ;

    /**
     * The part of the tile that was struck, in world coordinates: the whole cell for a cube, the
     * slab itself, the one step of a staircase. Null when unknown (the overlay then falls back to
     * the tile's outline box).
     */
    public AABB hitBox;

    /**
     * Which SubTile of the cell was struck (SubTile.id), or -1 for the cell's normal tile. Tiles that
     * share a cell are told apart by this; x/y/z are the cell either way.
     */
    public int overlayId = -1;

    /** Records the exact hit point and the box that was struck. Returns this, for chaining. */
    public HitResult withPoint(double hitX, double hitY, double hitZ, AABB hitBox) {
        this.hasPoint = true;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.hitBox = hitBox;
        return this;
    }

    public HitResult(int x, int y, int z, int type, int face) {
        this(x, y, z, type, face, x, y, z);
    }

    public HitResult(int x, int y, int z, int type, int face, int cellX, int cellY, int cellZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.face = face;
        this.cellX = cellX;
        this.cellY = cellY;
        this.cellZ = cellZ;
    }
}
