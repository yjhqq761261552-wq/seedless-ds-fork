#include "low_latency_audio.h"
#include <assert.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
static int fallbacks;
void audio_out_restart_playback(void) { ++fallbacks; }
int main(int argc,char **argv) {
    assert(argc==2);
    void *driver=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL); assert(driver);
    int (*starts)(void)=dlsym(driver,"fixture_starts");
    int (*closes)(void)=dlsym(driver,"fixture_closes");
    void (*fail_open)(int)=dlsym(driver,"fixture_fail_open");
    void (*render)(int16_t*,unsigned)=dlsym(driver,"fixture_audio");
    void (*disconnect)(void)=dlsym(driver,"fixture_disconnect");
    assert(starts && closes && fail_open && render && disconnect);
    low_latency_audio_configure(1); low_latency_audio_volume(50);
    assert(low_latency_audio_start(0));
    assert(starts()==0); // Never run an empty callback loop during ROM boot.
    low_latency_audio_resume(); assert(starts()==0);
    int16_t block[1470],out[1024];
    for(unsigned i=0;i<1470;i++)block[i]=i&1?-12000:12000;
    assert(low_latency_audio_feed(block,1470,0)==1 && starts()==0);
    assert(low_latency_audio_feed(block,1470,0)==1 && starts()==1);
    low_latency_audio_resume(); assert(starts()==1); // Core reset must not restart an active stream.
    render(out,512);
    for(unsigned i=0;i<1024;i++)assert(out[i]==(i&1?-6000:6000));
    uint64_t stats[6];low_latency_audio_stats(stats);
    assert(stats[0]==48000 && stats[1]==768 && stats[2]==0 && stats[5]==1);
    low_latency_audio_sound(0);
    assert(low_latency_audio_feed(block,1470,0)==1 && starts()==1);
    low_latency_audio_sound(1);
    low_latency_audio_pause();low_latency_audio_resume();
    assert(low_latency_audio_feed(block,1470,0)==1 && starts()==1);
    assert(low_latency_audio_feed(block,1470,0)==1 && starts()==2);
    disconnect();assert(low_latency_audio_feed(block,1470,0)==0);
    assert(fallbacks==1 && closes()==1 && !low_latency_audio_active());
    fail_open(1);assert(!low_latency_audio_start(0));
    fail_open(0);low_latency_audio_configure(0);assert(!low_latency_audio_start(0));
    puts("{\"audioPriming\":\"passed\",\"duplicateResume\":\"passed\",\"gainAndNativeRateConversion\":\"passed\",\"pauseResume\":\"passed\",\"disconnectFallback\":\"passed\",\"openFailureFallback\":\"passed\",\"scope\":\"Production audio adapter with fake AAudio driver in an isolated Android process; no physical sound measurement\"}");
    dlclose(driver);return 0;
}
