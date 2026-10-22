
#include <jni.h>
#include "seedlessds/nds.h"
#include "seedlessds/rewind.h"
#include "seedlessds/state_request.h"

#define SCRIPT_AXES 4

nds_t *seedless_core(void);

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSave(JNIEnv *env, jclass cls, jint slot,
                                                jboolean withScreens)
{
    (void)env;
    (void)cls;
    (void)withScreens;
    return nds_state_save(seedless_core(), (int)slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateLoad(JNIEnv *env, jclass cls, jint slot)
{
    (void)env;
    (void)cls;
    return nds_state_load(seedless_core(), (int)slot) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSaving(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_state_saving(seedless_core()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_stateSlot(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_state_slot(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_autosaveInterval(JNIEnv *env, jclass cls,
                                                       jint seconds)
{
    (void)env;
    (void)cls;
    nds_autosave_interval(seedless_core(), (unsigned)seconds);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_accelerometer(JNIEnv *env, jclass cls, jfloat x,
                                                    jfloat y, jfloat z)
{
    (void)env;
    (void)cls;
    nds_accel(seedless_core(), (float)x, (float)y, (float)z);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_gyroscope(JNIEnv *env, jclass cls, jfloat rotation)
{
    (void)env;
    (void)cls;
    nds_gyro(seedless_core(), (float)rotation, 0.0f, 0.0f);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_rumble(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_rumble_state(seedless_core()) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_whitenoise(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env;
    (void)cls;
    nds_whitenoise(seedless_core(), on != JNI_FALSE);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_hinge(JNIEnv *env, jclass cls, jboolean closed)
{
    (void)env;
    (void)cls;
    nds_hinge(seedless_core(), closed != JNI_FALSE);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptActive(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return nds_script_active(seedless_core()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptOverrides(JNIEnv *env, jclass cls)
{
    (void)env;
    (void)cls;
    return (jint)nds_script_overrides(seedless_core());
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptAxes(JNIEnv *env, jclass cls, jfloat leftX,
                                                 jfloat leftY, jfloat rightX,
                                                 jfloat rightY)
{
    float axes[SCRIPT_AXES];

    (void)env;
    (void)cls;
    axes[0] = (float)leftX;
    axes[1] = (float)leftY;
    axes[2] = (float)rightX;
    axes[3] = (float)rightY;
    nds_script_axis(seedless_core(), axes, SCRIPT_AXES);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_scriptRotation(JNIEnv *env, jclass cls,
                                                     jint rotation)
{
    (void)env;
    (void)cls;
    nds_script_rotation(seedless_core(), (float)rotation);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_rewindConfigure(JNIEnv *env, jclass cls, jboolean enabled, jint seconds)
{
    (void)env; (void)cls;
    nds_rewind_configure(enabled != JNI_FALSE, seconds < 0 ? 10u : (unsigned)seconds);
}
JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_rewindHold(JNIEnv *env, jclass cls, jboolean pressed)
{
    (void)env; (void)cls;
    return nds_rewind_hold(pressed != JNI_FALSE);
}
JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_rewindCancel(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    nds_rewind_cancel();
}
JNIEXPORT jlong JNICALL
Java_com_seedlessds_core_SeedlessCore_rewindStatus(JNIEnv *env, jclass cls)
{
    (void)env; (void)cls;
    return (jlong)nds_rewind_status();
}

JNIEXPORT jlong JNICALL
Java_com_seedlessds_core_SeedlessCore_stateRequest(JNIEnv *env, jclass cls, jint slot, jboolean load) {
    (void)env; (void)cls;
    return (jlong)nds_state_request((unsigned)slot, load != JNI_FALSE);
}
JNIEXPORT jint JNICALL
Java_com_seedlessds_core_SeedlessCore_stateRequestStatus(JNIEnv *env, jclass cls, jlong ticket) {
    (void)env; (void)cls;
    return nds_state_request_status((uint64_t)ticket);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_core_SeedlessCore_stateHold(JNIEnv *env, jclass cls, jboolean hold) {
    (void)env; (void)cls; nds_state_hold(hold != JNI_FALSE);
}
JNIEXPORT jboolean JNICALL
Java_com_seedlessds_core_SeedlessCore_stateHeld(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; return nds_state_held() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_seedlessds_core_SeedlessCore_rewindInterval(JNIEnv *env, jclass cls, jint seconds) {
    (void)env; (void)cls; nds_rewind_interval((unsigned)seconds);
}

JNIEXPORT void JNICALL Java_com_seedlessds_core_SeedlessCore_bootStateBegin(JNIEnv *env,jclass cls,jint slot) {
    (void)env; (void)cls; nds_boot_state_begin(slot);
}
JNIEXPORT jint JNICALL Java_com_seedlessds_core_SeedlessCore_bootStateStatus(JNIEnv *env,jclass cls) {
    (void)env; (void)cls; return nds_boot_state_status();
}
