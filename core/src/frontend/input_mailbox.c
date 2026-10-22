#include "seedlessds/metrics.h"
#include "input_mailbox.h"
#include <pthread.h>
#include <string.h>

static pthread_mutex_t input_lock = PTHREAD_MUTEX_INITIALIZER;
static nds_input_t latest;
static uint64_t changed_at;
static uint32_t pressed, pressed_mask;
static int pen_pending, pen_x, pen_y;

void input_mailbox_publish(const nds_input_t *input)
{
    if (!input) return;
    pthread_mutex_lock(&input_lock);
    if (!changed_at) changed_at = nds_metrics_now_us();
    uint32_t down = input->keys & ~latest.keys;
    pressed |= down;
    pressed_mask |= down & input->keys_mask;
    if (input->touch_down) {
        if (!latest.touch_down) pen_pending = 1;
        pen_x = input->touch_x;
        pen_y = input->touch_y;
    }
    latest = *input;
    latest.touch_down = input->touch_down != 0;
    pthread_mutex_unlock(&input_lock);
}

nds_input_t input_mailbox_read(void)
{
    pthread_mutex_lock(&input_lock);
    nds_input_t result = latest;
    if (changed_at) {
        nds_metrics_record(NDS_METRIC_INPUT, nds_metrics_now_us() - changed_at);
        changed_at = 0;
    }
    result.keys |= pressed;
    result.keys_mask |= pressed_mask;
    if (pen_pending && !result.touch_down) {
        result.touch_down = 1;
        result.touch_x = pen_x;
        result.touch_y = pen_y;
    }
    pressed = pressed_mask = 0;
    pen_pending = 0;
    pthread_mutex_unlock(&input_lock);
    return result;
}

void input_mailbox_reset(void)
{
    pthread_mutex_lock(&input_lock);
    memset(&latest, 0, sizeof latest);
    changed_at = 0;
    pressed = pressed_mask = 0;
    pen_pending = pen_x = pen_y = 0;
    pthread_mutex_unlock(&input_lock);
}

void input_mailbox_cancel_pen(void) {
    pthread_mutex_lock(&input_lock);
    pen_pending = 0;
    latest.touch_down = latest.touch_x = latest.touch_y = 0;
    pthread_mutex_unlock(&input_lock);
}
