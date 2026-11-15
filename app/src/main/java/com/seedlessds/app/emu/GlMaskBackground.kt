package com.seedlessds.app.emu

import android.opengl.GLES20
import android.opengl.GLUtils

/** Draws the current mask background under the game screens on an OpenGL
 *  surface, reusing [DirectBlit] (which saves/restores GL state). The mask is
 *  the base layer: drawn right after the framebuffer clear, before the
 *  screens. Render-thread confined; one instance per GL context. */
internal class GlMaskBackground {
    private val blit = DirectBlit()
    private var tex = 0
    private var loaded: ScreenMasks.Background? = null

    fun forget() {
        tex = 0
        loaded = null
        blit.forget()
    }

    fun draw(bg: ScreenMasks.Background?, surfaceH: Int) {
        if (bg == null) {
            loaded = null
            return
        }
        if (tex != 0 && !GLES20.glIsTexture(tex)) {
            tex = 0
            loaded = null
        }
        if (tex == 0) {
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            tex = t[0]
        }
        if (bg !== loaded) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bg.bitmap, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            loaded = bg
        }
        // GLUtils uploads bitmaps with the top row at v=0, so the mask needs
        // DirectBlit's flipY=true coordinate set (v=0 at the rect's top
        // edge). The flipY=false set is for FBO textures, whose row 0 is the
        // image bottom; using it here drew the mask upside down (user report
        // on 1.0.28).
        blit.draw(tex, bg.rect, surfaceH, flipY = true, alpha = 1f)
    }
}
