package com.dalong.alipaynfc.xp;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * libxposed API 102 backend. All XposedInterface calls are strictly typed so
 * LSPosed's runtime API call protection (PROP_RT_API_PROTECTION) rewrites them cleanly.
 */
final class ModernHookBackend implements HookRuntime.HookBackend {

    private static final String TAG = "AlipayNfc";
    private final XposedInterface api;

    ModernHookBackend(Object module, int apiVersion) {
        if (apiVersion < XposedInterface.API_102) {
            throw new IllegalStateException("Modern runtime requires libxposed API 102+, got " + apiVersion);
        }
        if (!(module instanceof XposedInterface)) {
            throw new IllegalArgumentException("Module is not an XposedInterface: " + module);
        }
        this.api = (XposedInterface) module;
        logFrameworkInfo(apiVersion);
    }

    private void logFrameworkInfo(int apiVersion) {
        try {
            long props = api.getFrameworkProperties();
            boolean protectedApi = (props & XposedInterface.PROP_RT_API_PROTECTION) != 0L;
            String info = "framework=" + api.getFrameworkName()
                    + " version=" + api.getFrameworkVersion()
                    + " code=" + api.getFrameworkVersionCode()
                    + " api=" + apiVersion
                    + " props=" + props
                    + " apiProtection=" + protectedApi;
            Log.i(TAG, info);
            api.log(Log.INFO, TAG, info);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public Object hook(Executable executable, HookRuntime.MethodCallback callback) {
        try {
            XposedInterface.Hooker hooker = chain -> intercept(executable, callback, chain);
            return api.hook(executable).intercept(hooker);
        } catch (Throwable t) {
            XLog.e("Modern hook failed for " + executable);
            XLog.e(t);
            return null;
        }
    }

    private Object intercept(
            Executable executable,
            HookRuntime.MethodCallback callback,
            XposedInterface.Chain chain) throws Throwable {
        Object thisObject = null;
        try {
            thisObject = chain.getThisObject();
        } catch (Throwable ignored) {
            // Static methods reject getThisObject().
        }
        Object[] args;
        try {
            List<Object> argList = chain.getArgs();
            args = argList == null ? new Object[0] : argList.toArray(new Object[0]);
        } catch (Throwable ignored) {
            args = new Object[0];
        }

        final Object targetThis = thisObject;
        HookRuntime.OriginalInvoker invoker = callArgs -> invokeOrigin(executable, targetThis, callArgs);
        HookRuntime.HookParam param = new HookRuntime.HookParam(executable, targetThis, args, invoker);

        try {
            callback.before(param);
        } catch (Throwable t) {
            XLog.e("before hook threw in " + executable.getName());
            XLog.e(t);
        }

        if (!param.hasOutcome()) {
            try {
                Object res = chain.proceed(param.args);
                param.captureOriginOutcome(res, null);
            } catch (Throwable t) {
                param.captureOriginOutcome(null, t);
            }
        }

        try {
            callback.after(param);
        } catch (Throwable t) {
            XLog.e("after hook threw in " + executable.getName());
            XLog.e(t);
        }

        Throwable err = param.getThrowable();
        if (err != null) {
            throw err;
        }
        return param.getResult();
    }

    private Object invokeOrigin(Executable executable, Object thisObject, Object[] args) throws Throwable {
        Object[] safeArgs = args == null ? new Object[0] : args;
        if (executable instanceof Method) {
            return api.getInvoker((Method) executable)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .invoke(thisObject, safeArgs);
        }
        if (executable instanceof Constructor<?>) {
            return api.getInvoker((Constructor<?>) executable)
                    .setType(XposedInterface.Invoker.Type.ORIGIN)
                    .newInstance(safeArgs);
        }
        return null;
    }

    @Override
    public Set<Object> hookAllMethods(Class<?> clazz, String methodName, HookRuntime.MethodCallback callback) {
        Set<Object> handles = new LinkedHashSet<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (methodName != null && !methodName.equals(m.getName())) continue;
            if (Modifier.isAbstract(m.getModifiers())) continue;
            Object handle = hook(m, callback);
            if (handle != null) {
                handles.add(handle);
            }
        }
        return handles;
    }

    @Override
    public void log(int priority, String tag, String msg, Throwable t) {
        try {
            if (t != null) {
                api.log(priority, tag, msg == null ? "" : msg, t);
            } else {
                api.log(priority, tag, msg == null ? "" : msg);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public SharedPreferences getRemotePreferences(String group) {
        try {
            return api.getRemotePreferences(group);
        } catch (Throwable t) {
            XLog.e("getRemotePreferences(" + group + ") failed");
            XLog.e(t);
            return null;
        }
    }

    @Override
    public SharedPreferences getLegacyXSharedPreferences(String modulePkg, String prefsName) {
        return null;
    }
}
