package com.dalong.alipaynfc.xp;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;

import com.dalong.alipaynfc.C;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Scans Android users (main user 0, XSpace 999, work/clone profiles) for installed Alipay
 * instances and publishes the result to Settings.Global + app broadcast.
 * Runs directly inside com.android.nfc (which holds MANAGE_USERS, INTERACT_ACROSS_USERS_FULL,
 * QUERY_ALL_PACKAGES, and WRITE_SECURE_SETTINGS on Android).
 */
public final class UserScanner {

    private static volatile long lastScanElapsed;

    private UserScanner() {}

    public static synchronized void scanAndBroadcast(Context ctx, String source) {
        if (ctx == null) return;
        long nowElapsed = SystemClock.elapsedRealtime();
        if (nowElapsed - lastScanElapsed < 250) return;
        lastScanElapsed = nowElapsed;

        try {
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
                    XLog.e("user enumeration failed in " + source);
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
            result.put("source", source);
            result.put("ts", System.currentTimeMillis());
            result.put("elapsed", System.currentTimeMillis() - start);

            GStore.putUsers(ctx, result.toString());
            Intent bi = new Intent(C.ACTION_SCAN_RESULT);
            bi.setPackage(C.MODULE_PKG);
            bi.putExtra(C.EXTRA_JSON, result.toString());
            ctx.sendBroadcast(bi);
            XLog.i("scan done (" + source + "): " + result);
        } catch (Throwable t) {
            XLog.e("scanAndBroadcast failed in " + source);
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
