package com.termux.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.termux.terminal.TerminalBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * GL renderer that renders terminal characters using a glyph atlas texture.
 *
 * This implementation creates a glyph atlas for ASCII range on first use (CPU-side using Paint),
 * uploads it as a single texture, and then in onDrawFrame it draws quads per character based on
 * the TerminalBuffer contents. It is a basic implementation aimed to be understandable and buildable.
 *
 * For simplicity, a single draw call per frame is used with client-side arrays (works but can be optimized).
 */
public class GLTerminalRenderer implements android.opengl.GLSurfaceView.Renderer {

    private static final String TAG = "GLTerminalRenderer";
    private final Context context;

    // simple shader sources
    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;" +
            "attribute vec2 aTexCoord;" +
            "varying vec2 vTexCoord;" +
            "uniform mat4 uMVP;" +
            "void main() {" +
            "  vTexCoord = aTexCoord;" +
            "  gl_Position = uMVP * aPosition;" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "varying vec2 vTexCoord;" +
            "uniform sampler2D uTexture;" +
            "void main() {" +
            "  gl_FragColor = texture2D(uTexture, vTexCoord);" +
            "}";

    private int program;
    private int textureId = -1;
    private FloatBuffer vertexBuffer;
    private FloatBuffer uvBuffer;
    private float[] mvp = new float[16];

    // glyph atlas helper
    private GlyphAtlas glyphAtlas;

    // Terminal access
    private TerminalRenderer terminalRenderer; // original renderer to reuse settings if needed
    private TerminalBuffer terminalBuffer;

    public GLTerminalRenderer(Context ctx) {
        context = ctx;
        glyphAtlas = null;
    }

    public void setTerminalRenderer(TerminalRenderer r) {
        this.terminalRenderer = r;
    }
    public void setTerminalBuffer(Object b) {
        if (b instanceof TerminalBuffer) this.terminalBuffer = (TerminalBuffer)b;
    }

    @Override
    public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 glUnused, javax.microedition.khronos.egl.EGLConfig config) {
        GLES20.glClearColor(0f,0f,0f,1f);
        program = GLUtil.createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        Matrix.setIdentityM(mvp, 0);
    }

    @Override
    public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 glUnused, int width, int height) {
        GLES20.glViewport(0,0,width,height);
        // orthographic projection mapping screen pixels to GL clip space
        Matrix.orthoM(mvp, 0, 0, width, 0, height, -1, 1);
    }

    @Override
    public void onDrawFrame(javax.microedition.khronos.opengles.GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (terminalBuffer == null) return;

        // lazy create glyph atlas using paint (CPU)
        if (glyphAtlas == null) {
            glyphAtlas = new GlyphAtlas(16, 16); // font size 16
            textureId = glyphAtlas.uploadToTexture();
        }

        // prepare arrays for quads based on buffer contents
        int cols = terminalBuffer.getColumns();
        int rows = terminalBuffer.getRows();

        // prepare dynamic buffers (4 vertices per char -> large but simple)
        int totalChars = cols * rows;
        
        // BATCHED RENDER: build triangle list (6 vertices per char) and render in a single draw call
        int cols = terminalBuffer.getColumns();
        int rows = terminalBuffer.getRows();
        int totalChars = cols * rows;
        float cellW = glyphAtlas.getCharWidth();
        float cellH = glyphAtlas.getCharHeight();

        float[] vertices = new float[totalChars * 6 * 3];
        float[] uvs = new float[totalChars * 6 * 2];

        int vi = 0, ui = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = terminalBuffer.getChar(c, r);
                if (ch == 0) ch = ' ';
                GlyphAtlas.GlyphInfo gi = glyphAtlas.getGlyph(ch);
                float x = c * cellW;
                float y = (rows - 1 - r) * cellH;

                float x0 = x, x1 = x + cellW, y0 = y, y1 = y + cellH;

                // tri 1
                vertices[vi++] = x0; vertices[vi++] = y1; vertices[vi++] = 0f;
                vertices[vi++] = x0; vertices[vi++] = y0; vertices[vi++] = 0f;
                vertices[vi++] = x1; vertices[vi++] = y1; vertices[vi++] = 0f;
                // tri 2
                vertices[vi++] = x0; vertices[vi++] = y0; vertices[vi++] = 0f;
                vertices[vi++] = x1; vertices[vi++] = y0; vertices[vi++] = 0f;
                vertices[vi++] = x1; vertices[vi++] = y1; vertices[vi++] = 0f;

                float u0 = gi.u0, v0 = gi.v0, u1 = gi.u1, v1 = gi.v1;
                // tri1 uvs
                uvs[ui++] = u0; uvs[ui++] = v0;
                uvs[ui++] = u0; uvs[ui++] = v1;
                uvs[ui++] = u1; uvs[ui++] = v0;
                // tri2 uvs
                uvs[ui++] = u0; uvs[ui++] = v1;
                uvs[ui++] = u1; uvs[ui++] = v1;
                uvs[ui++] = u1; uvs[ui++] = v0;
            }
        }

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);
        uvBuffer = ByteBuffer.allocateDirect(uvs.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        uvBuffer.put(uvs).position(0);

        GLES20.glUseProgram(program);
        int posLoc = GLES20.glGetAttribLocation(program, "aPosition");
        int texLoc = GLES20.glGetAttribLocation(program, "aTexCoord");
        int mvpLoc = GLES20.glGetUniformLocation(program, "uMVP");
        int texSampler = GLES20.glGetUniformLocation(program, "uTexture");

        GLES20.glEnableVertexAttribArray(posLoc);
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, uvBuffer);

        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(texSampler, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, totalChars * 6);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);


        // cleanup
        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }
}
