package com.dalong.alipaynfc.tap;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

/**
 * Debug helper: taps/swipes via UiAutomation.injectInputEvent, which works on HyperOS
 * without the "USB debugging (security settings)" toggle.
 *
 * Usage:
 *   am instrument -w -e mode tap -e x 100 -e y 200 com.dalong.alipaynfc/com.dalong.alipaynfc.tap.TapInstrumentation
 *   am instrument -w -e mode swipe -e x1 100 -e y1 800 -e x2 100 -e y2 200 -e dur 300 \
 *       com.dalong.alipaynfc/com.dalong.alipaynfc.tap.TapInstrumentation
 */
public class TapInstrumentation extends Instrumentation {

    private static final String TAG = "TapInstr";
    private Bundle args;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments;
        start();
    }

    @Override
    public void onStart() {
        new Thread(() -> {
            int status = 0;
            try {
                String mode = args == null ? "tap" : orDefault(args.getString("mode"), "tap");
                UiAutomation ua = getUiAutomation();
                // getUiAutomation() connects asynchronously; wait until it is usable.
                long deadline = SystemClock.uptimeMillis() + 5000;
                while (SystemClock.uptimeMillis() < deadline) {
                    try {
                        if (ua.getRootInActiveWindow() != null) break;
                    } catch (Throwable ignored) {
                    }
                    Thread.sleep(100);
                }
                Thread.sleep(300);
                if ("swipe".equals(mode)) {
                    float x1 = Float.parseFloat(require("x1"));
                    float y1 = Float.parseFloat(require("y1"));
                    float x2 = Float.parseFloat(require("x2"));
                    float y2 = Float.parseFloat(require("y2"));
                    long dur = Long.parseLong(orDefault(args.getString("dur"), "300"));
                    swipe(ua, x1, y1, x2, y2, dur);
                    Log.i(TAG, "swipe " + x1 + "," + y1 + " -> " + x2 + "," + y2);
                } else {
                    float x = Float.parseFloat(require("x"));
                    float y = Float.parseFloat(require("y"));
                    tap(ua, x, y);
                    Log.i(TAG, "tap " + x + "," + y);
                }
                Thread.sleep(300);
            } catch (Throwable t) {
                Log.e(TAG, "failed", t);
                status = 1;
            } finally {
                finish(status, null);
            }
        }).start();
    }

    private static void tap(UiAutomation ua, float x, float y) throws Throwable {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(down, true);
        down.recycle();
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(up, true);
        up.recycle();
    }

    private static void swipe(UiAutomation ua, float x1, float y1, float x2, float y2, long dur)
            throws Throwable {
        long start = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x1, y1, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(down, true);
        down.recycle();
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            long t = start + dur * i / steps;
            float fx = x1 + (x2 - x1) * i / steps;
            float fy = y1 + (y2 - y1) * i / steps;
            MotionEvent move = MotionEvent.obtain(start, t, MotionEvent.ACTION_MOVE, fx, fy, 0);
            move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            ua.injectInputEvent(move, true);
            move.recycle();
            Thread.sleep(dur / steps);
        }
        MotionEvent up = MotionEvent.obtain(start, start + dur, MotionEvent.ACTION_UP, x2, y2, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(up, true);
        up.recycle();
    }

    private String require(String key) {
        String v = args == null ? null : args.getString(key);
        if (v == null) throw new IllegalArgumentException("missing -e " + key);
        return v;
    }

    private static String orDefault(String v, String def) {
        return v == null ? def : v;
    }
}
