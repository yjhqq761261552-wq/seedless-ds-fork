#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>

/* Use native KHR sync on API 21+, without referencing Java EGL15 (API 29). */
JNIEXPORT jlong JNICALL
Java_com_seedlessds_core_SeedlessCore_gpuFenceCreate(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    EGLDisplay dpy = eglGetCurrentDisplay();
    const char *extensions = eglQueryString(dpy, EGL_EXTENSIONS);
    PFNEGLCREATESYNCKHRPROC create = (PFNEGLCREATESYNCKHRPROC)eglGetProcAddress("eglCreateSyncKHR");
    PFNEGLCLIENTWAITSYNCKHRPROC wait = (PFNEGLCLIENTWAITSYNCKHRPROC)eglGetProcAddress("eglClientWaitSyncKHR");
    PFNEGLDESTROYSYNCKHRPROC destroy = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
    if (extensions && strstr(extensions, "EGL_KHR_fence_sync") && create && wait && destroy) {
        const EGLint attribs[] = { EGL_NONE };
        EGLSyncKHR sync = create(dpy, EGL_SYNC_FENCE_KHR, attribs);
        if (sync != EGL_NO_SYNC_KHR) {
            glFlush();
            return (jlong)(intptr_t)sync;
        }
    }
    /* Unsupported fences still require producer completion before sharing textures. */
    glFinish();
    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_gpuFenceWait(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env; (void)cls;
    if (!handle) return JNI_TRUE;
    EGLDisplay dpy = eglGetCurrentDisplay();
    EGLSyncKHR sync = (EGLSyncKHR)(intptr_t)handle;
    PFNEGLCLIENTWAITSYNCKHRPROC wait = (PFNEGLCLIENTWAITSYNCKHRPROC)eglGetProcAddress("eglClientWaitSyncKHR");
    PFNEGLDESTROYSYNCKHRPROC destroy = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
    EGLint result = wait ? wait(dpy, sync, 0, EGL_FOREVER_KHR) : EGL_FALSE;
    if (destroy) destroy(dpy, sync);
    return result == EGL_CONDITION_SATISFIED_KHR ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_gpuFenceDestroy(JNIEnv *env, jclass cls, jlong handle)
{
    (void)env; (void)cls;
    if (!handle) return;
    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    PFNEGLDESTROYSYNCKHRPROC destroy = (PFNEGLDESTROYSYNCKHRPROC)eglGetProcAddress("eglDestroySyncKHR");
    if (destroy) destroy(dpy, (EGLSyncKHR)(intptr_t)handle);
}
