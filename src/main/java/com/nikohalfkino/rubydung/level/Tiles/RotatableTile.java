package com.nikohalfkino.rubydung.level.Tiles;

/**
 * A tile that can lie along any axis (direction 0 = Y, 1 = X, 2 = Z, stored in the data byte).
 * It draws through Tile.render like every other tile; this class only says which texture a face
 * uses and how that texture is oriented once rotated.
 */
public class RotatableTile extends Tile {

    // Face UVs when the tile is turned (per face and vertex: {u, v}, 0 = min, 1 = max).
    // ALT_DIRECTION says which direction value uses them; every other direction uses the default.
    private static final int[][][] ALT_UVS = {
            /* BOTTOM */ {{1, 1}, {0, 1}, {0, 0}, {1, 0}},
            /* TOP    */ {{0, 0}, {1, 0}, {1, 1}, {0, 1}},
            /* NORTH  */ {{1, 1}, {1, 0}, {0, 0}, {0, 1}},
            /* SOUTH  */ {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
            /* WEST   */ {{1, 1}, {1, 0}, {0, 0}, {0, 1}},
            /* EAST   */ {{0, 0}, {0, 1}, {1, 1}, {1, 0}},
    };
    private static final int[] ALT_DIRECTION = {2, 2, 2, 2, 1, 1};

    public RotatableTile(String name) {
        super(name);
    }

    public RotatableTile(String name, String defaultTextureName) {
        super(name, defaultTextureName);
    }

    public boolean isDirectional() {
        return true;
    }

    private int effectiveDirection(int direction) {
        return isDirectional() ? direction : 0;
    }

    private int rotateFace(int worldFace, int axis) {
        if (axis == 0) return worldFace;

        if (axis == 1) {
            if (worldFace == FACE_NORTH || worldFace == FACE_SOUTH) return FACE_TOP;
            if (worldFace == FACE_TOP || worldFace == FACE_BOTTOM) return FACE_NORTH;
            return FACE_WEST;
        }

        if (axis == 2) {
            if (worldFace == FACE_EAST || worldFace == FACE_WEST) return FACE_TOP;
            if (worldFace == FACE_TOP || worldFace == FACE_BOTTOM) return FACE_EAST;
            return FACE_NORTH;
        }

        return worldFace;
    }

    @Override
    protected int getTextureFace(int face, int direction) {
        return rotateFace(face, effectiveDirection(direction));
    }

    /** Self-culling uses the tile's own faces, so a girder's "top/bottom" are its end caps however it lies. */
    @Override
    protected int getCullFace(int face, int direction) {
        return rotateFace(face, effectiveDirection(direction));
    }

    @Override
    protected int[][] getFaceUvs(int face, int direction) {
        int d = effectiveDirection(direction);
        return d == ALT_DIRECTION[face] ? ALT_UVS[face] : super.getFaceUvs(face, d);
    }
}