package com.nikohalfkino.rubydung.util;

public enum Axis {
    X(Direction.WEST),
    Y(Direction.UP),
    Z(Direction.NORTH);

    public final Direction positiveDirection;

    private Axis(Direction positiveDirection){
        this.positiveDirection = positiveDirection;
    }

}
