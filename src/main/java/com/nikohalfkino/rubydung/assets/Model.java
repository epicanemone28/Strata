package com.nikohalfkino.rubydung.assets;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.nikohalfkino.rubydung.util.Direction;
import jdk.jfr.Category;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Model {

    ArrayList<ModelElement> elements = new ArrayList<>();

    public Model() {

    }


    static Gson gson = new Gson();

    public static Model fromAsset(String modelPath) {
        Model newModel = new Model();
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

            }

        } catch (FileNotFoundException e){
            newModel.elements.add(new ModelElement(0,0,0,16,16,16));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return newModel;
    }
}
