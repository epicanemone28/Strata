package com.nikohalfkino.rubydung.assets;

import com.nikohalfkino.rubydung.util.Direction;

public class ModelFace {
    public String texturePath;
    public Direction direction = Direction.UP;
    public Direction cullDirection = null;
    public float minU = 0.0f;
    public float minV = 0.0f;
    public float maxU = 1.0f;
    public float maxV = 1.0f;
    //measured in count of 90-degree steps
    public int rotation = 0;

    public ModelFace(String texturePath, Direction direction){

        this.texturePath = texturePath;
        this.direction = direction;
    }

    public ModelFace(String texturePath, Direction direction, int rotation, Direction cullDirection){
        this.texturePath = texturePath;
        this.direction = direction;
        this.rotation = rotation;
        this.cullDirection = cullDirection;
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

    public ModelFace setUVs(float u1, float v1, float u2, float v2){
        this.minU = Math.min(u1,u2);
        this.maxU = Math.max(u1,u2);
        this.minV = Math.min(v1,v2);
        this.maxV = Math.max(v1,v2);
        return this;
    }


    public ModelFace clone(){
        return new ModelFace(this.texturePath,this.direction,this.rotation,this.cullDirection);
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
            case WEST: return new float[][]{v[1],v[5],v[7],v[3]};
            case NORTH: return new float[][]{v[3],v[7],v[6],v[2]};
            case EAST: return new float[][]{v[2],v[6],v[4],v[0]};
            case SOUTH: return new float[][]{v[0],v[4],v[5],v[1]};
            case UP: return new float[][]{v[7],v[5],v[4],v[6]};
            case DOWN: return new float[][]{v[2],v[0],v[1],v[3]};
            default: throw new RuntimeException("????? invalid direction when creating quad ?????");
        }
    }
}
