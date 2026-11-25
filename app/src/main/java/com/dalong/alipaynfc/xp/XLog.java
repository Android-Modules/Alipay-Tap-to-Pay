package com.dalong.alipaynfc.xp;

import android.util.Log;

/** Logging helper: visible in logcat (tag AlipayNfc) and the active Xposed runtime log. */
public final class XLog {
    private static final String TAG = "AlipayNfc";

    private XLog() {}

    public static void i(String msg) {
        try {
            Log.i(TAG, msg);
            HookRuntime.log(Log.INFO, TAG, msg, null);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String msg) {
        try {
            Log.e(TAG, msg);
            HookRuntime.log(Log.ERROR, TAG, msg, null);
        } catch (Throwable ignored) {
        }
    }

    public static void e(Throwable t) {
        try {
            Log.e(TAG, Log.getStackTraceString(t));
            HookRuntime.log(Log.ERROR, TAG, Log.getStackTraceString(t), t);
        } catch (Throwable ignored) {
        }
    }
}
