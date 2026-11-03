package com.nikohalfkino.rubydung.level.tiles.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** where model and blockstate files come from, swappable so the loaders can be tested without a classpath */
public interface ModelSource {

    /** text of the resource at path (e.g. "/assets/rubydung/models/block/cube.json"), or null if there is none */
    String read(String path);

    /** reads from the classpath, next to the textures */
    ModelSource CLASSPATH = new ModelSource() {
        @Override
        public String read(String path) {
            try (InputStream in = ModelSource.class.getResourceAsStream(path)) {
                if (in == null) {
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ModelException("Could not read " + path + ": " + e.getMessage());
            }
        }
    };
}
