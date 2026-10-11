package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public final class XposedHelpers {

    private XposedHelpers() {}

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        throw new IllegalStateException("stub");
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        throw new IllegalStateException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className, ClassLoader classLoader, String methodName,
            Object... parameterTypesAndCallback) {
        throw new IllegalStateException("stub");
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        throw new IllegalStateException("stub");
    }

    public static Method findMethodExact(
            Class<?> clazz, String methodName, Object... parameterTypes) {
        throw new IllegalStateException("stub");
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw new IllegalStateException("stub");
    }

    public static Object callMethod(Object obj, String methodName, Class<?>[] parameterTypes,
            Object... args) {
        throw new IllegalStateException("stub");
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        throw new IllegalStateException("stub");
    }

    public static Object getObjectField(Object obj, String fieldName) {
        throw new IllegalStateException("stub");
    }

    public static int getIntField(Object obj, String fieldName) {
        throw new IllegalStateException("stub");
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        throw new IllegalStateException("stub");
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        throw new IllegalStateException("stub");
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        throw new IllegalStateException("stub");
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        throw new IllegalStateException("stub");
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        throw new IllegalStateException("stub");
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        throw new IllegalStateException("stub");
    }

    public static void setAdditionalInstanceField(Object obj, String key, Object value) {
        throw new IllegalStateException("stub");
    }
}
