package com.seedlessds.app.emu

import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder




internal class DirectBlit {
    private var prog = 0
    private var uTex = 0
    private var uAlpha = 0
    private var progSolid = 0
    private var uColor = 0
    private var vbo = 0
    private val aPos = 14
    private val aTex = 15
    private val quadPos = floatBuffer(-1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)
    private val quadTex = floatBuffer(0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
    private val quadTexFbo = floatBuffer(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f)

    private fun floatBuffer(vararg v: Float) =
        ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(v); position(0) }


    fun forget() { prog = 0; progSolid = 0; vbo = 0 }


    fun draw(tex: Int, r: FloatArray, surfaceH: Int, flipY: Boolean = true, alpha: Float = 1f) {
        if (prog == 0) build()
        val i1 = IntArray(1); val vp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, i1, 0); val progPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, i1, 0); val unitPrev = i1[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, i1, 0); val texPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, i1, 0); val vboPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        val blendPrev = GLES20.glIsEnabled(GLES20.GL_BLEND)

        val vw = r[2].toInt().coerceAtLeast(1)
        val vh = r[3].toInt().coerceAtLeast(1)
        val vy = (surfaceH - (r[1].toInt() + vh)).coerceAtLeast(0)
        GLES20.glViewport(r[0].toInt(), vy, vw, vh)
        GLES20.glDisable(GLES20.GL_BLEND)
        if (alpha < 1f) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }
        GLES20.glUseProgram(prog)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform1f(uAlpha, alpha)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, if (flipY) 48 else 96)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboPrev)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPrev)
        GLES20.glActiveTexture(unitPrev)
        GLES20.glUseProgram(progPrev)
        if (blendPrev) GLES20.glEnable(GLES20.GL_BLEND) else GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
    }

    fun drawBorder(r: FloatArray, surfaceW: Int, surfaceH: Int, thickness: Float, color: FloatArray) {
        if (r[2] <= 0f || r[3] <= 0f || surfaceW <= 0 || surfaceH <= 0) return
        if (progSolid == 0) buildSolid()
        val i1 = IntArray(1); val vp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, i1, 0); val progPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, i1, 0); val unitPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, i1, 0); val vboPrev = i1[0]
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        val blendPrev = GLES20.glIsEnabled(GLES20.GL_BLEND)

        GLES20.glViewport(0, 0, surfaceW, surfaceH)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(progSolid)
        GLES20.glUniform4f(uColor, color[0], color[1], color[2], color[3])

        val x0 = r[0] / surfaceW * 2f - 1f
        val x1 = (r[0] + r[2]) / surfaceW * 2f - 1f
        val yt = 1f - r[1] / surfaceH * 2f
        val yb = 1f - (r[1] + r[3]) / surfaceH * 2f
        val tx = (thickness / surfaceW * 2f).coerceAtLeast(2f / surfaceW)
        val ty = (thickness / surfaceH * 2f).coerceAtLeast(2f / surfaceH)
        val q = floatArrayOf(
            x0, yt, x0, yt - ty, x1, yt - ty, x0, yt, x1, yt - ty, x1, yt,
            x0, yb + ty, x0, yb, x1, yb, x0, yb + ty, x1, yb, x1, yb + ty,
            x0, yt, x0, yb, x0 + tx, yb, x0, yt, x0 + tx, yb, x0 + tx, yt,
            x1 - tx, yt, x1 - tx, yb, x1, yb, x1 - tx, yt, x1, yb, x1, yt,
        )
        val b = ByteBuffer.allocateDirect(q.size * 4).order(ByteOrder.nativeOrder())
        b.asFloatBuffer().put(q).flip()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, b)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 24)
        GLES20.glDisableVertexAttribArray(aPos)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboPrev)
        GLES20.glActiveTexture(unitPrev)
        GLES20.glUseProgram(progPrev)
        if (blendPrev) GLES20.glEnable(GLES20.GL_BLEND) else GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glViewport(vp[0], vp[1], vp[2], vp[3])
    }

    private fun build() {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src); GLES20.glCompileShader(id)
            return id
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex;" +
                "void main(){ vTex = aTex; gl_Position = vec4(aPos, 0.0, 1.0); }")
        val fs = compile(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; uniform float uAlpha;" +
                "void main(){ vec4 c = texture2D(uTex, vTex); gl_FragColor = vec4(c.rgb, c.a * uAlpha); }")
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glBindAttribLocation(p, aPos, "aPos")
        GLES20.glBindAttribLocation(p, aTex, "aTex")
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) com.seedlessds.app.AppLog.e("reconDS", "BLIT: program does not link: " + GLES20.glGetProgramInfoLog(p))
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        prog = p
        uTex = GLES20.glGetUniformLocation(p, "uTex")
        uAlpha = GLES20.glGetUniformLocation(p, "uAlpha")
        val prev = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, prev, 0)
        val b = IntArray(1); GLES20.glGenBuffers(1, b, 0); vbo = b[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val data = ByteBuffer.allocateDirect(144).order(ByteOrder.nativeOrder())
        data.asFloatBuffer().apply { put(quadPos); put(quadTex); put(quadTexFbo); quadPos.position(0); quadTex.position(0); quadTexFbo.position(0) }
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 144, data, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, prev[0])
    }

    private fun buildSolid() {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src); GLES20.glCompileShader(id)
            return id
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 aPos; void main(){ gl_Position = vec4(aPos, 0.0, 1.0); }")
        val fs = compile(GLES20.GL_FRAGMENT_SHADER,
            "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor = uColor; }")
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs)
        GLES20.glBindAttribLocation(p, aPos, "aPos")
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) com.seedlessds.app.AppLog.e("reconDS", "BLIT: solid program does not link: " + GLES20.glGetProgramInfoLog(p))
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        progSolid = p
        uColor = GLES20.glGetUniformLocation(p, "uColor")
    }
}
