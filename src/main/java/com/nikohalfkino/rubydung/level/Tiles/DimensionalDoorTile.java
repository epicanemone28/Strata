package com.nikohalfkino.rubydung.level.Tiles;

import com.nikohalfkino.rubydung.level.Level;

/**
 * A three-tall billboard door with a colour and two states. Every colour/state combination is its
 * own tile (e.g. dimensional_door_blue.png / dimensional_door_blue_open.png; regular doors use
 * dimensional_door.png / dimensional_door_open.png), all occupying a 1x3x1 footprint.
 * {@link #setOpen} swaps one state for the other in place, keeping position, colour and rotation.
 */
public class DimensionalDoorTile extends BillboardTile {

    public static final float HEIGHT = 3.0F;

    // No static state here on purpose: Tile's static initialiser constructs these doors, and this
    // class can be initialised first (e.g. via textureName), so the constructor must not need any.

    private final DoorColor color;
    private final boolean open;

    public DimensionalDoorTile(DoorColor color, boolean open) {
        super(textureName(color, open), textureName(color, open), HEIGHT);
        // Occupies 3 real cells (1x3x1), like ColumnTile does for its height.
        occupyHeight();
        this.color = color;
        this.open = open;
    }

    /** Name of both the tile and its texture, e.g. "dimensional_door_blue_open". */
    public static String textureName(DoorColor color, boolean open) {
        String name = "dimensional_door";
        if (!color.getSuffix().isEmpty()) {
            name += "_" + color.getSuffix();
        }
        return open ? name + "_open" : name;
    }

    /** The registered tile for a colour and state. */
    public static DimensionalDoorTile forColor(DoorColor color, boolean open) {
        return (DimensionalDoorTile) Tile.byName(textureName(color, open));
    }

    public DoorColor getColor() {
        return this.color;
    }

    public boolean isOpen() {
        return this.open;
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    /** The placeable (closed) form of this door; what pick-block should select. */
    public Tile getClosedForm() {
        return forColor(this.color, false);
    }

    /**
     * Opens or closes the door whose origin cell is (x, y, z). Does nothing if that cell isn't a
     * dimensional door or is already in the requested state.
     */
    public static void setOpen(Level level, int x, int y, int z, boolean open) {
        Tile current = Tile.byId(level.getTile(x, y, z));
        if (!(current instanceof DimensionalDoorTile) || ((DimensionalDoorTile) current).open == open) {
            return;
        }

        Tile target = forColor(((DimensionalDoorTile) current).color, open);
        int rotation = level.getData(x, y, z);

        // Overwriting any cell of a multi-tile object removes the whole object, then place the other state.
        level.setTile(x, y, z, 0);
        level.setTile(x, y, z, target.id, rotation);
    }
}