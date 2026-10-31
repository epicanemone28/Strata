package com.nikohalfkino.rubydung.level.Tiles;

public class RockTile extends BillboardTile {

    private static final int HITBOX = 1;

    public RockTile(String name) {
        super(name, name, HITBOX);
        setFootprint(HITBOX, HITBOX, HITBOX);
    }
}