package com.nikohalfkino.rubydung.util;

import com.nikohalfkino.rubydung.level.tiles.Tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** minimal ordered registry, entries get sequential ids from registration order. currently unused: Tile keeps its own id list */
public class Registry<T> {

    private final List<T> entries = new ArrayList<>();

    /** the id the next registered entry gets */
    public int nextId() {
        return this.entries.size();
    }

    /** returns the same entry so one expression can register it and assign it to a field */
    public <E extends T> E register(E entry) {
        this.entries.add(entry);
        return entry;
    }

    /** null if no entry has that id */
    public T get(int id) {
        if (id < 0 || id >= this.entries.size()) {
            return null;
        }
        return this.entries.get(id);
    }

    public List<T> entries() {
        return Collections.unmodifiableList(this.entries);
    }

    public int size() {
        return this.entries.size();
    }
}