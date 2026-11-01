package com.nikohalfkino.rubydung.assets;

import com.nikohalfkino.rubydung.util.Axis;

import java.util.ArrayList;

public class ModelElement {
    public float x1;
    public float y1;
    public float z1;
    public float x2;
    public float y2;
    public float z2;
    // currently not assuming everything has 6 faces. probably a safe thing to do?
    public ArrayList<ModelFace> faces = new ArrayList<>();


    public ModelElement(float x1, float y1, float z1, float x2, float y2, float z2) {

        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    public ModelElement rotatedCopy(Axis axis){
        return rotatedCopy(axis,1);
    }

    public ModelElement rotatedCopy(Axis axis, int times){
        ModelElement rotated;
        switch(axis){
            case X:
                rotated = new ModelElement(x1, z1, 16-y1, x2, z2, 16-y2);
                break;
            case Y:
                rotated = new ModelElement(z1, y1, 16-x1, z2, y2, 16-x2);
                break;
            case Z:
                rotated = new ModelElement(16-y1, x1, z1, 16-y2, x2, z2);
                break;
            default:
                throw new RuntimeException("impossible.. non-xyz axis..");
        }

        for(ModelFace face : faces) {
            ModelFace newFace = face.clone();
            newFace.direction = newFace.direction.rotate(axis,1);
            if(newFace.cullDirection != null) newFace.cullDirection = newFace.cullDirection.rotate(axis,1);
            newFace.setUVs(face.minU,face.minV,face.maxU,face.maxV);
            switch(axis){
                case X:
                    if(face.direction.axis == axis){
                        newFace.rotation += face.direction.isPositive() ? -1 : 1;
                    } else {
                        if(newFace.direction.axis == Axis.Z) newFace.rotation += 2;
                    }
                    break;
                case Y:
                    if(face.direction.axis == axis) newFace.rotation += 1;
                    break;
                case Z:
                    // combination X/Z rotation is too intense. suddenly you need to deal with roll + its really hard to test that everything works.
                    throw new UnsupportedOperationException("i'm not implementing that! not only is it unneeded, its also incredibly painful to program!");
            }
            rotated.faces.add(newFace);
        }

        return rotated;
    }

}
