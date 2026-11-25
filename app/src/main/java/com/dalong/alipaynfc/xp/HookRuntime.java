package com.dalong.alipaynfc.xp;

import android.content.SharedPreferences;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dual-runtime hook abstraction supporting both libxposed API 102 and legacy Xposed API.
 * Keeps de.robv.android.xposed.* isolated from shared hook code.
 */
public final class HookRuntime {

    public interface OriginalInvoker {
        Object invoke(Object[] args) throws Throwable;
    }

    public static final class HookParam {
        public final Member member;
        public final Object thisObject;
        public final Object[] args;
        private final OriginalInvoker originalInvoker;
        private boolean hasOutcome;
        private Object result;
        private Throwable throwable;
        private Map<String, Object> extras;

        HookParam(Member member, Object thisObject, Object[] args, OriginalInvoker originalInvoker) {
            this.member = member;
            this.thisObject = thisObject;
            this.args = args;
            this.originalInvoker = originalInvoker;
        }

        public boolean hasOutcome() {
            return hasOutcome;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object value) {
            this.hasOutcome = true;
            this.throwable = null;
            this.result = value;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable t) {
            this.hasOutcome = t != null;
            this.throwable = t;
            if (t != null) {
                this.result = null;
            }
        }

        void captureOriginOutcome(Object value, Throwable err) {
            this.hasOutcome = true;
            this.result = value;
            this.throwable = err;
        }

        public void setObjectExtra(String key, Object value) {
            if (extras == null) extras = new HashMap<>();
            extras.put(key, value);
        }

        public Object getObjectExtra(String key) {
            return extras == null ? null : extras.get(key);
        }

        public Object invokeOriginal() throws Throwable {
            return originalInvoker.invoke(args);
        }
    }

    public static abstract class MethodCallback {
        protected void before(HookParam param) throws Throwable {}
        protected void after(HookParam param) throws Throwable {}
    }

    interface HookBackend {
        Object hook(Executable executable, MethodCallback callback);
        Set<Object> hookAllMethods(Class<?> clazz, String methodName, MethodCallback callback);
        void log(int priority, String tag, String msg, Throwable t);
        SharedPreferences getRemotePreferences(String group);
        SharedPreferences getLegacyXSharedPreferences(String modulePkg, String prefsName);
    }

    private static volatile HookBackend backend;

    private HookRuntime() {}

    public static void useLegacy() {
        if (backend == null) {
            backend = new LegacyHookBackend();
        }
    }

    public static void useModern(Object module, int apiVersion) {
        if (backend instanceof ModernHookBackend) return;
        backend = new ModernHookBackend(module, apiVersion);
    }

    public static boolean isModern() {
        return backend instanceof ModernHookBackend;
    }

    public static Object findAndHookMethod(
            Class<?> clazz, String methodName, Class<?>[] paramTypes, MethodCallback callback) {
        HookBackend b = backend;
        if (b == null) throw new IllegalStateException("HookRuntime not initialized");
        return b.hook(Reflect.findMethod(clazz, methodName, paramTypes), callback);
    }

    public static Set<Object> hookAllMethods(
            Class<?> clazz, String methodName, MethodCallback callback) {
        HookBackend b = backend;
        if (b == null) throw new IllegalStateException("HookRuntime not initialized");
        Set<Object> hooked = b.hookAllMethods(clazz, methodName, callback);
        return hooked == null ? Collections.emptySet() : hooked;
    }

    public static void log(int priority, String tag, String msg, Throwable t) {
        HookBackend b = backend;
        if (b != null) {
            b.log(priority, tag, msg, t);
        }
    }

    public static SharedPreferences getRemotePreferences(String group) {
        HookBackend b = backend;
        return b == null ? null : b.getRemotePreferences(group);
    }

    public static SharedPreferences getLegacyXSharedPreferences(String modulePkg, String prefsName) {
        HookBackend b = backend;
        return b == null ? null : b.getLegacyXSharedPreferences(modulePkg, prefsName);
    }
}
