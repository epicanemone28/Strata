package com.nikohalfkino.rubydung.assets;

import com.sun.org.apache.xerces.internal.impl.xpath.regex.RegularExpression;
import jdk.internal.joptsimple.util.RegexMatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssetPath {
    public String namespace;
    public String path;

    @Override
    public String toString() {
        return this.namespace + "/" + this.path;
    }

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

    private static final Pattern directoryRegex = Pattern.compile("(?:assets[\\\\/])?([a-z_]+)[\\\\/]([a-z_\\\\/]+)\\.([a-z]+)");
    public static AssetPath fromDirectory(String fullPath, String assetType){
        Matcher m = directoryRegex.matcher(fullPath);
        if(!m.find()) return null;


        String namespace = m.group(1);

        String path = m.group(2);
        path = path.replace("\\","/");
        path = path.replace(assetType+"/","");

        String filetype = m.group(3);

        return new AssetPath(namespace,path);
    }

    public String asModelPath(){
        return "assets/" + namespace + "/models/" + path + ".json";
    }
    public String asTexturePath(){ return "/assets/" + namespace + "/textures/" + path + ".png"; }
}
