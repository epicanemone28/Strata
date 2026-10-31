package com.nikohalfkino.rubydung;

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
