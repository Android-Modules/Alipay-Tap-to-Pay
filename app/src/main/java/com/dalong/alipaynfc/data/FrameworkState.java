package com.dalong.alipaynfc.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.dalong.alipaynfc.C;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * App-side libxposed service integration (API 102):
 * tracks framework metadata/scope and mirrors config to RemotePreferences.
 */
public final class FrameworkState {

    public interface Listener {
        void onFrameworkStateChanged();
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    private static volatile boolean registered;
    private static volatile Context appContext;
    private static volatile XposedService boundService;
    private static volatile Set<String> boundScope;

    private FrameworkState() {}

    public static void init(Context ctx) {
        if (registered) return;
        registered = true;
        appContext = ctx.getApplicationContext();
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    boundService = service;
                    try {
                        List<String> scope = service.getScope();
                        boundScope = scope == null ? null : Set.copyOf(scope);
                    } catch (Throwable ignored) {
                    }
                    if (appContext != null) {
                        Store.Config current = Store.loadConfig(appContext);
                        long rev = Store.revision(appContext);
                        syncConfig(current, rev);
                    }
                    notifyListeners();
                }

                @Override
                public void onServiceDied(XposedService service) {
                    if (boundService == service) {
                        boundService = null;
                        boundScope = null;
                    }
                    notifyListeners();
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void syncConfig(Store.Config c, long revision) {
        XposedService service = boundService;
        if (service == null || c == null) return;
        try {
            SharedPreferences remote = service.getRemotePreferences(C.PREFS);
            if (remote == null) return;
            remote.edit()
                    .putBoolean(C.K_ENABLED, c.enabled)
                    .putInt(C.K_TARGET_USER, c.targetUser)
                    .putString(C.K_TARGET_PKG, c.targetPkg == null ? "" : c.targetPkg.trim())
                    .putString(C.K_ALIPAY_PKG, c.alipayPkg == null || c.alipayPkg.trim().isEmpty()
                            ? C.ALIPAY_PKG : c.alipayPkg.trim())
                    .putBoolean(C.K_KEEP_HISTORY, c.keepHistory)
                    .putLong(C.K_REVISION, revision)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isBound() {
        return boundService != null;
    }

    public static String getFrameworkSummary() {
        XposedService service = boundService;
        if (service == null) return null;
        try {
            String name = service.getFrameworkName();
            String ver = service.getFrameworkVersion();
            int api = service.getApiVersion();
            return name + " " + ver + " · API " + api;
        } catch (Throwable e) {
            return "libxposed API 102";
        }
    }

    public static Set<String> getScope() {
        return boundScope;
    }

    public static void addListener(Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        mainHandler.post(() -> {
            for (Listener l : listeners) {
                try {
                    l.onFrameworkStateChanged();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
