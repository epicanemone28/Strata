package com.nikohalfkino.rubydung.level.tiles;

/**
 * A tile with a cardinal direction: the base for anything that turns to the four compass points
 * (panes, and later doors, benches, stairs...). Not for tiles that can also lie along the vertical
 * axis; that is {@link RotatableTile}.
 *
 * <p>Its blockstate must list {@code facing = ["north", "east", "south", "west"]} FIRST in
 * [properties], so the facing is always {@code data % 4}; other properties come after it and
 * multiply up from there (see {@link PaneTile}). The constructor checks this.
 *
 * <p>"North" is -Z, "south" +Z, "west" -X, "east" +X, like the tile faces. A tile's facing is the
 * way its FRONT points; placed by the player it faces the player (like a furnace), so it is the
 * opposite of the way they look.
 */
public class FacingTile extends ModelTile {

    public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    public static final String[] NAMES = {"north", "east", "south", "west"};

    public FacingTile(String name) {
        super(name);
        for (int facing = 0; facing < NAMES.length; facing++) {
            if (dataOf("facing", NAMES[facing]) != facing) {
                throw new IllegalStateException("Blockstate of '" + name + "' must start with"
                        + " facing = [\"north\", \"east\", \"south\", \"west\"] in [properties]");
            }
        }
    }

    /** 0..3 (NORTH, EAST, SOUTH, WEST) of any data byte, also one that carries other properties. */
    public static int facingOf(int data) {
        return data % NAMES.length;
    }

    /** The facing that looks back at a player looking along (lookX, lookZ); only the horizontal part matters. */
    public static int facingTowardsPlayer(double lookX, double lookZ) {
        if (Math.abs(lookX) > Math.abs(lookZ)) {
            return lookX > 0 ? WEST : EAST;      // looking east: the front points west, back at them
        }
        return lookZ > 0 ? NORTH : SOUTH;        // looking south (+z): the front points north
    }

    /** Without a look direction (the old call), the clicked face decides: see {@link #facingFromFace}. */
    @Override
    public int getPlacementData(int face, double fractionX, double fractionY, double fractionZ) {
        return facingFromFace(face);
    }

    /** With the player's horizontal look direction: the front faces the player. */
    @Override
    public int getPlacementData(int face, double fractionX, double fractionY, double fractionZ,
                                double lookX, double lookZ) {
        return facingTowardsPlayer(lookX, lookZ);
    }

    /** Placed against a side face, facing away from that wall; on top of / under a block, north. */
    protected int facingFromFace(int face) {
        switch (face) {
            case FACE_SOUTH: return SOUTH;
            case FACE_EAST:  return EAST;
            case FACE_WEST:  return WEST;
            default:         return NORTH;
        }
    }
}
