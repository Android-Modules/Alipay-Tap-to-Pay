package com.dalong.alipaynfc.xp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import com.dalong.alipaynfc.C;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Config reader for hooked processes. Channels:
 * 1. libxposed API 102 RemotePreferences (native framework sync).
 * 2. Legacy XSharedPreferences (world-readable prefs on LSPosed 1.9.x).
 * 3. ContentProvider call into the module app.
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
    private static volatile SharedPreferences remotePrefs;

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
        Cache primary = HookRuntime.isModern() ? loadRemotePrefs() : loadLegacyXsp();
        Cache provider = loadProvider(ctx);

        Cache chosen;
        if (configChanged) {
            if (provider.valid && (!primary.valid || provider.revision >= primary.revision)) {
                chosen = provider;
            } else {
                chosen = primary.valid ? primary : provider;
            }
        } else {
            if (primary.valid && (!provider.valid || primary.revision >= provider.revision)) {
                chosen = primary;
            } else {
                chosen = provider.valid ? provider : primary;
            }
        }
        if (chosen.valid && chosen.revision >= cached.revision) {
            cached = chosen;
        } else if (chosen.valid && !cached.valid) {
            cached = chosen;
        }
        nextReloadElapsed = SystemClock.elapsedRealtime() + RELOAD_INTERVAL_MS;
    }

    private static Cache loadRemotePrefs() {
        Cache c = new Cache();
        try {
            if (remotePrefs == null) {
                remotePrefs = HookRuntime.getRemotePreferences(C.PREFS);
            }
            if (remotePrefs == null) return c;
            return readFromPrefs(remotePrefs, "remote");
        } catch (Throwable t) {
            XLog.e("loadRemotePrefs failed");
            XLog.e(t);
        }
        return c;
    }

    private static Cache loadLegacyXsp() {
        Cache c = new Cache();
        try {
            SharedPreferences xsp = HookRuntime.getLegacyXSharedPreferences(C.MODULE_PKG, C.PREFS);
            if (xsp == null) return c;
            return readFromPrefs(xsp, "xsp");
        } catch (Throwable t) {
            XLog.e("loadLegacyXsp failed");
            XLog.e(t);
        }
        return c;
    }

    private static Cache readFromPrefs(SharedPreferences sp, String source) {
        Cache c = new Cache();
        c.enabled = sp.getBoolean(C.K_ENABLED, true);
        c.targetUser = sp.getInt(C.K_TARGET_USER, 0);
        c.targetPkg = sp.getString(C.K_TARGET_PKG, "");
        c.alipayPkg = sp.getString(C.K_ALIPAY_PKG, C.ALIPAY_PKG);
        c.keepHistory = sp.getBoolean(C.K_KEEP_HISTORY, true);
        c.revision = sp.getLong(C.K_REVISION, -1);
        c.source = source;
        c.valid = sp.contains(C.K_ENABLED);
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
