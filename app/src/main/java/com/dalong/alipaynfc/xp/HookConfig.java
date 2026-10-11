package com.dalong.alipaynfc.xp;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import com.dalong.alipaynfc.C;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Config reader for hooked processes. Channels (first available wins):
 * 1. XSharedPreferences — requires LSPosed "xposedsharedprefs" support (world-readable prefs).
 * 2. ContentProvider call into the module app.
 * Values are cached for a couple of seconds; {@link #invalidate()} is triggered by a
 * config-changed broadcast for immediate effect.
 */
public final class HookConfig {

    public static class Cache {
        public boolean valid = false;
        public boolean enabled = true;
        public int targetUser = 0;
        public String targetPkg = "";
        public String alipayPkg = C.ALIPAY_PKG;
        public boolean keepHistory = true;
        public long revision = -1;
        public String source = "default";
    }

    private static final Object LOCK = new Object();
    private static final long RELOAD_INTERVAL_MS = 2000;

    private static volatile Cache cached;
    private static long lastReloadElapsed;
    private static XSharedPreferences xsp;

    private HookConfig() {}

    /** Returns cached config; reloads from the app if stale. Never returns null. */
    public static Cache get(Context ctx) {
        Cache c = cached;
        if (c != null && SystemClock.elapsedRealtime() - lastReloadElapsed < RELOAD_INTERVAL_MS) {
            return c;
        }
        synchronized (LOCK) {
            if (cached != null && SystemClock.elapsedRealtime() - lastReloadElapsed < RELOAD_INTERVAL_MS) {
                return cached;
            }
            Cache fresh = load(ctx);
            lastReloadElapsed = SystemClock.elapsedRealtime();
            if (fresh.valid) {
                cached = fresh;
                return fresh;
            }
            return cached != null ? cached : fresh;
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cached = null;
            lastReloadElapsed = 0;
        }
        XLog.i("config cache invalidated");
    }

    private static Cache load(Context ctx) {
        Cache a = loadXsp();
        if (a.valid) return a;
        Cache b = loadProvider(ctx);
        if (b.valid) return b;
        return a; // empty defaults; keep the system untouched (enabled=true, user=0)
    }

    private static Cache loadXsp() {
        Cache c = new Cache();
        try {
            if (xsp == null) {
                xsp = new XSharedPreferences(C.MODULE_PKG, C.PREFS);
            }
            xsp.reload();
            c.enabled = xsp.getBoolean(C.K_ENABLED, true);
            c.targetUser = xsp.getInt(C.K_TARGET_USER, 0);
            c.targetPkg = xsp.getString(C.K_TARGET_PKG, "");
            c.alipayPkg = xsp.getString(C.K_ALIPAY_PKG, C.ALIPAY_PKG);
            c.keepHistory = xsp.getBoolean(C.K_KEEP_HISTORY, true);
            c.revision = xsp.getLong(C.K_REVISION, -1);
            c.source = "xsp";
            c.valid = xsp.contains(C.K_ENABLED);
        } catch (Throwable t) {
            XLog.e("loadXsp failed");
            XLog.e(t);
        }
        return c;
    }

    private static Cache loadProvider(Context ctx) {
        Cache c = new Cache();
        if (ctx == null) return c;
        try {
            Bundle b = ctx.getContentResolver()
                    .call(Uri.parse("content://" + C.MODULE_PKG + ".CP"), "getConfig", null, null);
            if (b == null) return c;
            c.enabled = b.getBoolean(C.K_ENABLED, true);
            c.targetUser = b.getInt(C.K_TARGET_USER, 0);
            c.targetPkg = b.getString(C.K_TARGET_PKG, "");
            c.alipayPkg = b.getString(C.K_ALIPAY_PKG, C.ALIPAY_PKG);
            c.keepHistory = b.getBoolean(C.K_KEEP_HISTORY, true);
            c.revision = b.getLong(C.K_REVISION, -1);
            c.source = "provider";
            c.valid = b.containsKey(C.K_ENABLED);
        } catch (Throwable t) {
            XLog.e("loadProvider failed");
            XLog.e(t);
        }
        return c;
    }
}
