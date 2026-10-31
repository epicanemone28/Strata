package com.nikohalfkino.rubydung.level.Tiles;

public class ColumnTile extends BillboardTile {

    public ColumnTile(String name) {
        super(name, "column", 8.0F);
        // Occupies 8 real cells (1x8x1) instead of one cell pretending to be tall.
        occupyHeight();
    }
}
