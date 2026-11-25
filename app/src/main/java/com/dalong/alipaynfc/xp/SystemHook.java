package com.dalong.alipaynfc.xp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserManager;

import com.dalong.alipaynfc.C;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loaded inside system_server.
 * Enumerates Android users that have Alipay installed (system app-cloning runs the same
 * package under a hidden user id, usually 999) and reports the list to the module app.
 * Also handles scan and history-clear requests.
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
                        XLog.i("system context acquired: " + (sysCtx != null));
                        if (sysCtx == null) return;
                        HandlerThread ht = new HandlerThread("AlipayNfcSwitch");
                        ht.start();
                        handler = new Handler(ht.getLooper());
                        hookSystemReady(cl);
                        // Fallback trigger: AMS.systemReady may not be hookable on all ROMs.
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

    /** SystemServer.mContext was removed/obfuscated on Android 16; go via ActivityThread. */
    private static Context getSystemContext(ClassLoader cl) {
        try {
            Class<?> atCls = Reflect.findClass("android.app.ActivityThread", cl);
            Object at = Reflect.callStatic(atCls, "systemMain", new Class<?>[0]);
            return (Context) Reflect.call(at, "getSystemContext", new Class<?>[0]);
        } catch (Throwable t) {
            XLog.e("getSystemContext via ActivityThread failed");
            XLog.e(t);
            return null;
        }
    }

    private static void hookSystemReady(ClassLoader cl) {
        try {
            Class<?> ams = Reflect.findClassOrNull("com.android.server.am.ActivityManagerService", cl);
            if (ams == null) {
                XLog.e("ActivityManagerService not found");
                return;
            }
            HookRuntime.hookAllMethods(ams, "systemReady", new HookRuntime.MethodCallback() {
                @Override
                protected void after(HookRuntime.HookParam param) {
                    try {
                        XLog.i("systemReady observed");
                        initialize();
                    } catch (Throwable t) {
                        XLog.e(t);
                    }
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
            handler.postDelayed(SystemHook::scanAndBroadcast, 1500);
            NfcHook.sendAlive(sysCtx, "android");
            XLog.i("initialized");
        } catch (Throwable t) {
            initialized = false;
            XLog.e("initialize failed");
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
                        handler.post(SystemHook::scanAndBroadcast);
                    } else if (C.ACTION_REQUEST_CLEAR.equals(action) && handler != null) {
                        handler.post(() -> GStore.clearRecords(sysCtx));
                    }
                }
            }, f, flags);
            XLog.i("request-scan receiver registered");
        } catch (Throwable t) {
            XLog.e("registerReceiver failed");
            XLog.e(t);
        }
    }

    private static void scanAndBroadcast() {
        try {
            Context ctx = sysCtx;
            if (ctx == null) return;
            long start = System.currentTimeMillis();
            UserManager um = ctx.getSystemService(UserManager.class);
            JSONArray users = new JSONArray();

            Object userObj = null;
            boolean isUserInfo = false;
            try {
                userObj = Reflect.call(um, "getUsers", new Class<?>[]{boolean.class}, true);
                isUserInfo = true;
            } catch (Throwable t1) {
                try {
                    userObj = Reflect.call(um, "getUserHandles", new Class<?>[]{boolean.class}, true);
                } catch (Throwable t2) {
                    XLog.e("user enumeration failed");
                    XLog.e(t1);
                    XLog.e(t2);
                }
            }
            if (userObj instanceof Iterable) {
                for (Object u : (Iterable<?>) userObj) {
                    if (u == null) continue;
                    int id;
                    String name = "";
                    if (isUserInfo) {
                        id = Reflect.getIntField(u, "id");
                        Object n = Reflect.getField(u, "name");
                        name = n == null ? "" : n.toString();
                        boolean partial = Reflect.getBooleanField(u, "partial");
                        if (partial && !packageInstalled(ctx, C.ALIPAY_PKG, id)) continue;
                    } else {
                        id = (int) Reflect.call(u, "getIdentifier", new Class<?>[0]);
                    }
                    boolean alipay = packageInstalled(ctx, C.ALIPAY_PKG, id);
                    JSONObject o = new JSONObject();
                    o.put("id", id);
                    o.put("name", name);
                    o.put("alipay", alipay);
                    users.put(o);
                }
            }

            JSONObject result = new JSONObject();
            result.put("users", users);
            result.put("ts", System.currentTimeMillis());
            result.put("elapsed", System.currentTimeMillis() - start);

            GStore.putUsers(ctx, result.toString());
            Intent bi = new Intent(C.ACTION_SCAN_RESULT);
            bi.setPackage(C.MODULE_PKG);
            bi.putExtra(C.EXTRA_JSON, result.toString());
            ctx.sendBroadcast(bi);
            XLog.i("scan done: " + result);
        } catch (Throwable t) {
            XLog.e("scanAndBroadcast failed");
            XLog.e(t);
        }
    }

    private static boolean packageInstalled(Context ctx, String pkg, int userId) {
        try {
            Class<?> appGlobals = Reflect.findClass("android.app.AppGlobals", ctx.getClassLoader());
            Object globals = Reflect.callStatic(appGlobals, "getPackageManager", new Class<?>[0]);
            try {
                return Reflect.call(
                        globals,
                        "getPackageInfo",
                        new Class<?>[]{String.class, long.class, int.class},
                        pkg, 0L, userId) != null;
            } catch (Throwable longFlagsError) {
                return Reflect.call(
                        globals,
                        "getPackageInfo",
                        new Class<?>[]{String.class, int.class, int.class},
                        pkg, 0, userId) != null;
            }
        } catch (Throwable t) {
            XLog.e("packageInstalled(" + pkg + ", user " + userId + ") failed");
            XLog.e(t);
            return false;
        }
    }
}
