package com.nikohalfkino.rubydung.assets;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.nikohalfkino.rubydung.util.Direction;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;

public class Model {

    String modelName;

    public ArrayList<ModelElement> elements = new ArrayList<>();

    public Model(String modelName) {
        this.modelName = modelName;
    }

    public void addSimplestCube(int x1, int y1, int z1, int x2, int y2, int z2, String texture){
        ModelElement element = new ModelElement(x1,y1,z1,x2,y2,z2);
        for(Direction d : Direction.allDirs.values()){
            element.faces.add(new ModelFace(texture,d));
        }
        elements.add(element);
    }


    static Gson gson = new Gson();

    public static Model fromAsset(String modelPath) {
        Model newModel = new Model(modelPath);
        URL resource = Thread.currentThread().getContextClassLoader().getResource(modelPath);

        if (resource == null)
            throw new IllegalArgumentException("file is not found!");

        try(FileReader fileReader = new FileReader(resource.getFile())){
            JsonReader reader = new JsonReader(fileReader);

            JsonObject modelJson = ((JsonElement) gson.fromJson(fileReader,JsonElement.class)).getAsJsonObject();
            JsonArray elements = modelJson.getAsJsonArray("elements");
            for(JsonElement elementJson : elements){

                JsonObject obj = elementJson.getAsJsonObject();

                int[] from = gson.fromJson(obj.get("from").getAsJsonArray(),int[].class);
                int[] to = gson.fromJson(obj.get("to").getAsJsonArray(),int[].class);
                ModelElement element = new ModelElement(from[0], from[1], from[2], to[0], to[1], to[2]);

                JsonObject faces = obj.getAsJsonObject("faces");
                for(String face : faces.keySet()){
                    faces.getAsJsonObject(face);
                    element.faces.add(new ModelFace("rubydung:block/gold",Direction.fromName(face)));
                }
                newModel.elements.add(element);

            }

        } catch (FileNotFoundException e){
            newModel.elements.add(new ModelElement(0,0,0,16,16,16));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return newModel;
    }
}
