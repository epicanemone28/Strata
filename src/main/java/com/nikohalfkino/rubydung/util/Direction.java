package com.nikohalfkino.rubydung.util;

import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Direction {

    NORTH("north",1,0,0),
    SOUTH("south",-1,0,0),
    EAST("east",0,0,1),
    WEST("west",0,0,-1),
    UP("up",0,1,0),
    DOWN("down",0,-1,0);

    public static final LinkedHashMap<String,Direction> allDirs = new LinkedHashMap<>();

    private final String name;
    public final int x;
    public final int y;
    public final int z;

    public String getName() {
        return name;
    }

    public static Direction fromName(String name){
        if(!allDirs.containsKey(name)) throw new IllegalArgumentException("no direction with this name!");
        return allDirs.get(name);
    }

    private Direction(String name, int x, int y, int z) {
        this.name = name;
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
