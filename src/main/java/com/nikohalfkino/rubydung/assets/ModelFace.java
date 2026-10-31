package com.nikohalfkino.rubydung.assets;

import com.nikohalfkino.rubydung.util.Direction;

public class ModelFace {
    public String texturePath;
    public Direction direction = Direction.UP;
    public Direction cullDirection = null;
    public float minU = 0;
    public float minV = 0;
    public float maxU = 1.0f;
    public float maxV = 1.0f;
    //measured in count of 90-degree steps
    public int rotation = 0;

    public ModelFace(String texturePath, Direction direction){

        this.texturePath = texturePath;
        this.direction = direction;
    }


    public ModelFace cullFace(Direction d){
        this.cullDirection = d;
        return this;
    }

    public ModelFace cullFace(){
        this.cullDirection = this.direction;
        return this;
    }

    public ModelFace clearCullFace(){
        this.cullDirection = null;
        return this;
    }

    
    public float[][] getQuadFromCube(ModelElement elt,float x, float y, float z){
        float s = 16.0f;
        float[][] v = new float[][]{
                {elt.x1/s+x,elt.y1/s+y,elt.z1/s+z},
                {elt.x2/s+x,elt.y1/s+y,elt.z1/s+z},
                {elt.x1/s+x,elt.y1/s+y,elt.z2/s+z},
                {elt.x2/s+x,elt.y1/s+y,elt.z2/s+z},
                {elt.x1/s+x,elt.y2/s+y,elt.z1/s+z},
                {elt.x2/s+x,elt.y2/s+y,elt.z1/s+z},
                {elt.x1/s+x,elt.y2/s+y,elt.z2/s+z},
                {elt.x2/s+x,elt.y2/s+y,elt.z2/s+z}
        };

        switch(this.direction){
            case NORTH: return new float[][]{v[1],v[5],v[7],v[3]};
            case EAST: return new float[][]{v[3],v[7],v[6],v[2]};
            case SOUTH: return new float[][]{v[2],v[6],v[4],v[0]};
            case WEST: return new float[][]{v[0],v[4],v[5],v[1]};
            case UP: return new float[][]{v[6],v[7],v[5],v[4]};
            case DOWN: return new float[][]{v[3],v[2],v[0],v[1]};
            default: throw new RuntimeException("????? invalid direction when creating quad ?????");
        }
    }
}
