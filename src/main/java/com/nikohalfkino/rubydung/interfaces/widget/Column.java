package com.nikohalfkino.rubydung.interfaces.widget;

/**
 * Stacks its children top to bottom. See {@link Stack}.
 * <pre>
 *     Column menu = add(new Column(10), Anchor.TOP_CENTER, 0, 100);
 *     menu.add(new Button("Play", 200, 30, true));
 *     menu.add(new Button("Quit", 200, 30, true));
 * </pre>
 */
public class Column extends Stack {

    public Column(float gap) {
        super(true, gap);
    }
}
