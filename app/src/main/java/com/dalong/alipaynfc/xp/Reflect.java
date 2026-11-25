package com.dalong.alipaynfc.xp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Pure-Java reflection utilities replacing legacy XposedHelpers. */
public final class Reflect {

    private Reflect() {}

    public static Class<?> findClass(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Class<?> findClassOrNull(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Method m = cur.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new RuntimeException(new NoSuchMethodException(cls.getName() + "#" + name));
    }

    public static Object call(Object target, String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = findMethod(target.getClass(), name, paramTypes);
            return m.invoke(target, args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object callStatic(Class<?> cls, String name, Class<?>[] paramTypes, Object... args) {
        try {
            Method m = findMethod(cls, name, paramTypes);
            return m.invoke(null, args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static <T> T newInstance(Class<T> cls, Class<?>[] paramTypes, Object... args) {
        try {
            Constructor<T> ctor = cls.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        throw new RuntimeException(new NoSuchFieldException(cls.getName() + "." + name));
    }

    public static Object getField(Object target, String name) {
        try {
            return findField(target.getClass(), name).get(target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int getIntField(Object target, String name) {
        try {
            return findField(target.getClass(), name).getInt(target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static boolean getBooleanField(Object target, String name) {
        try {
            return findField(target.getClass(), name).getBoolean(target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
