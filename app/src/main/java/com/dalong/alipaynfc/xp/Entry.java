package com.dalong.alipaynfc.xp;

import com.dalong.alipaynfc.C;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Xposed entry point. */
public class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (pkg == null) return;
        switch (pkg) {
            case C.MODULE_PKG:
                // The module app itself: nothing to hook.
                break;
            case "com.android.nfc":
                NfcHook.init(lpparam);
                break;
            case "android":
                SystemHook.init(lpparam);
                break;
            default:
                break;
        }
    }
}
