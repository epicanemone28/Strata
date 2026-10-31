package com.nikohalfkino.rubydung.util;

public enum Axis {
    X(Direction.NORTH),
    Y(Direction.UP),
    Z(Direction.EAST);

    public final Direction positiveDirection;

    private Axis(Direction positiveDirection){
        this.positiveDirection = positiveDirection;
    }

}
