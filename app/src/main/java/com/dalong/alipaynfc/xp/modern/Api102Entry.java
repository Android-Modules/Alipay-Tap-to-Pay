package com.dalong.alipaynfc.xp.modern;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.xp.HookRuntime;
import com.dalong.alipaynfc.xp.NfcHook;
import com.dalong.alipaynfc.xp.SystemHook;
import com.dalong.alipaynfc.xp.XLog;

import io.github.libxposed.api.XposedModule;

/** Modern libxposed API 102 entry point (LSPosed 2.1/2.2+). */
public class Api102Entry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        HookRuntime.useModern(this, getApiVersion());
        XLog.i("modern module loaded api=" + getApiVersion()
                + " process=" + param.getProcessName()
                + " system=" + param.isSystemServer());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        HookRuntime.useModern(this, getApiVersion());
        SystemHook.init(param.getClassLoader());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        HookRuntime.useModern(this, getApiVersion());
        String pkg = param.getPackageName();
        if (pkg == null) return;
        if (C.NFC_PKG.equals(pkg)) {
            NfcHook.init(pkg, param.getClassLoader());
        } else if ("android".equals(pkg)) {
            SystemHook.init(param.getClassLoader());
        }
    }
}
