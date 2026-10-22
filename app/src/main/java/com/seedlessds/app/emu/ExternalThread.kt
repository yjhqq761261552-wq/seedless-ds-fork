package com.seedlessds.app.emu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.seedlessds.app.AppLog
import com.seedlessds.app.filesystem.PathCache
import com.seedlessds.core.SeedlessCore
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ExternalThread(private val dpy: EGLDisplay, private val shared: EGLContext) : Thread("DsExternal") {
    private val lock = Object()
    private var pending = false
    private var producerFence = 0L
    private var consumerFence = 0L
    private var topTex = 0
    private var bottomTex = 0
    private var srcW = 256
    private var srcH = 192
    private var busyTop = 0
    private var busyBottom = 0
    @Volatile private var quit = false
    private lateinit var cfg: EGLConfig
    private var ctx: EGLContext? = null
    private var parking: EGLSurface? = null
    private var surface: EGLSurface? = null
    private var surfaceGen = -1
    private var surfaceW = 0
    private var surfaceH = 0
    private var extVbo = 0
    private var chainFilter: String? = null
    private var chainSw = 0
    private var chainSh = 0
    private var chainW = 0
    private var chainH = 0
    private var chainRect = FloatArray(4)
    private val uv = floatArrayOf(
        0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f,
        0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f,
    )
    private val full = floatArrayOf(-1f, 1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)

    fun publish(fence: Long, top: Int, bottom: Int, sw: Int, sh: Int) = synchronized(lock) {
        if (quit) { SeedlessCore.gpuFenceDestroy(fence); return@synchronized }
        SeedlessCore.gpuFenceDestroy(producerFence)
        pending = true; producerFence = fence
        topTex = top; bottomTex = bottom; srcW = sw; srcH = sh
        lock.notifyAll()
    }

    fun terminate() {
        synchronized(lock) { quit = true; lock.notifyAll() }
        // The parent EGL display must outlive the external context.
        if (currentThread() !== this) join()
    }

    /** A queued obsolete frame is dropped before its textures are overwritten.
     * Display VSYNC never owns the texture lease. */
    fun acquire(top: Int, bottom: Int): Long = synchronized(lock) {
        while (busyTop == top || busyBottom == bottom) lock.wait()
        if (pending && (topTex == top || bottomTex == bottom)) {
            pending = false
            SeedlessCore.gpuFenceDestroy(producerFence); producerFence = 0L
        }
        val fence = consumerFence; consumerFence = 0L
        fence
    }

    override fun run() {
        try {
            initialize()
            while (true) {
                val frame: Frame
                synchronized(lock) {
                    while (!pending && !quit) lock.wait()
                    if (quit) return
                    frame = Frame(producerFence, topTex, bottomTex, srcW, srcH)
                    producerFence = 0L; pending = false
                    busyTop = frame.top; busyBottom = frame.bottom
                }
                var ownsProducerFence = true
                var releasedLease = false
                try {
                    val snap = ExternalSurface.snapshot()
                    if (snap == null || !ensureSurface(snap)) { releaseSurface(); continue }
                    ownsProducerFence = false
                    check(SeedlessCore.gpuFenceWait(frame.fence)) { "Producer fence wait failed" }
                    draw(snap, frame)
                    val done = SeedlessCore.gpuFenceCreate()
                    synchronized(lock) {
                        SeedlessCore.gpuFenceDestroy(consumerFence)
                        consumerFence = done
                        busyTop = 0; busyBottom = 0; releasedLease = true
                        lock.notifyAll()
                    }
                    // Main-screen uploads may resume after the sampling fence,
                    // even while this display is blocked on its refresh cycle.
                    if (!EGL14.eglSwapBuffers(dpy, surface)) {
                        AppLog.w("reconDS", "External swap failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                        releaseSurface()
                    }
                } finally {
                    if (ownsProducerFence) SeedlessCore.gpuFenceDestroy(frame.fence)
                    if (!releasedLease) GLES20.glFinish()
                    synchronized(lock) { busyTop = 0; busyBottom = 0; lock.notifyAll() }
                }
            }
        } catch (e: Exception) {
            ExternalSurface.attached = false
            AppLog.e("reconDS", "External renderer stopped: $e")
        } finally {
            synchronized(lock) {
                quit = true; pending = false
                SeedlessCore.gpuFenceDestroy(producerFence); producerFence = 0L
                lock.notifyAll()
            }
            release()
        }
    }

    private data class Frame(val fence: Long, val top: Int, val bottom: Int, val sw: Int, val sh: Int)

    private fun initialize() {
        val out = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(dpy, intArrayOf(
            EGL14.EGL_RED_SIZE, if (RendererPolicy.usesVulkan(SettingsRepo.current)) 8 else 5,
            EGL14.EGL_GREEN_SIZE, if (RendererPolicy.usesVulkan(SettingsRepo.current)) 8 else 6,
            EGL14.EGL_BLUE_SIZE, if (RendererPolicy.usesVulkan(SettingsRepo.current)) 8 else 5,
            EGL14.EGL_ALPHA_SIZE, if (RendererPolicy.usesVulkan(SettingsRepo.current)) 8 else 0,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE), 0, out, 0, 1, count, 0) && count[0] > 0)
        cfg = out[0]!!
        ctx = EGL14.eglCreateContext(dpy, cfg, shared, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        check(ctx != null && ctx != EGL14.EGL_NO_CONTEXT)
        parking = EGL14.eglCreatePbufferSurface(dpy, cfg, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(parking != null && parking != EGL14.EGL_NO_SURFACE)
        check(EGL14.eglMakeCurrent(dpy, parking, parking, ctx))
    }

    private fun ensureSurface(snap: ExternalSurface.Snap): Boolean {
        if (!snap.surface.isValid) return false
        if (surface != null && surfaceGen == snap.generation && surfaceW == snap.width && surfaceH == snap.height) return true
        releaseSurface()
        // Surface destruction may race with this snapshot after the UI callback.
        val next = try {
            EGL14.eglCreateWindowSurface(dpy, cfg, snap.surface, intArrayOf(EGL14.EGL_NONE), 0)
        } catch (_: IllegalArgumentException) { return false }
        if (next == null || next == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(dpy, next, next, ctx)) { EGL14.eglDestroySurface(dpy, next); return false }
        surface = next; surfaceGen = snap.generation; surfaceW = snap.width; surfaceH = snap.height
        chainW = 0
        return true
    }

    private fun releaseSurface() {
        if (ctx != null && parking != null) EGL14.eglMakeCurrent(dpy, parking, parking, ctx)
        surface?.let { EGL14.eglDestroySurface(dpy, it) }
        surface = null; surfaceGen = -1
    }

    private fun release() {
        releaseSurface()
        if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT && parking != null && parking != EGL14.EGL_NO_SURFACE &&
            EGL14.eglMakeCurrent(dpy, parking, parking, ctx)) {
            GLES20.glFinish()
            SeedlessCore.fxRelease(true)
            if (extVbo != 0) GLES20.glDeleteBuffers(1, intArrayOf(extVbo), 0)
        }
        synchronized(lock) { SeedlessCore.gpuFenceDestroy(consumerFence); consumerFence = 0L }
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        parking?.let { if (it != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, it) }; parking = null
        ctx?.let { if (it != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, it) }; ctx = null
        EGL14.eglReleaseThread()
    }

    private fun writeVbo(offset: Int, values: FloatArray) {
        val buffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(values).flip()
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, offset, values.size * 4, buffer)
    }

    private fun prepare(snap: ExternalSurface.Snap, r: FloatArray, sw: Int, sh: Int) {
        if (extVbo == 0) {
            val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); extVbo = ids[0]
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 192, null, GLES20.GL_DYNAMIC_DRAW)
            writeVbo(48, full); writeVbo(96, uv)
        } else GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, extVbo)
        val filter = SettingsRepo.current.filter
        if (filter == chainFilter && sw == chainSw && sh == chainSh && snap.width == chainW && snap.height == chainH && r.contentEquals(chainRect)) return
        fun x(px: Float) = px / snap.width * 2f - 1f
        fun y(px: Float) = 1f - px / snap.height * 2f
        val left = x(r[0]); val right = x(r[0] + r[2]); val top = y(r[1]); val bottom = y(r[1] + r[3])
        writeVbo(0, floatArrayOf(left, top, left, bottom, right, bottom, left, top, right, bottom, right, top))
        if (filter != chainFilter) {
            val result = SeedlessCore.extfxLoad(PathCache.SYS_PREFIX + "shaders/$filter.dfx", 0, 96)
            if (result != 0) AppLog.w("reconDS", "External shader $filter failed ($result), using fallback")
            chainFilter = filter
        }
        SeedlessCore.extfxSetup(sw, sh, 0, 0, snap.width, snap.height)
        chainSw = sw; chainSh = sh; chainW = snap.width; chainH = snap.height; chainRect = r.copyOf()
    }

    private fun draw(snap: ExternalSurface.Snap, frame: Frame) {
        val r = ExternalLayout.rect(snap.width, snap.height)
        prepare(snap, r, frame.sw, frame.sh)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, snap.width, snap.height)
        GLES20.glClearColor(0f, 0f, 0f, 1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val settings = SettingsRepo.current
        val screen = ScreenGeometry.externalScreen(settings.extDisplayScreen, settings.swapScreens)
        val texture = if (screen == 1) frame.bottom else frame.top
        if (texture != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            SeedlessCore.extfxRender(texture, screen, 0, 6, r[2].toInt().coerceAtLeast(1), r[3].toInt().coerceAtLeast(1))
        }
    }
}
