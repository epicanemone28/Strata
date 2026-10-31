package com.nikohalfkino.rubydung.assets;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.nikohalfkino.rubydung.util.Direction;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class Model {

    String modelName;

    public HashMap<String,String> textures = new HashMap<>();
    public ArrayList<ModelElement> elements = new ArrayList<>();

    public Model(String modelName) {
        this.modelName = modelName;
    }

    public void addSimplestCube(int x1, int y1, int z1, int x2, int y2, int z2, String texture){
        ModelElement element = new ModelElement(x1,y1,z1,x2,y2,z2);
        for(Direction d : Direction.allDirs.values()){
            element.faces.add(new ModelFace(texture,d).cullFace());
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

            JsonObject texturesJson = modelJson.getAsJsonObject("textures");
            for(String texture : texturesJson.keySet()){
                newModel.textures.put("#"+texture,texturesJson.get(texture).getAsString());
            }

            JsonArray elements = modelJson.getAsJsonArray("elements");
            for(JsonElement elementJson : elements){

                JsonObject obj = elementJson.getAsJsonObject();

                float[] from = gson.fromJson(obj.get("from").getAsJsonArray(),float[].class);
                float[] to = gson.fromJson(obj.get("to").getAsJsonArray(),float[].class);
                ModelElement element = new ModelElement(from[0], from[1], from[2], to[0], to[1], to[2]);

                JsonObject faces = obj.getAsJsonObject("faces");
                for(String faceName : faces.keySet()){
                    JsonObject face = faces.getAsJsonObject(faceName);
                    String texture = face.get("texture").getAsString();
                    ModelFace newFace = new ModelFace(newModel.textures.get(texture),Direction.fromName(faceName));
                    if(face.has("cullface") && !face.get("cullface").isJsonNull()){
                        newFace.cullDirection = Direction.fromName((face.get("cullface").getAsString()));
                    }
                    if(face.has("uv")){
                        float[] textureUV = gson.fromJson(face.get("uv").getAsJsonArray(),float[].class);
                        newFace.minU = textureUV[0] / 16.0f;
                        newFace.minV = textureUV[1] / 16.0f;
                        newFace.maxU = textureUV[2] / 16.0f;
                        newFace.maxV = textureUV[3] / 16.0f;
                    }
                    if(face.has("rotation")){
                        int rotation = face.get("rotation").getAsInt();
                        if(rotation % 90 != 0) throw new IllegalArgumentException("rotation must be a multiple of 90 degrees!");
                        newFace.rotation = rotation/90;
                    }
                    element.faces.add(newFace);
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
