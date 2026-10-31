package com.nikohalfkino.rubydung.util;

import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Direction {

    NORTH("north",1,0,0, Axis.X),
    SOUTH("south",-1,0,0, Axis.X),
    EAST("east",0,0,1, Axis.Z),
    WEST("west",0,0,-1, Axis.Z),
    UP("up",0,1,0, Axis.Y),
    DOWN("down",0,-1,0, Axis.Y);

    public static final LinkedHashMap<String,Direction> allDirs = new LinkedHashMap<>();

    private final String name;
    public final int x;
    public final int y;
    public final int z;
    public final Axis axis;

    public String getName() {
        return name;
    }

    public static Direction fromName(String name){
        try {
            return Direction.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private Direction(String name, int x, int y, int z, Axis axis) {
        this.name = name;

        this.axis = axis;

        this.x = x;
        this.y = y;
        this.z = z;
    }

    static {
        for(Direction d : Direction.values()){
            allDirs.put(d.name, d);
        }
    }


}
