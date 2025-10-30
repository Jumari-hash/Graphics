
package com.termux.view;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

/**
 * GLTerminalView now extends TerminalView so it can be used in place of TerminalView throughout the app.
 * It internally manages a GLSurfaceView overlay that can be toggled on/off to enable GPU rendering.
 */
public class GLTerminalView extends TerminalView {

    private GLSurfaceView glSurfaceView;
    private GLTerminalRenderer glRenderer;
    private boolean gpuMode = false;

    public GLTerminalView(Context context) {
        this(context, null);
    }

    public GLTerminalView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // create GLSurfaceView and attach renderer
        glSurfaceView = new GLSurfaceView(context);
        glSurfaceView.setEGLContextClientVersion(2);
        glRenderer = new GLTerminalRenderer(context);
        glSurfaceView.setRenderer(glRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // add GL surface as sibling in parent when attached
        // We'll manage visibility on attach/detach.
        post(() -> {
            if (getParent() instanceof FrameLayout) {
                FrameLayout parent = (FrameLayout)getParent();
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                parent.addView(glSurfaceView, lp);
                glSurfaceView.setVisibility(View.GONE);
            } else if (getParent() instanceof RelativeLayout) {
                RelativeLayout parent = (RelativeLayout)getParent();
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                parent.addView(glSurfaceView, lp);
                glSurfaceView.setVisibility(View.GONE);
            } else {
                // fallback: add as overlay to this view
                addView(glSurfaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                glSurfaceView.setVisibility(View.GONE);
            }
        });
    }

    public void setGpuMode(boolean enable) {
        gpuMode = enable;
        if (glSurfaceView == null) return;
        if (gpuMode) {
            // copy renderer & buffer
            glRenderer.setTerminalRenderer(this.mRenderer);
            glRenderer.setTerminalBuffer(this.mBuffer);
            glSurfaceView.setVisibility(View.VISIBLE);
            this.setVisibility(View.GONE);
            glSurfaceView.requestRender();
        } else {
            glSurfaceView.setVisibility(View.GONE);
            this.setVisibility(View.VISIBLE);
        }
    }

    public boolean isGpuMode() { return gpuMode; }

    // Ensure GL renderer gets updated when terminal updates; call requestRender if GPU mode is active
    @Override
    public void invalidate() {
        super.invalidate();
        if (gpuMode && glSurfaceView != null) {
            glSurfaceView.requestRender();
        }
    }
}
