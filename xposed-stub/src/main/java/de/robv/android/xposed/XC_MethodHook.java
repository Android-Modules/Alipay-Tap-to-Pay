package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public abstract class XC_MethodHook {

    public XC_MethodHook() {}

    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public boolean returnEarly;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }

        public Object getObjectExtra(String key) {
            throw new IllegalStateException("stub");
        }

        public void setObjectExtra(String key, Object value) {
            throw new IllegalStateException("stub");
        }
    }

    public class Unhook {
        public Member getHookedMethod() {
            throw new IllegalStateException("stub");
        }

        public void unhook() {}
    }
}
