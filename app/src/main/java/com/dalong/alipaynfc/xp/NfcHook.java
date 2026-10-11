package com.dalong.alipaynfc.xp;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.xp.HookConfig.Cache;

import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Loaded inside com.android.nfc (Nfc_st on Xiaomi).
 *
 * Dispatch flow (verified by decompiling Nfc_st.apk, HyperOS 3 / Android 16):
 *   NfcDispatcher.dispatchTag -> NfcDispatcher$DispatchInfo.tryStartActivity()
 *     -> resolves receivers for the CURRENT user (user 0) -> main Alipay wins
 *     -> context.startActivityAsUser(rootIntent, UserHandle.CURRENT)
 *   rootIntent = NfcRootActivity carrying the real dispatch intent in "launchIntent".
 *   NfcRootActivity.onCreate reads "launchIntentUserHandle" (defaults to CURRENT)
 *   and forwards startActivityAsUser(launchIntent, thatUser).
 *
 * This hook intercepts the outer startActivityAsUser call and rewrites:
 *   - "launchIntentUserHandle" extra + the outer UserHandle argument -> target user
 *     (system app-cloning: same package under a hidden user id, e.g. XSpace 999)
 *   - or "launchIntent" package -> container package (third-party multi-open)
 */
public final class NfcHook {

    private static volatile Context appContext;

    private NfcHook() {}

    public static void init(XC_LoadPackage.LoadPackageParam lpp) {
        XLog.i("loaded in " + lpp.packageName + " sdk=" + android.os.Build.VERSION.SDK_INT);
        installDispatchHook(lpp.classLoader);
        attachApp();
    }

    private static void attachApp() {
        try {
            XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length > 0 && param.args[0] instanceof Context
                                && appContext == null) {
                            appContext = (Context) param.args[0];
                            XLog.i("application attached, context ready");
                            registerConfigListener(appContext);
                            sendAlive(appContext, "com.android.nfc");
                        }
                    } catch (Throwable t) {
                        XLog.e(t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("attachApp hook failed");
            XLog.e(t);
        }
    }

    private static void registerConfigListener(Context ctx) {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(C.ACTION_CONFIG_CHANGED);
            f.addAction(C.ACTION_REQUEST_CLEAR);
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (C.ACTION_CONFIG_CHANGED.equals(action)) {
                        HookConfig.invalidate();
                    } else if (C.ACTION_REQUEST_CLEAR.equals(action)) {
                        GStore.clearRecords(context);
                        XLog.i("records cleared via nfc process");
                    }
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(r, f, android.content.Context.RECEIVER_EXPORTED);
            } else {
                ctx.registerReceiver(r, f);
            }
        } catch (Throwable t) {
            XLog.e("registerConfigListener failed");
            XLog.e(t);
        }
    }

    private static void installDispatchHook(ClassLoader cl) {
        try {
            Class<?> contextImpl = XposedHelpers.findClass("android.app.ContextImpl", cl);
            XposedBridge.hookAllMethods(contextImpl, "startActivityAsUser", redirectHook);
            XLog.i("dispatch hook installed on ContextImpl.startActivityAsUser");
        } catch (Throwable t) {
            XLog.e("installDispatchHook failed");
            XLog.e(t);
        }
    }

    private static final XC_MethodHook redirectHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                Object[] args = param.args;
                if (args == null) return;
                int intentIdx = -1;
                int userIdx = -1;
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof Intent && intentIdx < 0) intentIdx = i;
                    else if (a instanceof UserHandle && userIdx < 0) userIdx = i;
                }
                if (intentIdx < 0) return;
                Intent outer = (Intent) args[intentIdx];

                // Only NfcRootActivity / TechListChooserActivity root intents carry "launchIntent".
                ComponentName cn = outer.getComponent();
                if (cn == null || !C.NFC_PKG.equals(cn.getPackageName())) return;
                Intent inner = outer.getParcelableExtra("launchIntent");
                if (inner == null || !isNfcAction(inner.getAction())) return;

                Context ctx = appContext != null ? appContext
                        : (param.thisObject instanceof Context ? (Context) param.thisObject : null);
                Cache cfg = HookConfig.get(ctx);

                XLog.i("nfc dispatch: action=" + inner.getAction()
                        + " data=" + inner.getDataString()
                        + " type=" + inner.getType()
                        + " pkg=" + inner.getPackage()
                        + " outerUser=" + (userIdx >= 0 ? args[userIdx] : "none")
                        + " cfg=" + cfg.source + " enabled=" + cfg.enabled
                        + " targetUser=" + cfg.targetUser + " targetPkg=" + cfg.targetPkg);

                if (!cfg.enabled) return;
                if (!isAlipayDispatch(inner, cfg)) return;
                if (outer.getBooleanExtra(C.EXTRA_HANDLED, false)) return;

                String from = inner.getComponent() != null
                        ? inner.getComponent().flattenToString() : inner.getDataString();

                if (cfg.targetUser == 0 && TextUtils.isEmpty(cfg.targetPkg)) {
                    outer.putExtra(C.EXTRA_HANDLED, true);
                    record(ctx, inner, cfg, from, "user 0 (default)");
                    return;
                }

                redirect(param, outer, inner, cfg, userIdx, from);
            } catch (Throwable t) {
                XLog.e("redirectHook error");
                XLog.e(t);
            }
        }
    };

    static boolean isNfcAction(String action) {
        return C.ACTION_NDEF.equals(action) || C.ACTION_TAG.equals(action)
                || C.ACTION_TECH.equals(action) || C.ACTION_TRANSACTION.equals(action);
    }

    static boolean isAlipayDispatch(Intent intent, Cache cfg) {
        String alipayPkg = cfg.alipayPkg == null ? C.ALIPAY_PKG : cfg.alipayPkg;
        ComponentName cn = intent.getComponent();
        if (cn != null && alipayPkg.equals(cn.getPackageName())) return true;
        String pkg = intent.getPackage();
        if (pkg != null && alipayPkg.equals(pkg)) return true;
        return looksLikeAlipay(intent.getDataString()) || looksLikeAlipay(intent.getType());
    }

    private static boolean looksLikeAlipay(String s) {
        if (s == null) return false;
        String d = s.toLowerCase();
        return d.startsWith("alipays://") || d.startsWith("alipay:")
                || d.contains("alipay.com") || d.contains("alipay.cn")
                || d.contains("alipayplus.com") || d.contains("alipayobjects")
                || d.contains("macaupass.com") || d.contains("com.alipay")
                || d.startsWith("https://nfc/");
    }

    /**
     * Launches the dispatch intent directly as the target user from the u0 nfc context,
     * bypassing NfcRootActivity. On MIUI clone users (u10/u999), NfcRootActivity's own
     * forward resolves UserHandle.CURRENT to the foreground user (0), so main Alipay
     * would open instead of the clone. Direct launch keeps everything in one hop; the
     * NfcRootActivity extra mechanism stays as fallback.
     */
    private static void redirect(XC_MethodHook.MethodHookParam param, Intent outer, Intent inner,
            Cache cfg, int userIdx, String original) {
        try {
            Intent ni = new Intent(inner);
            ni.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            boolean redirectUser = cfg.targetUser != 0;
            boolean redirectPkg = !TextUtils.isEmpty(cfg.targetPkg);
            if (redirectPkg) {
                ni.setPackage(cfg.targetPkg);
                ni.setComponent(null);
            }
            int targetUser = redirectUser ? cfg.targetUser : 0;
            UserHandle uh = userHandleOf(targetUser);
            String target = redirectPkg
                    ? "package " + cfg.targetPkg + " (user " + targetUser + ")"
                    : "user " + targetUser;
            try {
                // Same ContextImpl the dispatcher uses; NFC dispatch holds an app-switch
                // window, and nfc uid has INTERACT_ACROSS_USERS for clone users.
                XposedHelpers.callMethod(param.thisObject, "startActivityAsUser",
                        ni, null, uh);
                param.setResult(null);
                outer.putExtra(C.EXTRA_HANDLED, true);
                XLog.i("direct-launched: " + original + " -> " + target);
            } catch (Throwable directError) {
                // Fallback: the stock NfcRootActivity extra mechanism.
                outer.putExtra("launchIntent", ni);
                if (redirectUser) {
                    outer.putExtra("launchIntentUserHandle", uh);
                    if (userIdx >= 0) param.args[userIdx] = uh;
                }
                outer.putExtra(C.EXTRA_HANDLED, true);
                XLog.e("direct launch failed (" + directError + "), using rootIntent extras");
            }
            record(appContext, ni, cfg, original, target);
        } catch (Throwable t) {
            XLog.e("redirect failed, falling back to original dispatch");
            XLog.e(t);
        }
    }

    /** UserHandle.of(int) is hidden; fall back to the public constructor. */
    static UserHandle userHandleOf(int userId) {
        try {
            return (UserHandle) XposedHelpers.callStaticMethod(UserHandle.class, "of", userId);
        } catch (Throwable t) {
            try {
                return UserHandle.class.getConstructor(int.class).newInstance(userId);
            } catch (Throwable t2) {
                throw new RuntimeException(t2);
            }
        }
    }

    private static void record(Context ctx, Intent intent, Cache cfg, String from, String target) {
        if (!cfg.keepHistory || ctx == null) return;
        try {
            JSONObject o = new JSONObject();
            o.put("ts", System.currentTimeMillis());
            o.put("uri", intent.getDataString());
            o.put("action", intent.getAction());
            o.put("from", from);
            o.put("to", target);
            o.put("revision", cfg.revision);
            GStore.addRecord(ctx, o.toString());
            Intent bi = new Intent(C.ACTION_RECORD);
            bi.setPackage(C.MODULE_PKG);
            bi.putExtra(C.EXTRA_JSON, o.toString());
            ctx.sendBroadcast(bi);
        } catch (Throwable t) {
            XLog.e("record failed");
            XLog.e(t);
        }
    }

    static void sendAlive(Context ctx, String process) {
        try {
            GStore.markAlive(ctx, process);
            JSONObject o = new JSONObject();
            o.put("process", process);
            Intent bi = new Intent(C.ACTION_HOOK_ALIVE);
            bi.setPackage(C.MODULE_PKG);
            bi.putExtra(C.EXTRA_JSON, o.toString());
            ctx.sendBroadcast(bi);
            XLog.i("alive broadcast sent from " + process);
        } catch (Throwable t) {
            XLog.e("sendAlive failed");
            XLog.e(t);
        }
    }
}
