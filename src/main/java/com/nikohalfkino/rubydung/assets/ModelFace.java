package com.nikohalfkino.rubydung.assets;

import com.nikohalfkino.rubydung.util.Direction;

public class ModelFace {
    public String texturePath;
    public final Direction direction;
    public final Direction cullDirection = null;

    public ModelFace(String texturePath, Direction direction){
        this.texturePath = texturePath;
        this.direction = direction;
    }
}
