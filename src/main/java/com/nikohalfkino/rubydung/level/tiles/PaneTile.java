package com.nikohalfkino.rubydung.level.tiles;

import com.nikohalfkino.rubydung.level.Level;
import com.nikohalfkino.rubydung.level.SubTile;
import com.nikohalfkino.rubydung.render.Tessellator;

/**
 * The common class for thin, flat, see-through blocks that stand in the middle of a cell and
 * extend vertically: metal bars, framed glass, and whatever comes next. To add one:
 * <ol>
 *   <li>a 16x48 texture (top / middle / bottom 16x16 slices),</li>
 *   <li>a blockstate that extends the shared one:
 *       <pre>
 * parent = "pane"
 * [textures]
 * top = "my_pane@0,0"
 * middle = "my_pane@0,1"
 * bottom = "my_pane@0,2"</pre></li>
 *   <li>{@code register(new PaneTile("my_pane"))} in TileRegistry.</li>
 * </ol>
 *
 * <p>The shape (flat plane, 2 px collision), the facing and the stacking rule all live here and in
 * blockstates/pane.toml. Facing north or south gives a plane running east-west, facing east or
 * west one running north-south (the plane faces the player when placed).
 *
 * <p>Only the facing is stored in the world. The "part" property (top / middle / bottom) is worked
 * out when drawing from the panes of the same plane above, so nothing needs updating when
 * neighbours change:
 * <pre>
 *   height 1: top
 *   height 2: top, middle                (the lower one is "middle")
 *   height 3: top, middle, bottom
 *   height 4+: top, middle ... middle, bottom
 * </pre>
 *
 * <p>Panes may cross slabs: they can be placed into a cell that holds a slab (and the other way
 * round) and the two simply intersect. See {@link #canIntersect}.
 */
public class PaneTile extends FacingTile {

    private static final String[] PARTS = {"top", "middle", "bottom"};
    private static final int TOP = 0, MIDDLE = 1, BOTTOM = 2;

    /** data byte of every (facing, part) combination, for the model state to draw with */
    private final int[][] stateData = new int[NAMES.length][PARTS.length];

    public PaneTile(String name) {
        super(name);
        setTransparent(true);   // see-through: lets light and neighbours' faces through
        for (int facing = 0; facing < NAMES.length; facing++) {
            for (int part = 0; part < PARTS.length; part++) {
                this.stateData[facing][part] = dataOf("facing", NAMES[facing], "part", PARTS[part]);
            }
        }
    }

    /**
     * Placed against a side face the pane faces away from that wall, as for any FacingTile. A top or
     * bottom click (the top of a slab, the floor) says nothing about direction, so the pointer's
     * spot in the cell decides: near the east or west edge the pane runs north-south, near the
     * north or south edge east-west. Aim at the middle for the default (east-west).
     * (A placement call that passes the look direction overrides all this: see FacingTile.)
     */
    @Override
    public int getPlacementData(int face, double fractionX, double fractionY, double fractionZ) {
        if ((face == FACE_TOP || face == FACE_BOTTOM)
                && !Double.isNaN(fractionX) && !Double.isNaN(fractionZ)) {
            double fromEastWestEdge = Math.abs(fractionX - 0.5);
            double fromNorthSouthEdge = Math.abs(fractionZ - 0.5);
            return stateData[fromEastWestEdge > fromNorthSouthEdge ? EAST : NORTH][TOP];
        }
        return super.getPlacementData(face, fractionX, fractionY, fractionZ);
    }

    /** Slabs pass through panes (and panes through slabs). */
    @Override
    public boolean canIntersect(Tile other) {
        return other instanceof SlabTile;
    }

    @Override
    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z,
                       int totalAtlasSlots, int data) {
        int facing = facingOf(data);

        int part;
        if (!connects(level, x, y + 1, z, facing)) {
            part = TOP;                                   // nothing above: top (a lone pane too)
        } else if (!connects(level, x, y - 1, z, facing)) {
            // The bottom of a stack: "bottom" from height 3 on, "middle" at height 2.
            part = connects(level, x, y + 2, z, facing) ? BOTTOM : MIDDLE;
        } else {
            part = MIDDLE;
        }

        super.render(tessellator, level, layer, x, y, z, totalAtlasSlots, this.stateData[facing][part]);
    }

    /** Whether the cell holds this pane in the same plane (as its tile or as a tile sharing the cell). */
    private boolean connects(Level level, int x, int y, int z, int facing) {
        int plane = facing % 2;
        if (level.getTile(x, y, z) == this.id && facingOf(level.getData(x, y, z)) % 2 == plane) {
            return true;
        }
        if (level.getSubTiles().has(x, y, z)) {
            for (SubTile sub : level.getSubTiles().at(x, y, z)) {
                if (sub.tileId == this.id && facingOf(SubTile.baseData(sub.data)) % 2 == plane) {
                    return true;
                }
            }
        }
        return false;
    }
}
