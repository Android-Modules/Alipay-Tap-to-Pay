package com.dalong.alipaynfc.xp;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import com.dalong.alipaynfc.C;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Config reader for hooked processes. Channels (first available wins):
 * 1. XSharedPreferences — requires LSPosed "xposedsharedprefs" support (world-readable prefs).
 * 2. ContentProvider call into the module app.
 * Only the NFC worker reads either channel. Dispatch consumes the latest snapshot.
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

    private static final long RELOAD_INTERVAL_MS = 2000;
    private static final AtomicBoolean refreshQueued = new AtomicBoolean();

    private static volatile Cache cached = new Cache();
    private static volatile long nextReloadElapsed;
    private static XSharedPreferences xsp;

    private HookConfig() {}

    static Cache get(Context ctx, Handler worker) {
        if (worker != null && SystemClock.elapsedRealtime() >= nextReloadElapsed
                && refreshQueued.compareAndSet(false, true)) {
            if (!worker.post(() -> {
                try {
                    if (SystemClock.elapsedRealtime() >= nextReloadElapsed) refresh(ctx, false);
                } finally {
                    refreshQueued.set(false);
                }
            })) {
                refreshQueued.set(false);
            }
        }
        return cached;
    }

    /** Runs on the NFC worker, including after the settings UI broadcasts a change. */
    static void refresh(Context ctx, boolean configChanged) {
        // The app's in-memory preferences are current even while apply() is writing the file.
        Cache fresh = configChanged ? loadProvider(ctx) : loadXsp();
        if (!fresh.valid || (!configChanged && fresh.revision < cached.revision)) {
            fresh = configChanged ? loadXsp() : loadProvider(ctx);
        }
        if (fresh.valid) cached = fresh;
        nextReloadElapsed = SystemClock.elapsedRealtime() + RELOAD_INTERVAL_MS;
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
