package com.dalong.alipaynfc.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.dalong.alipaynfc.C;

import org.json.JSONArray;
import org.json.JSONObject;

/** App-side storage: configuration (read by hooks via XSharedPreferences/provider) + records. */
public final class Store {
    private static final int MAX_RECORDS = 30;

    private Store() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(C.PREFS, Context.MODE_PRIVATE);
    }

    public static class Config {
        public boolean enabled = true;
        public int targetUser = 0;
        public String targetPkg = "";
        public String alipayPkg = C.ALIPAY_PKG;
        public boolean keepHistory = true;
    }

    public static Config loadConfig(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Config c = new Config();
        c.enabled = p.getBoolean(C.K_ENABLED, true);
        c.targetUser = p.getInt(C.K_TARGET_USER, 0);
        c.targetPkg = p.getString(C.K_TARGET_PKG, "");
        c.alipayPkg = p.getString(C.K_ALIPAY_PKG, C.ALIPAY_PKG);
        c.keepHistory = p.getBoolean(C.K_KEEP_HISTORY, true);
        return c;
    }

    public static void saveConfig(Context ctx, Config c) {
        SharedPreferences p = prefs(ctx);
        long revision = p.getLong(C.K_REVISION, 0) + 1;
        p.edit()
                .putBoolean(C.K_ENABLED, c.enabled)
                .putInt(C.K_TARGET_USER, c.targetUser)
                .putString(C.K_TARGET_PKG, c.targetPkg == null ? "" : c.targetPkg.trim())
                .putString(C.K_ALIPAY_PKG, c.alipayPkg == null || c.alipayPkg.trim().isEmpty()
                        ? C.ALIPAY_PKG : c.alipayPkg.trim())
                .putBoolean(C.K_KEEP_HISTORY, c.keepHistory)
                .putLong(C.K_REVISION, revision)
                .apply();
    }

    public static long revision(Context ctx) {
        return prefs(ctx).getLong(C.K_REVISION, 0);
    }

    public static void saveUsers(Context ctx, String json) {
        prefs(ctx).edit().putString(C.K_USERS_JSON, json).apply();
    }

    public static String getUsers(Context ctx) {
        // Settings.Global is written by the hooks and readable from any process state.
        String g = null;
        try {
            g = android.provider.Settings.Global.getString(ctx.getContentResolver(), C.GKEY_USERS);
        } catch (Exception ignored) {
        }
        return g != null ? g : prefs(ctx).getString(C.K_USERS_JSON, "");
    }

    public static void setAlive(Context ctx, String process) {
        SharedPreferences p = prefs(ctx);
        try {
            JSONObject alive = new JSONObject(getAlive(ctx));
            alive.put(process, System.currentTimeMillis());
            p.edit().putString(C.K_ALIVE_JSON, alive.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static String getAlive(Context ctx) {
        String g = null;
        try {
            g = android.provider.Settings.Global.getString(ctx.getContentResolver(), C.GKEY_ALIVE);
        } catch (Exception ignored) {
        }
        return g != null ? g : prefs(ctx).getString(C.K_ALIVE_JSON, "{}");
    }

    public static synchronized void addRecord(Context ctx, String recJson) {
        SharedPreferences p = prefs(ctx);
        try {
            JSONArray arr = new JSONArray(p.getString(C.K_HISTORY_JSON, "[]"));
            JSONArray next = new JSONArray();
            next.put(new JSONObject(recJson));
            for (int i = 0; i < arr.length() && next.length() < MAX_RECORDS; i++) {
                next.put(arr.get(i));
            }
            p.edit().putString(C.K_HISTORY_JSON, next.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static JSONArray getRecords(Context ctx) {
        try {
            String g = android.provider.Settings.Global.getString(ctx.getContentResolver(),
                    C.GKEY_RECORDS);
            return new JSONArray(g == null ? prefs(ctx).getString(C.K_HISTORY_JSON, "[]") : g);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void clearRecords(Context ctx) {
        prefs(ctx).edit().putString(C.K_HISTORY_JSON, "[]").apply();
        // Hook side (system_server) owns the global store; ask it to clear too.
        try {
            android.content.Intent i = new android.content.Intent(C.ACTION_REQUEST_CLEAR);
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }
}
