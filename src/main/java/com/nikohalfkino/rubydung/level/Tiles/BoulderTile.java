package com.nikohalfkino.rubydung.level.Tiles;

public class BoulderTile extends BillboardTile {

    private static final int HITBOX = 2;

    public BoulderTile(String name) {
        super(name, name, HITBOX);
        setFootprint(HITBOX, HITBOX, HITBOX);
    }
}