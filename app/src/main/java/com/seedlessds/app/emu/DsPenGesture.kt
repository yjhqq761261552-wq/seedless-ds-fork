package com.seedlessds.app.emu

import android.view.MotionEvent
import android.view.View

/** Android event adapter. Historical points deliberately do not enter the latest-state mailbox. */
internal class DsPenGesture(private val view: View, private val cancel: () -> Unit, send: (Boolean, Int) -> Unit) {
    private val pen = DsPenPointer(send)

    fun release() { pen.cancel(emitRelease = false); cancel() }

    fun handle(event: MotionEvent, rect: FloatArray, enabled: Boolean = true): Boolean {
        if (!enabled || rect.size < 4 || rect[2] <= 0f || rect[3] <= 0f) {
            release()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pen.cancel()
                val accepted = pen.down(event.getPointerId(0), event.x, event.y, rect)
                // MotionEvent overload is available since API 21, our minimum.
                if (accepted && SettingsRepo.current.lowLatencyTouch) view.requestUnbufferedDispatch(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pen.down(event.getPointerId(i), event.getX(i), event.getY(i), rect)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(pen.activeId)
                if (i < 0) release()
                else pen.move(pen.activeId, event.getX(i), event.getY(i), rect)
            }
            MotionEvent.ACTION_POINTER_UP -> pen.up(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> pen.cancel()
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }
}
