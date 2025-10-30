package com.termux.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.SparseArray;

/**
 * Simple glyph atlas: renders ASCII glyphs into a single bitmap atlas using Paint,
 * then provides UV coordinates per glyph. Not heavily optimized.
 */
public class GlyphAtlas {

    public static class GlyphInfo {
        public float u0, v0, u1, v1;
        public int width, height;
    }

    private final int fontSize;
    private final int cols = 16;
    private final int rows = 8; // supports 128 chars
    private final int atlasW;
    private final int atlasH;
    private final Bitmap atlasBitmap;
    private final SparseArray<GlyphInfo> map = new SparseArray<>();
    private final int charWidth;
    private final int charHeight;

    public GlyphAtlas(int fontSize, int padding) {
        this.fontSize = fontSize;
        // approximate char size
        charWidth = fontSize + padding;
        charHeight = fontSize + padding;
        atlasW = cols * charWidth;
        atlasH = rows * charHeight;
        atlasBitmap = Bitmap.createBitmap(atlasW, atlasH, Bitmap.Config.ARGB_8888);
        renderAtlas();
    }

    private void renderAtlas() {
        Canvas c = new Canvas(atlasBitmap);
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(fontSize);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(android.graphics.Typeface.MONOSPACE);

        int ch = 32;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                if (ch > 127) break;
                int x = col * charWidth;
                int y = r * charHeight;
                String s = Character.toString((char)ch);
                // measure and draw
                Rect bounds = new Rect();
                p.getTextBounds(s, 0, 1, bounds);
                int drawX = x + 2;
                int drawY = y + (charHeight - bounds.bottom) - 2;
                c.drawText(s, drawX, drawY, p);
                GlyphInfo gi = new GlyphInfo();
                gi.width = charWidth;
                gi.height = charHeight;
                gi.u0 = (float)x / atlasW;
                gi.v0 = (float)(y) / atlasH;
                gi.u1 = (float)(x + charWidth) / atlasW;
                gi.v1 = (float)(y + charHeight) / atlasH;
                map.put(ch, gi);
                ch++;
            }
        }
    }

    public GlyphInfo getGlyph(char c) {
        GlyphInfo gi = map.get((int)c);
        if (gi == null) return map.get((int)' ');
        return gi;
    }

    public int uploadToTexture() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        int id = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlasBitmap, 0);
        return id;
    }

    public int getAtlasWidth() { return atlasW; }
    public int getAtlasHeight() { return atlasH; }
    public int getCharWidth() { return charWidth; }
    public int getCharHeight() { return charHeight; }
}
