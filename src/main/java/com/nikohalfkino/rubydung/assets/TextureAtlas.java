package com.nikohalfkino.rubydung.assets;

import com.nikohalfkino.rubydung.level.Tiles.Tile;
import com.nikohalfkino.rubydung.render.Tessellator;
import com.nikohalfkino.rubydung.render.Textures;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_NEAREST;

public class TextureAtlas {

    public static final TextureAtlas BLOCK_ATLAS = new TextureAtlas("block");
    public final int texture;
    public HashMap<String,float[]> atlasLookup;


    public TextureAtlas(String textureAssetType) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("assets");
        if(resource == null) throw new RuntimeException("the assets directory is missing??");

        File rootDir = new File(resource.getFile());
        File[] subDirs = rootDir.listFiles();

        ArrayList<Path> texturePaths = new ArrayList<>();

        assert subDirs != null;
        try {
            for(File namespaceDir : subDirs){
                String namespace = namespaceDir.getName();
                texturePaths.addAll(Files.find(
                        Paths.get(namespaceDir.getCanonicalPath(),"textures/"+textureAssetType),
                        999,
                        (p, bfa) -> bfa.isRegularFile()
                ).collect(Collectors.toList()));
            }
        } catch (IOException e){

        }

        int totalTextures = texturePaths.size();
        atlasLookup = new HashMap<>();

        String[] relativeTexturePaths = new String[totalTextures];
        int index = 0;
        for(Path texturePath : texturePaths) {
            AssetPath assetPath = AssetPath.fromDirectory(rootDir.toPath().relativize(texturePath).toString(),"textures");

            atlasLookup.put(assetPath.toString(),new float[]{
                    (float)index/totalTextures,0.0f,
                    ((float)index+1)/totalTextures,1.0f
            });
            relativeTexturePaths[index] = assetPath.asTexturePath();

            index++;
        }

        texture = Textures.buildAtlas(relativeTexturePaths,GL_NEAREST);
    }
}
