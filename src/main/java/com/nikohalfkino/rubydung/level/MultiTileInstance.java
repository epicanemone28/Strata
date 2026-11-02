package com.nikohalfkino.rubydung.level;

import com.nikohalfkino.rubydung.level.tiles.Tile;

public final class MultiTileInstance {

    public final Tile tile;
    public final int x, y, z;
    public final int sizeX, sizeY, sizeZ;

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
