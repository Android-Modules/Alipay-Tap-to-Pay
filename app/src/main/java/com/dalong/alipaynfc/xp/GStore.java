package com.dalong.alipaynfc.xp;

import android.content.Context;
import android.provider.Settings;

import com.dalong.alipaynfc.C;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Hook-side writes to Settings.Global — a broadcast-free channel readable by the app
 * from any process state (MIUI may block background broadcast delivery).
 */
public final class GStore {
    private static final int MAX_RECORDS = 30;

    private GStore() {}

    public static void markAlive(Context ctx, String process) {
        try {
            String raw = Settings.Global.getString(ctx.getContentResolver(), C.GKEY_ALIVE);
            JSONObject o = new JSONObject(raw == null ? "{}" : raw);
            o.put(process, System.currentTimeMillis());
            o.put(process + "_boot", Settings.Global.getInt(ctx.getContentResolver(),
                    Settings.Global.BOOT_COUNT, -1));
            Settings.Global.putString(ctx.getContentResolver(), C.GKEY_ALIVE, o.toString());
        } catch (Throwable t) {
            XLog.e("GStore.markAlive failed");
            XLog.e(t);
        }
    }

    public static void putUsers(Context ctx, String json) {
        try {
            Settings.Global.putString(ctx.getContentResolver(), C.GKEY_USERS, json);
        } catch (Throwable t) {
            XLog.e("GStore.putUsers failed");
            XLog.e(t);
        }
    }

    public static synchronized void addRecord(Context ctx, String recJson) {
        try {
            String cur = Settings.Global.getString(ctx.getContentResolver(), C.GKEY_RECORDS);
            JSONArray arr = new JSONArray(cur == null ? "[]" : cur);
            JSONArray next = new JSONArray();
            next.put(new JSONObject(recJson));
            for (int i = 0; i < arr.length() && next.length() < MAX_RECORDS; i++) {
                next.put(arr.get(i));
            }
            Settings.Global.putString(ctx.getContentResolver(), C.GKEY_RECORDS, next.toString());
        } catch (Throwable t) {
            XLog.e("GStore.addRecord failed");
            XLog.e(t);
        }
    }

    public static void clearRecords(Context ctx) {
        try {
            Settings.Global.putString(ctx.getContentResolver(), C.GKEY_RECORDS, "[]");
        } catch (Throwable ignored) {
        }
    }
}
