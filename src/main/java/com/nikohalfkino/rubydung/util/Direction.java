package com.nikohalfkino.rubydung.util;

import java.util.Dictionary;
import java.util.LinkedHashMap;
import java.util.Map;

public class Direction {
    private static final LinkedHashMap<String,Direction> allDirs = new LinkedHashMap<>();

    static Direction NORTH = new Direction("north",1,0,0);
    static Direction SOUTH = new Direction("south",-1,0,0);
    static Direction EAST = new Direction("east",0,0,1);
    static Direction WEST = new Direction("west",0,0,-1);
    static Direction UP = new Direction("up",0,1,0);
    static Direction DOWN = new Direction("down",0,-1,0);


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

        allDirs.put(name, this);
    }


}
