package com.nikohalfkino.rubydung.assets;

public class AssetPath {
    public String namespace;
    public String path;

    public AssetPath(String namespace, String path){
        this.namespace = namespace;
        this.path = path;
    }

    public static AssetPath parse(String fullPath){
        int colon = fullPath.indexOf(":");
        String namespace = fullPath.substring(0,colon);
        String path = fullPath.substring(colon+1);

        return new AssetPath(namespace,path);
    }

    public String asModelPath(){
        return "assets/" + namespace + "/models/" + path + ".json";
    }
}
