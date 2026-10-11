package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.Set;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public final class XposedBridge {

    private XposedBridge() {}

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        throw new IllegalStateException("stub");
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> hookClass, String methodName, XC_MethodHook callback) {
        throw new IllegalStateException("stub");
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> hookClass, XC_MethodHook callback) {
        throw new IllegalStateException("stub");
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws Throwable {
        throw new IllegalStateException("stub");
    }

    public static void log(String text) {}

    public static void log(Throwable t) {}
}
