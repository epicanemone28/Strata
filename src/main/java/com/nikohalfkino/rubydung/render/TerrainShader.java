package com.nikohalfkino.rubydung.render;

import org.lwjgl.opengl.GL20;

/**
 * Draws chunk meshes with the time of day applied at draw time instead of baked into the mesh.
 *
 * Chunk vertex colour is packed as: r = sky light (shadow only), g = block light, b = face shade.
 * Light-emitting tiles write g = 1, so they stay fullbright at any ambient.
 */
public final class TerrainShader {

    private static final String VERT =
            "#version 120\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    gl_Position = ftransform();\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    vColor = gl_Color;\n" +
            "}\n";

    private static final String FRAG =
            "#version 120\n" +
            "uniform sampler2D tex;\n" +
            "uniform float ambient;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    vec4 c = texture2D(tex, gl_TexCoord[0].st);\n" +
            "    float s = vColor.r * ambient;\n" +
            "    float l = (s + (1.0 - s) * vColor.g) * vColor.b;\n" +
            "    gl_FragColor = vec4(c.rgb * l, c.a);\n" +
            "}\n";

    private static int program = 0;
    private static int ambientLocation = -1;

    private TerrainShader() {
    }

    public static void bind(float ambient) {
        if (program == 0) {
            init();
        }
        GL20.glUseProgram(program);
        GL20.glUniform1f(ambientLocation, ambient);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    private static void init() {
        int vs = compile(GL20.GL_VERTEX_SHADER, VERT);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAG);

        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Terrain shader link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        }

        GL20.glUseProgram(p);
        GL20.glUniform1i(GL20.glGetUniformLocation(p, "tex"), 0);
        GL20.glUseProgram(0);

        ambientLocation = GL20.glGetUniformLocation(p, "ambient");
        program = p;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Terrain shader compile failed: " + GL20.glGetShaderInfoLog(shader, 4096));
        }
        return shader;
    }
}
