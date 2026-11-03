package com.nikohalfkino.rubydung.interfaces.widget;

/** stacks its children top to bottom, see Stack */
public class Column extends Stack {

    public Column(float gap) {
        super(true, gap);
    }
}
