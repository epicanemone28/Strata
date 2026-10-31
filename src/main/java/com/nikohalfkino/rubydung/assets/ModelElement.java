package com.nikohalfkino.rubydung.assets;

import java.util.ArrayList;

public class ModelElement {
    public int x1;
    public int y1;
    public int z1;
    public int x2;
    public int y2;
    public int z2;
    // currently not assuming everything has 6 faces. probably a safe thing to do?
    public ArrayList<ModelFace> faces = new ArrayList<>();


    public ModelElement(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
    }
}
