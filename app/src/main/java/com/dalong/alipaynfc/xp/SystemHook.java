package com.dalong.alipaynfc.xp;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserManager;

import com.dalong.alipaynfc.C;

import org.json.JSONArray;
import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Loaded inside system_server.
 * Enumerates Android users that have Alipay installed (system app-cloning runs the same
 * package under a hidden user id, usually 999) and reports the list to the module app.
 * Also relays REQUEST_SCAN / CONFIG_CHANGED broadcasts.
 */
public final class SystemHook {

    private static volatile Context sysCtx;
    private static Handler handler;
    private static volatile boolean initialized;

    private SystemHook() {}

    public static void init(XC_LoadPackage.LoadPackageParam lpp) {
        XLog.i("loaded in system_server sdk=" + android.os.Build.VERSION.SDK_INT);
        try {
            Class<?> systemServer = XposedHelpers.findClass("com.android.server.SystemServer",
                    lpp.classLoader);
            XposedBridge.hookAllMethods(systemServer, "startOtherServices", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (sysCtx != null) return;
                        sysCtx = getSystemContext(lpp.classLoader);
                        if (sysCtx == null) {
                            // Fall back to the historical field (pre-R ROMs).
                            try {
                                sysCtx = (Context) XposedHelpers.getObjectField(
                                        param.thisObject, "mContext");
                            } catch (Throwable ignored) {
                            }
                        }
                        XLog.i("system context acquired: " + (sysCtx != null));
                        if (sysCtx == null) return;
                        HandlerThread ht = new HandlerThread("AlipayNfcSwitch");
                        ht.start();
                        handler = new Handler(ht.getLooper());
                        hookSystemReady(lpp.classLoader);
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
            Object at = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", cl), "systemMain");
            return (Context) XposedHelpers.callMethod(at, "getSystemContext");
        } catch (Throwable t) {
            XLog.e("getSystemContext via ActivityThread failed");
            XLog.e(t);
            return null;
        }
    }

    private static void hookSystemReady(ClassLoader cl) {
        try {
            Class<?> ams = XposedHelpers.findClassIfExists(
                    "com.android.server.am.ActivityManagerService", cl);
            if (ams == null) {
                XLog.e("ActivityManagerService not found");
                return;
            }
            XposedBridge.hookAllMethods(ams, "systemReady", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
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
            f.addAction(C.ACTION_CONFIG_CHANGED);
            int flags = android.os.Build.VERSION.SDK_INT >= 33
                    ? android.content.Context.RECEIVER_EXPORTED : 0;
            sysCtx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (C.ACTION_REQUEST_SCAN.equals(action) && handler != null) {
                        handler.post(SystemHook::scanAndBroadcast);
                    } else if (C.ACTION_REQUEST_CLEAR.equals(action) && handler != null) {
                        handler.post(() -> GStore.clearRecords(sysCtx));
                    } else if (C.ACTION_CONFIG_CHANGED.equals(action)) {
                        HookConfig.invalidate();
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
                userObj = XposedHelpers.callMethod(um, "getUsers", true);
                isUserInfo = true;
            } catch (Throwable t1) {
                try {
                    userObj = XposedHelpers.callMethod(um, "getUserHandles", true);
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
                        id = (int) XposedHelpers.getIntField(u, "id");
                        Object n = XposedHelpers.getObjectField(u, "name");
                        name = n == null ? "" : n.toString();
                        boolean partial = XposedHelpers.getBooleanField(u, "partial");
                        if (partial && !packageInstalled(ctx, C.ALIPAY_PKG, id)) continue;
                    } else {
                        id = (int) XposedHelpers.callMethod(u, "getIdentifier");
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
            Object globals = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.AppGlobals",
                            ctx.getClassLoader()), "getPackageManager");
            try {
                return XposedHelpers.callMethod(globals, "getPackageInfo", pkg, 0L, userId)
                        != null;
            } catch (Throwable longFlagsError) {
                return XposedHelpers.callMethod(globals, "getPackageInfo", pkg, 0, userId)
                        != null;
            }
        } catch (Throwable t) {
            XLog.e("packageInstalled(" + pkg + ", user " + userId + ") failed");
            XLog.e(t);
            return false;
        }
    }
}
