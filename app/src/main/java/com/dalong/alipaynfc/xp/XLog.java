package com.dalong.alipaynfc.xp;

import de.robv.android.xposed.XposedBridge;

/** Logging helper: visible both in logcat (tag AlipayNfc) and the LSPosed log. */
public final class XLog {
    private static final String TAG = "AlipayNfc";

    private XLog() {}

    public static void i(String msg) {
        try {
            android.util.Log.i(TAG, msg);
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String msg) {
        try {
            android.util.Log.e(TAG, msg);
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }

    public static void e(Throwable t) {
        try {
            android.util.Log.e(TAG, android.util.Log.getStackTraceString(t));
            XposedBridge.log(t);
        } catch (Throwable ignored) {
        }
    }
}
