package com.nikohalfkino.rubydung.level.Tiles;

/**
 * A tile that can lie along any axis. The data byte is the axis: 0 = Y (standing), 1 = Z, 2 = X.
 *
 * <p>Nothing about the rotation lives here any more: textures and the axis -> orientation table
 * are in the tile's blockstate file (assets/rubydung/blockstates/&lt;name&gt;.toml, which usually
 * extends axis_column.toml), and the geometry is rotated when the model is baked.
 */
public class RotatableTile extends ModelTile {

    public RotatableTile(String name) {
        super(name);
    }

    public boolean isDirectional() {
        return true;
    }
}
