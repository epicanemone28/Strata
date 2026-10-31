package com.nikohalfkino.rubydung.level.Tiles;

/**
 * Colour of a dimensional door. A coloured door can only lead to a world that has a door of the
 * same colour; REGULAR doors are universal and match every colour (see {@link #matches}).
 */
public enum DoorColor {
    REGULAR("", "Regular", 0xE0A868),
    BLUE("blue", "Blue", 0x88AAFF),
    GREEN("green", "Green", 0x90E890),
    PURPLE("purple", "Purple", 0xC8A0FF),
    RED("red", "Red", 0xFF8C8C),
    YELLOW("yellow", "Yellow", 0xFFE680);

    private final String suffix;
    private final String displayName;
    private final int tint;

    DoorColor(String suffix, String displayName, int tint) {
        this.suffix = suffix;
        this.displayName = displayName;
        this.tint = tint;
    }

    /** RGB tint for UI frames that reflect this colour (same style as the other screens' frame tints). */
    public int getTint() {
        return this.tint;
    }

    /** Part of the texture / tile name after "dimensional_door_" ("" for regular doors). */
    public String getSuffix() {
        return this.suffix;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /** Whether a door of this colour and a door of the other colour can be used to reach each other. */
    public boolean matches(DoorColor other) {
        return this == REGULAR || other == REGULAR || this == other;
    }
}