package com.dalong.alipaynfc.xp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.dalong.alipaynfc.C;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional legacy fallback inside system_server (if the user also scoped system/android).
 * Primary scanning and record management now run directly inside com.android.nfc.
 */
public final class SystemHook {

    private static final AtomicBoolean hookInstalled = new AtomicBoolean(false);
    private static volatile Context sysCtx;
    private static Handler handler;
    private static volatile boolean initialized;

    private SystemHook() {}

    public static void init(ClassLoader cl) {
        if (!hookInstalled.compareAndSet(false, true)) return;
        XLog.i("loaded in system_server sdk=" + Build.VERSION.SDK_INT
                + " modern=" + HookRuntime.isModern());
        try {
            Class<?> systemServer = Reflect.findClass("com.android.server.SystemServer", cl);
            HookRuntime.hookAllMethods(systemServer, "startOtherServices", new HookRuntime.MethodCallback() {
                @Override
                protected void after(HookRuntime.HookParam param) {
                    try {
                        if (sysCtx != null) return;
                        sysCtx = getSystemContext(cl);
                        if (sysCtx == null) {
                            try {
                                sysCtx = (Context) Reflect.getField(param.thisObject, "mContext");
                            } catch (Throwable ignored) {
                            }
                        }
                        if (sysCtx == null) return;
                        HandlerThread ht = new HandlerThread("AlipayNfcSwitch");
                        ht.start();
                        handler = new Handler(ht.getLooper());
                        hookSystemReady(cl);
                        handler.postDelayed(SystemHook::initialize, 8000);
                    } catch (Throwable t) {
                        XLog.e("SystemHook.startOtherServices after failed");
                        XLog.e(t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("SystemHook init failed");
            XLog.e(t);
        }
    }

    private static Context getSystemContext(ClassLoader cl) {
        try {
            Class<?> atCls = Reflect.findClass("android.app.ActivityThread", cl);
            Object at = null;
            try {
                at = Reflect.callStatic(atCls, "currentActivityThread", new Class<?>[0]);
            } catch (Throwable ignored) {
            }
            if (at == null) {
                at = Reflect.callStatic(atCls, "systemMain", new Class<?>[0]);
            }
            return at == null ? null : (Context) Reflect.call(at, "getSystemContext", new Class<?>[0]);
        } catch (Throwable t) {
            XLog.e("getSystemContext via ActivityThread failed");
            XLog.e(t);
            return null;
        }
    }

    private static void hookSystemReady(ClassLoader cl) {
        try {
            Class<?> ams = Reflect.findClassOrNull("com.android.server.am.ActivityManagerService", cl);
            if (ams == null) return;
            HookRuntime.hookAllMethods(ams, "systemReady", new HookRuntime.MethodCallback() {
                @Override
                protected void after(HookRuntime.HookParam param) {
                    initialize();
                }
            });
        } catch (Throwable t) {
            XLog.e("hookSystemReady failed");
            XLog.e(t);
        }
    }

    private static void initialize() {
        if (initialized || sysCtx == null || handler == null) return;
        try {
            initialized = true;
            registerReceiver();
            handler.postDelayed(() -> UserScanner.scanAndBroadcast(sysCtx, "system"), 1500);
            NfcHook.sendAlive(sysCtx, "android");
        } catch (Throwable t) {
            initialized = false;
            XLog.e(t);
        }
    }

    private static void registerReceiver() {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(C.ACTION_REQUEST_SCAN);
            f.addAction(C.ACTION_REQUEST_CLEAR);
            int flags = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_EXPORTED : 0;
            sysCtx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (C.ACTION_REQUEST_SCAN.equals(action) && handler != null) {
                        handler.post(() -> UserScanner.scanAndBroadcast(sysCtx, "system"));
                    } else if (C.ACTION_REQUEST_CLEAR.equals(action) && handler != null) {
                        handler.post(() -> GStore.clearRecords(sysCtx));
                    }
                }
            }, f, flags);
        } catch (Throwable t) {
            XLog.e(t);
        }
    }
}
