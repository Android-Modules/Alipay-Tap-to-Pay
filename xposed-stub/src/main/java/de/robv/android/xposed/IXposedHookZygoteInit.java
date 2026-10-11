package de.robv.android.xposed;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public interface IXposedHookZygoteInit {

    void initZygote(StartupParam startupParam) throws Throwable;

    class StartupParam {
        public boolean startsystemserver;
        public String modulePath;
    }
}
