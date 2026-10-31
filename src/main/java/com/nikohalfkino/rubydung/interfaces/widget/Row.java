package com.nikohalfkino.rubydung.interfaces.widget;

/** Stacks its children left to right. See {@link Stack}. */
public class Row extends Stack {

    public Row(float gap) {
        super(false, gap);
    }
}
