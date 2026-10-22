#include "shared_frames.h"
#include <atomic>
#include <cassert>
#include <cstdio>
#include <cstring>
#include <thread>

static std::atomic<uint64_t> serial{1};
static unsigned copies;
extern "C" uint64_t nds_video_latest_serial(void) { return serial.load(); }
extern "C" void nds_video_wake(void) {}
extern "C" int nds_video_copy_latest(void *out,size_t capacity,uint64_t,nds_video_snapshot_t *info) {
    assert(capacity>=1024);
    uint64_t frame=serial.load();
    std::memset(out,(int)(frame&255u),1024);
    *info={frame,{16,16},{8,8},{0,512},1024};
    ++copies;
    return 1;
}
int main() {
    seedless_vk::SharedFrames pool;
    assert(!pool.active());
    pool.attach(); assert(pool.active());
    auto first=pool.latest(false,1024);
    auto external=pool.latest(true,1024);
    assert(first==external && copies==1);
    assert(pool.latest(false,1024)==first && copies==1);
    serial=2;
    auto second=pool.latest(false,1024);
    assert(second!=first && second->info.serial==2 && copies==2);
    assert(first->info.serial==1 && first->rgba[0]==1);
    serial=3;
    // Both slots are leased: skip publication instead of mutating a reader or allocating a queue.
    auto repeated=pool.latest(false,1024);
    assert(repeated==second && copies==2);
    first.reset(); external.reset();
    auto third=pool.latest(false,1024);
    assert(third->info.serial==3 && third->rgba[0]==3 && second->rgba[0]==2);
    repeated.reset(); second.reset(); third.reset();
    std::atomic<bool> stop{false};
    std::thread reader([&] {
        while (!stop.load()) {
            auto frame=pool.latest(true,1024);
            if (frame) for (size_t i=0;i<1024;++i) assert(frame->rgba[i]==(frame->info.serial&255u));
        }
    });
    for (unsigned i=4;i<1004;++i) { serial=i; pool.latest(false,1024); }
    stop=true; reader.join();
    pool.detach(); assert(!pool.active() && pool.serial()==0);
    std::puts("{\"sharedFrameOwnership\":\"passed\",\"packOncePerSerial\":\"passed\",\"boundedTwoSlotPool\":\"passed\",\"concurrentPublicationAttempts\":1000,\"scope\":\"Production packet pool; fixture core snapshots; Android CPU executable, no Vulkan driver\"}");
}
