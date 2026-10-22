#ifndef SEEDLESS_FRAME_OBSERVER_H
#define SEEDLESS_FRAME_OBSERVER_H
#ifdef __cplusplus
extern "C" {
#endif
/* The callback runs on the emulation thread, once per emulated frame, before
 * pause/load/rewind or input sampling. It must never wait for the UI thread. */
void nds_frame_observer_set(void (*callback)(void));
void nds_frame_observer_emit(void);
#ifdef __cplusplus
}
#endif
#endif
