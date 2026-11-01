package com.nikohalfkino.rubydung.util;

import java.util.*;

public enum Direction {

    NORTH("north",0,0,1, Axis.Z),
    EAST("east",-1,0,0, Axis.X),
    SOUTH("south",0,0,-1, Axis.Z),
    WEST("west",1,0,0, Axis.X),
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

    static private Direction[] getRotationOrder(Axis axis){
        switch(axis){
            case X: return new Direction[]{NORTH, UP, SOUTH, DOWN};
            case Y: return new Direction[]{EAST, SOUTH, WEST, NORTH};
            case Z: return new Direction[]{EAST, DOWN, WEST, UP};
            default: throw new RuntimeException("tried to rotate around a null axis!");
        }
    }

    public Direction rotate(Axis axis,int offset) {
        if(this.axis == axis) return this;

        Direction[] order = getRotationOrder(axis);
        for(int i = 0; i<order.length; i++){
            if(order[i]==this) return order[Math.floorMod(i+offset,4)];
        }

        return this;
    }

    public Direction getOpposite(){
        switch (this){
            case WEST: return EAST;
            case EAST: return WEST;
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case UP: return DOWN;
            case DOWN: return UP;
            default: throw new RuntimeException("tried to get the opposite of a null direction!");
        }
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

    public boolean isPositive() {
        return (this.x+this.y+this.z)>0;
    }


}
