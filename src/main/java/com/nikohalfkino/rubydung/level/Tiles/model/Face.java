package com.nikohalfkino.rubydung.level.Tiles.model;

/**
 * Cube faces. The numbering is the same as Tile.FACE_* (BOTTOM, TOP, NORTH = -Z, SOUTH = +Z,
 * WEST = -X, EAST = +X), so a face index can be passed between the two without translation.
 */
public final class Face {

    private Face() {}

    public static final int DOWN = 0;
    public static final int UP = 1;
    /** The -Z face (called "south" in model files). */
    public static final int NEG_Z = 2;
    /** The +Z face (called "north" in model files). */
    public static final int POS_Z = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    /** Names as used in model JSON, by face index. North is +Z, so index 2 (-Z) is "south". */
    public static final String[] NAMES = {"down", "up", "south", "north", "west", "east"};

    /** Outward normal per face. */
    public static final int[][] NORMALS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    /**
     * The 4 corners of each face, as 0 = min / 1 = max per axis, in the order top-left, bottom-left,
     * bottom-right, top-right as seen from outside (counter-clockwise, which is the winding the
     * renderer expects). Corner i takes UV corner i: (u1,v1), (u1,v2), (u2,v2), (u2,v1).
     */
    static final int[][][] CORNERS = {
            /* DOWN  */ {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            /* UP    */ {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            /* -Z    */ {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            /* +Z    */ {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            /* WEST  */ {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            /* EAST  */ {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
    };

    /** The face with this name, or -1. Also accepts "bottom" and "top". */
    public static int byName(String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        if (name.equals("bottom")) {
            return DOWN;
        }
        if (name.equals("top")) {
            return UP;
        }
        return -1;
    }

    /** The face whose normal is closest to the given vector (largest component wins). */
    static int fromNormal(double x, double y, double z) {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        if (ax >= ay && ax >= az) {
            return x < 0 ? WEST : EAST;
        }
        if (ay >= az) {
            return y < 0 ? DOWN : UP;
        }
        return z < 0 ? NEG_Z : POS_Z;
    }

    /**
     * One quarter turn about an axis through the origin. With north = +Z:
     * axis 0 (x) takes up to north (+Z), axis 1 (y) takes east to south (-Z, clockwise on a
     * north-up map), axis 2 (z) takes up to east.
     */
    static void quarterTurn(double[] p, int axis) {
        double x = p[0];
        double y = p[1];
        double z = p[2];
        switch (axis) {
            case 0:  p[1] = -z; p[2] = y;  break;
            case 1:  p[0] = z;  p[2] = -x; break;
            default: p[0] = y;  p[1] = -x; break;
        }
    }

    /** Applies the three rotations (quarter turns each) in order x, y, z. */
    static void rotate(double[] p, int turnsX, int turnsY, int turnsZ) {
        for (int i = 0; i < turnsX; i++) quarterTurn(p, 0);
        for (int i = 0; i < turnsY; i++) quarterTurn(p, 1);
        for (int i = 0; i < turnsZ; i++) quarterTurn(p, 2);
    }

    /**
     * Rotates a box {minX, minY, minZ, maxX, maxY, maxZ} (in blocks, cell = 0..1) about the cell
     * centre, with the same turns and order as {@link #rotate(double[], int, int, int)}.
     */
    static float[] rotateBox(float[] box, int turnsX, int turnsY, int turnsZ) {
        if (turnsX == 0 && turnsY == 0 && turnsZ == 0) {
            return box;
        }
        double[] a = {box[0] - 0.5, box[1] - 0.5, box[2] - 0.5};
        double[] b = {box[3] - 0.5, box[4] - 0.5, box[5] - 0.5};
        rotate(a, turnsX, turnsY, turnsZ);
        rotate(b, turnsX, turnsY, turnsZ);
        float[] out = new float[6];
        for (int i = 0; i < 3; i++) {
            out[i] = (float) (Math.min(a[i], b[i]) + 0.5);
            out[i + 3] = (float) (Math.max(a[i], b[i]) + 0.5);
        }
        return out;
    }

    /** The face a face ends up as after the rotations. */
    static int rotate(int face, int turnsX, int turnsY, int turnsZ) {
        double[] v = {NORMALS[face][0], NORMALS[face][1], NORMALS[face][2]};
        rotate(v, turnsX, turnsY, turnsZ);
        return fromNormal(v[0], v[1], v[2]);
    }
}