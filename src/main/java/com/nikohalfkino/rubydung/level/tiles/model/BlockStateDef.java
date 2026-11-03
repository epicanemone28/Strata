package com.nikohalfkino.rubydung.level.tiles.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * a tile's properties and the baked model of every combination of them
 *
 * the combination is packed into the cell's data byte as a mixed-radix number, first property least significant. axis = ["y","z","x"] gives 0 = y, 1 = z, 2 = x. no properties = exactly one state (data 0)
 */
public final class BlockStateDef {

    public static final class Property {
        public final String name;
        public final List<String> values;

        Property(String name, List<String> values) {
            this.name = name;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    private final List<Property> properties;
    private final BakedModel[] states;
    private final boolean fullCube;

    BlockStateDef(List<Property> properties, BakedModel[] states) {
        this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
        this.states = states;

        boolean full = true;
        for (BakedModel state : states) {
            full &= state.isFullCube();
        }
        this.fullCube = full;
    }

    public List<Property> properties() {
        return this.properties;
    }

    public int stateCount() {
        return this.states.length;
    }

    /** values past the last state use state 0 (the default) */
    public BakedModel state(int data) {
        return data >= 0 && data < this.states.length ? this.states[data] : this.states[0];
    }

    /** every state fills the whole cell, so neighbours may hide the faces that touch it */
    public boolean isFullCube() {
        return this.fullCube;
    }

    public Set<String> textures() {
        Set<String> names = new LinkedHashSet<>();
        for (BakedModel state : this.states) {
            names.addAll(state.textures());
        }
        return names;
    }

    /** data byte for property values given as name, value pairs: dataOf("axis", "x"). left-out properties take their first value */
    public int dataOf(String... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("dataOf takes name, value pairs");
        }

        int data = 0;
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            int stride = 1;
            Property found = null;
            for (Property property : this.properties) {
                if (property.name.equals(nameValuePairs[i])) {
                    found = property;
                    break;
                }
                stride *= property.values.size();
            }
            if (found == null) {
                throw new IllegalArgumentException("No property '" + nameValuePairs[i] + "'");
            }
            int index = found.values.indexOf(nameValuePairs[i + 1]);
            if (index < 0) {
                throw new IllegalArgumentException("Property '" + found.name + "' has no value '"
                        + nameValuePairs[i + 1] + "' (allowed: " + found.values + ")");
            }
            data += index * stride;
        }
        return data;
    }
}
