#ifndef SEEDLESS_METRICS_H
#define SEEDLESS_METRICS_H
#ifdef __cplusplus
extern "C" {
#endif
#include <stdint.h>
enum { NDS_METRIC_INPUT, NDS_METRIC_FRAME, NDS_METRIC_REWIND, NDS_METRIC_COPY, NDS_METRICS };
uint64_t nds_metrics_now_us(void);
void nds_metrics_frame_boundary(void);
void nds_metrics_frame_gap(void);
void nds_metrics_record(unsigned metric, uint64_t us);
uint64_t nds_metrics_percentile(unsigned metric, unsigned percentile);
uint64_t nds_metrics_count(unsigned metric);
#ifdef __cplusplus
}
#endif
#endif
