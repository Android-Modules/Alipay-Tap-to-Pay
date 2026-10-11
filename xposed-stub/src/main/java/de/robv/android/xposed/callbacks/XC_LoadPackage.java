package de.robv.android.xposed.callbacks;

/** Compile-time stub. The real implementation is provided by the Xposed framework at runtime. */
public abstract class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public android.content.pm.ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }
}
