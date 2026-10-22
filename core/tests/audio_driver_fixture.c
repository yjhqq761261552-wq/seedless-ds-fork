/* AAudio fault adapter for a standalone test process, never used by the app. */
#include <aaudio/AAudio.h>
#include <string.h>
struct AAudioStreamBuilderStruct { int unused; };
struct AAudioStreamStruct { int unused; };
static struct AAudioStreamBuilderStruct builder;
static struct AAudioStreamStruct stream;
static AAudioStream_dataCallback data_callback;
static AAudioStream_errorCallback error_callback;
static void *data_user, *error_user;
static int starts, closes, fail_open, buffer_size = 768;
static aaudio_stream_state_t state = AAUDIO_STREAM_STATE_OPEN;
aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder **out) { *out=&builder; return AAUDIO_OK; }
aaudio_result_t AAudioStreamBuilder_delete(AAudioStreamBuilder *b) { (void)b; return AAUDIO_OK; }
void AAudioStreamBuilder_setDirection(AAudioStreamBuilder *b,aaudio_direction_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder *b,int32_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setFormat(AAudioStreamBuilder *b,aaudio_format_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder *b,aaudio_performance_mode_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder *b,aaudio_sharing_mode_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setUsage(AAudioStreamBuilder *b,aaudio_usage_t v) { (void)b;(void)v; }
void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder *b,AAudioStream_dataCallback callback,void *user) { (void)b;data_callback=callback;data_user=user; }
void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder *b,AAudioStream_errorCallback callback,void *user) { (void)b;error_callback=callback;error_user=user; }
aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder *b,AAudioStream **out) { (void)b; if(fail_open)return AAUDIO_ERROR_UNAVAILABLE;*out=&stream;state=AAUDIO_STREAM_STATE_OPEN;return AAUDIO_OK; }
aaudio_result_t AAudioStream_requestStart(AAudioStream *s) { (void)s; if(state==AAUDIO_STREAM_STATE_STARTED)return AAUDIO_ERROR_INVALID_STATE;state=AAUDIO_STREAM_STATE_STARTED;++starts;return AAUDIO_OK; }
aaudio_result_t AAudioStream_requestPause(AAudioStream *s) { (void)s;state=AAUDIO_STREAM_STATE_PAUSED;return AAUDIO_OK; }
aaudio_result_t AAudioStream_close(AAudioStream *s) { (void)s;state=AAUDIO_STREAM_STATE_CLOSED;++closes;return AAUDIO_OK; }
int32_t AAudioStream_getSampleRate(AAudioStream *s) { (void)s;return 48000; }
int32_t AAudioStream_getChannelCount(AAudioStream *s) { (void)s;return 2; }
aaudio_format_t AAudioStream_getFormat(AAudioStream *s) { (void)s;return AAUDIO_FORMAT_PCM_I16; }
int32_t AAudioStream_getFramesPerBurst(AAudioStream *s) { (void)s;return 192; }
aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream *s,int32_t frames) { (void)s;buffer_size=frames;return frames; }
int32_t AAudioStream_getBufferSizeInFrames(AAudioStream *s) { (void)s;return buffer_size; }
int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream *s) { (void)s;return 4096; }
int32_t AAudioStream_getXRunCount(AAudioStream *s) { (void)s;return 0; }
aaudio_stream_state_t AAudioStream_getState(AAudioStream *s) { (void)s;return state; }
aaudio_result_t AAudioStream_waitForStateChange(AAudioStream *s,aaudio_stream_state_t old,aaudio_stream_state_t *next,int64_t timeout) { (void)s;(void)old;(void)timeout;*next=state;return AAUDIO_OK; }
int fixture_starts(void) { return starts; }
int fixture_closes(void) { return closes; }
void fixture_fail_open(int fail) { fail_open=fail; }
void fixture_audio(int16_t *out,unsigned frames) { data_callback(&stream,data_user,out,(int32_t)frames); }
void fixture_disconnect(void) { state=AAUDIO_STREAM_STATE_DISCONNECTED;error_callback(&stream,error_user,AAUDIO_ERROR_DISCONNECTED); }
