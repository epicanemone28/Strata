package com.nikohalfkino.rubydung.level.tiles;

public class StatueTile extends BillboardTile {

    private static final int FOOTPRINT = 2;
    private static final int HEIGHT    = 6;

    public StatueTile(String name) {
        super(name, name, HEIGHT);
        setFootprint(FOOTPRINT, HEIGHT, FOOTPRINT);
    }
}