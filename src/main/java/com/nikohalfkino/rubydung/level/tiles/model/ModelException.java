package com.nikohalfkino.rubydung.level.tiles.model;

/** a model, blockstate, JSON or TOML file is malformed or refers to something that doesn't exist */
public final class ModelException extends RuntimeException {

    public ModelException(String message) {
        super(message);
    }
}
