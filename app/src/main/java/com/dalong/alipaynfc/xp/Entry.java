package com.dalong.alipaynfc.xp;

import com.dalong.alipaynfc.C;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy Xposed entry point (LSPosed 1.9.x). */
public class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        HookRuntime.useLegacy();
        String pkg = lpparam.packageName;
        if (pkg == null) return;
        switch (pkg) {
            case C.MODULE_PKG:
                break;
            case C.NFC_PKG:
                NfcHook.init(pkg, lpparam.classLoader);
                break;
            case "android":
                SystemHook.init(lpparam.classLoader);
                break;
            default:
                break;
        }
    }
}
