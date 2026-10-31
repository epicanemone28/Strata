package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.level.Tiles.Tile;

/**
 * One placed multi-tile object, e.g. an 8-high column or a 2x2 statue.
 *
 * The object covers the box that starts at the origin (its minimum corner) and extends
 * sizeX/sizeY/sizeZ cells in +x/+y/+z. Every cell in that box holds the tile's id in the level,
 * so collision, picking, light blocking and saving all see real cells. The sizes are already
 * rotated (a 2x1 tile placed with rotation 1 has sizeX = 1, sizeZ = 2).
 */
public final class MultiTileInstance {

    public final Tile tile;
    public final int x, y, z;
    public final int sizeX, sizeY, sizeZ;

    /** 0..3, quarter turns around the Y axis. Also stored in the level's data byte for every cell. */
    public final int rotation;

    public MultiTileInstance(Tile tile, int x, int y, int z, int rotation) {
        this.tile = tile;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotation = rotation & 3;
        this.sizeX = tile.getSizeX(this.rotation);
        this.sizeY = tile.getSizeY();
        this.sizeZ = tile.getSizeZ(this.rotation);
    }

    public boolean contains(int px, int py, int pz) {
        return px >= this.x && px < this.x + this.sizeX
                && py >= this.y && py < this.y + this.sizeY
                && pz >= this.z && pz < this.z + this.sizeZ;
    }

    public boolean isOrigin(int px, int py, int pz) {
        return px == this.x && py == this.y && pz == this.z;
    }

    /** Offset of a cell from the origin, for tiles that want to render each part differently. */
    public int localX(int px) {
        return px - this.x;
    }

    public int localY(int py) {
        return py - this.y;
    }

    public int localZ(int pz) {
        return pz - this.z;
    }
}
