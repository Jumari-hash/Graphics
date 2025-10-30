package com.termux.view;

import android.opengl.GLES20;
import android.util.Log;

public class GLUtil {
    public static int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            Log.e("GLUtil", "Shader compile error: " + log);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    public static int createProgram(String v, String f) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, v);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, f);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] link = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            Log.e("GLUtil", "Program link error: " + log);
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }
}
