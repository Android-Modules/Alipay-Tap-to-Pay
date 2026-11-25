package com.dalong.alipaynfc.xp;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Executable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

/** Legacy Xposed API (LSPosed 1.9.x) backend. */
final class LegacyHookBackend implements HookRuntime.HookBackend {

    private XSharedPreferences cachedXsp;
    private String cachedXspKey;

    @Override
    public Object hook(Executable executable, HookRuntime.MethodCallback callback) {
        try {
            return XposedBridge.hookMethod(executable, wrapCallback(callback));
        } catch (Throwable t) {
            XLog.e("Legacy hook failed for " + executable);
            XLog.e(t);
            return null;
        }
    }

    @Override
    public Set<Object> hookAllMethods(Class<?> clazz, String methodName, HookRuntime.MethodCallback callback) {
        try {
            Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                    clazz, methodName, wrapCallback(callback));
            return unhooks == null ? Collections.emptySet() : new LinkedHashSet<>(unhooks);
        } catch (Throwable t) {
            XLog.e("Legacy hookAllMethods failed for " + clazz.getName() + "#" + methodName);
            XLog.e(t);
            return Collections.emptySet();
        }
    }

    private XC_MethodHook wrapCallback(HookRuntime.MethodCallback callback) {
        return new XC_MethodHook() {
            private final String paramKey = "alipaynfc.legacyParam." + System.identityHashCode(callback);

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                HookRuntime.OriginalInvoker invoker = callArgs ->
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, callArgs);
                HookRuntime.HookParam bridge = new HookRuntime.HookParam(
                        param.method, param.thisObject, param.args, invoker);
                param.setObjectExtra(paramKey, bridge);
                callback.before(bridge);
                if (bridge.hasOutcome()) {
                    if (bridge.getThrowable() != null) {
                        param.setThrowable(bridge.getThrowable());
                    } else {
                        param.setResult(bridge.getResult());
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object saved = param.getObjectExtra(paramKey);
                HookRuntime.HookParam bridge;
                if (saved instanceof HookRuntime.HookParam) {
                    bridge = (HookRuntime.HookParam) saved;
                } else {
                    HookRuntime.OriginalInvoker invoker = callArgs ->
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, callArgs);
                    bridge = new HookRuntime.HookParam(
                            param.method, param.thisObject, param.args, invoker);
                }
                bridge.captureOriginOutcome(param.getResult(), param.getThrowable());
                callback.after(bridge);
                if (bridge.getThrowable() != null) {
                    param.setThrowable(bridge.getThrowable());
                } else {
                    param.setResult(bridge.getResult());
                }
            }
        };
    }

    @Override
    public void log(int priority, String tag, String msg, Throwable t) {
        try {
            if (t != null) {
                XposedBridge.log(t);
            } else {
                XposedBridge.log(tag + ": " + msg);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public SharedPreferences getRemotePreferences(String group) {
        return null;
    }

    @Override
    public synchronized SharedPreferences getLegacyXSharedPreferences(String modulePkg, String prefsName) {
        try {
            String key = modulePkg + "/" + prefsName;
            if (cachedXsp == null || !key.equals(cachedXspKey)) {
                cachedXsp = new XSharedPreferences(modulePkg, prefsName);
                cachedXspKey = key;
            }
            cachedXsp.reload();
            return cachedXsp;
        } catch (Throwable t) {
            Log.e("AlipayNfc", "XSharedPreferences init failed", t);
            return null;
        }
    }
}
