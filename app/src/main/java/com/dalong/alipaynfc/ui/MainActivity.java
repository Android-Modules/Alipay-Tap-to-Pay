package com.dalong.alipaynfc.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.R;
import com.dalong.alipaynfc.data.FrameworkState;
import com.dalong.alipaynfc.data.Store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main configuration screen: module status, Alipay instance selection,
 * redirect toggles, dispatch history, advanced overrides, and About entry.
 */
public class MainActivity extends Activity {

    private static final class UserChoice {
        final int userId;
        final String title;
        final String subtitle;

        UserChoice(int userId, String title, String subtitle) {
            this.userId = userId;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private LinearLayout statusCard;
    private TextView statusHeadline;
    private TextView statusVersion;
    private LinearLayout statusChipRow;
    private TextView statusHint;

    private LinearLayout usersListBox;
    private TextView usersHint;

    private Switch enableSwitch;
    private Switch historySwitch;

    private TextView recordsCountText;
    private LinearLayout recordsBox;
    private TextView recordsEmpty;

    private TextView advancedSummary;
    private TextView advancedToggleText;
    private LinearLayout advancedBody;
    private Switch pkgModeSwitch;
    private EditText pkgInput;
    private EditText userInput;

    private TextView helpToggleText;
    private TextView helpBody;

    private BroadcastReceiver receiver;
    private ContentObserver contentObserver;
    private final FrameworkState.Listener fwListener = this::renderStatus;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);

    private final List<UserChoice> userChoices = new ArrayList<>();
    private final List<View> choiceDots = new ArrayList<>();
    private final Set<Integer> scannedUserIds = new HashSet<>();
    private int selectedUserId = 0;
    private boolean isBinding = false;
    private boolean advancedExpanded = false;
    private boolean helpExpanded = false;
    private boolean initialExpansionApplied = false;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        UiKit.applySystemBars(this);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (C.ACTION_SCAN_RESULT.equals(action) || C.ACTION_HOOK_ALIVE.equals(action)) {
                    runOnUiThread(MainActivity.this::refreshAll);
                } else if (C.ACTION_RECORD.equals(action)) {
                    runOnUiThread(MainActivity.this::renderRecords);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(C.ACTION_SCAN_RESULT);
        filter.addAction(C.ACTION_HOOK_ALIVE);
        filter.addAction(C.ACTION_RECORD);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        // Hooks publish state via Settings.Global; observe changes for live UI refresh.
        contentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                runOnUiThread(MainActivity.this::refreshAll);
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(C.GKEY_ALIVE), true, contentObserver);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(C.GKEY_USERS), true, contentObserver);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(C.GKEY_RECORDS), true, contentObserver);

        refreshAll();
        requestScan();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FrameworkState.addListener(fwListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        requestScan();
    }

    @Override
    protected void onStop() {
        FrameworkState.removeListener(fwListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }
        if (contentObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(contentObserver);
            } catch (Exception ignored) {
            }
            contentObserver = null;
        }
    }

    private void requestScan() {
        try {
            sendBroadcast(new Intent(C.ACTION_REQUEST_SCAN));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- persistence

    private void persist() {
        if (isBinding) return;
        Store.Config c = new Store.Config();
        c.enabled = enableSwitch.isChecked();
        c.targetUser = currentSelectionUserId();
        c.targetPkg = pkgModeSwitch.isChecked() ? pkgInput.getText().toString().trim() : "";
        c.keepHistory = historySwitch.isChecked();
        selectedUserId = c.targetUser;
        updateChoiceIndicators();
        Store.saveConfig(this, c);
        updateAdvancedSummary(c);
        try {
            sendBroadcast(new Intent(C.ACTION_CONFIG_CHANGED));
        } catch (Exception ignored) {
        }
    }

    private int currentSelectionUserId() {
        if (userInput != null) {
            String manual = userInput.getText().toString().trim();
            if (!manual.isEmpty()) {
                try {
                    return Integer.parseInt(manual);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return selectedUserId;
    }

    // ---------------------------------------------------------------- rendering

    private void refreshAll() {
        renderUsers();
        renderStatus();
        renderRecords();
        renderSwitches();
    }

    private void renderStatus() {
        UiKit.AliveStatus st = UiKit.readAliveStatus(this);
        statusVersion.setText("v" + UiKit.getAppVersionName(this));

        int bgColor = UiKit.color(this, st.allActive ? R.color.bg_success_soft : R.color.bg_warn_soft);
        statusCard.setBackground(UiKit.roundedRect(bgColor, UiKit.dpF(this, 20)));

        if (st.allActive) {
            statusHeadline.setText("模块已激活");
            statusHeadline.setTextColor(UiKit.color(this, R.color.status_ok));
            statusHint.setText("配置即时生效，触碰商家「碰一下」设备即可按所选实例响应。");
        } else if (FrameworkState.isBound() && FrameworkState.hasNfcScope()) {
            statusHeadline.setText("等待 NFC 服务加载");
            statusHeadline.setTextColor(UiKit.color(this, R.color.status_warn));
            statusHint.setText("LSPosed 作用域已就绪，请开启手机 NFC（或开关一次 NFC / 重启手机）以生效。");
        } else {
            statusHeadline.setText("未检测到模块注入");
            statusHeadline.setTextColor(UiKit.color(this, R.color.status_warn));
            statusHint.setText("请在 LSPosed 中启用本模块并勾选「NFC」(com.android.nfc)，再开关一次 NFC 或重启手机。");
        }

        statusChipRow.removeAllViews();
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMarginEnd(UiKit.dp(this, 8));
        statusChipRow.addView(UiKit.statusChip(this, "NFC 服务", st.nfcOk), chipLp);
        if (FrameworkState.isBound()) {
            LinearLayout.LayoutParams chipLp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLp2.setMarginEnd(UiKit.dp(this, 8));
            statusChipRow.addView(UiKit.statusChip(this, "实例扫描", st.scanOk), chipLp2);
            statusChipRow.addView(UiKit.statusChip(this, "API 102", true));
        } else {
            statusChipRow.addView(UiKit.statusChip(this, "实例扫描", st.scanOk));
        }

        if (!initialExpansionApplied) {
            initialExpansionApplied = true;
            setHelpExpanded(!st.allActive);
        }
    }

    private void renderSwitches() {
        isBinding = true;
        try {
            Store.Config c = Store.loadConfig(this);
            enableSwitch.setChecked(c.enabled);
            historySwitch.setChecked(c.keepHistory);

            boolean pkgMode = !c.targetPkg.isEmpty();
            pkgModeSwitch.setChecked(pkgMode);
            if (!pkgInput.hasFocus() && !pkgInput.getText().toString().equals(c.targetPkg)) {
                pkgInput.setText(c.targetPkg);
            }
            pkgInput.setVisibility(pkgMode ? View.VISIBLE : View.GONE);

            boolean known = scannedUserIds.contains(c.targetUser);
            selectedUserId = c.targetUser;
            if (!userInput.hasFocus()) {
                if (known) {
                    if (!userInput.getText().toString().isEmpty()) {
                        userInput.setText("");
                    }
                } else if (c.targetUser != 0) {
                    String val = String.valueOf(c.targetUser);
                    if (!userInput.getText().toString().equals(val)) {
                        userInput.setText(val);
                    }
                } else if (!userInput.getText().toString().isEmpty()) {
                    userInput.setText("");
                }
            }
            updateChoiceIndicators();
            updateAdvancedSummary(c);

            if ((pkgMode || (!known && c.targetUser != 0)) && !advancedExpanded) {
                setAdvancedExpanded(true);
            }
        } finally {
            isBinding = false;
        }
    }

    private void renderUsers() {
        try {
            usersListBox.removeAllViews();
            userChoices.clear();
            choiceDots.clear();
            scannedUserIds.clear();

            scannedUserIds.add(0);
            userChoices.add(new UserChoice(0, "主支付宝", "默认实例 · 用户 0"));

            String json = Store.getUsers(this);
            if (!json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                JSONArray users = root.optJSONArray("users");
                if (users != null) {
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        int id = u.optInt("id", -1);
                        if (id == 0) continue;
                        if (!u.optBoolean("alipay", false)) continue;
                        scannedUserIds.add(id);
                        String name = u.optString("name", "");
                        if (id == 999) {
                            userChoices.add(new UserChoice(id, "多开支付宝", "系统分身 · 用户 999"));
                        } else {
                            String title = name.isEmpty() ? "支付宝实例" : name;
                            userChoices.add(new UserChoice(id, title, "Android 用户 " + id));
                        }
                    }
                }
            }

            Store.Config cur = Store.loadConfig(this);
            boolean known = scannedUserIds.contains(cur.targetUser);
            if (!known && cur.targetUser != 0) {
                userChoices.add(new UserChoice(
                        cur.targetUser,
                        cur.targetUser == 999 ? "多开支付宝" : "自定义支付宝实例",
                        "手动指定 · 用户 " + cur.targetUser));
            }

            selectedUserId = cur.targetUser;
            for (int i = 0; i < userChoices.size(); i++) {
                if (i > 0) {
                    usersListBox.addView(UiKit.divider(this, 16));
                }
                usersListBox.addView(buildChoiceRow(userChoices.get(i)));
            }
            updateChoiceIndicators();

            boolean scanned = !json.isEmpty();
            if (scanned && userChoices.size() > 1) {
                usersHint.setVisibility(View.GONE);
            } else {
                usersHint.setVisibility(View.VISIBLE);
                usersHint.setText(scanned
                        ? "未扫描到其他用户空间中的支付宝。如使用第三方多开容器，请展开下方「高级设置」。"
                        : "重启手机后自动扫描分身实例，或点击右上角「重新扫描」。");
            }
        } catch (Exception e) {
            usersHint.setVisibility(View.VISIBLE);
            usersHint.setText("解析扫描结果失败：" + e.getMessage());
        }
    }

    private View buildChoiceRow(UserChoice choice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(UiKit.rippleBg(this, Color.TRANSPARENT, 0));
        row.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 13),
                UiKit.dp(this, 16), UiKit.dp(this, 13));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(choice.title);
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(UiKit.color(this, R.color.text_primary));
        textCol.addView(title);

        TextView sub = new TextView(this);
        sub.setText(choice.subtitle);
        sub.setTextSize(12.5f);
        sub.setTextColor(UiKit.color(this, R.color.text_secondary));
        sub.setPadding(0, UiKit.dp(this, 2), 0, 0);
        textCol.addView(sub);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View dot = new View(this);
        int size = UiKit.dp(this, 20);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(size, size);
        dotLp.setMarginStart(UiKit.dp(this, 12));
        row.addView(dot, dotLp);
        choiceDots.add(dot);

        row.setOnClickListener(v -> {
            selectedUserId = choice.userId;
            isBinding = true;
            try {
                if (userInput != null && !userInput.getText().toString().isEmpty()) {
                    userInput.setText("");
                }
            } finally {
                isBinding = false;
            }
            updateChoiceIndicators();
            persist();
        });
        return row;
    }

    private void updateChoiceIndicators() {
        for (int i = 0; i < userChoices.size() && i < choiceDots.size(); i++) {
            boolean checked = userChoices.get(i).userId == selectedUserId;
            styleRadioDot(choiceDots.get(i), checked);
        }
    }

    private void styleRadioDot(View dot, boolean checked) {
        if (checked) {
            int brand = UiKit.color(this, R.color.brand_primary);
            GradientDrawable outer = new GradientDrawable();
            outer.setShape(GradientDrawable.OVAL);
            outer.setColor(Color.TRANSPARENT);
            outer.setStroke(UiKit.dp(this, 2), brand);

            GradientDrawable inner = new GradientDrawable();
            inner.setShape(GradientDrawable.OVAL);
            inner.setColor(brand);

            LayerDrawable layered = new LayerDrawable(new GradientDrawable[]{outer, inner});
            int inset = UiKit.dp(this, 5);
            layered.setLayerInset(1, inset, inset, inset, inset);
            dot.setBackground(layered);
        } else {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(Math.max(2, UiKit.dp(this, 1)), UiKit.color(this, R.color.text_tertiary));
            dot.setBackground(ring);
        }
    }

    private void renderRecords() {
        recordsBox.removeAllViews();
        JSONArray records = Store.getRecords(this);
        int count = records.length();
        recordsCountText.setText(count > 0 ? " (" + count + ")" : "");
        recordsEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < count; i++) {
            try {
                JSONObject r = records.getJSONObject(i);
                if (i > 0) {
                    recordsBox.addView(UiKit.divider(this, 16));
                }
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 11),
                        UiKit.dp(this, 16), UiKit.dp(this, 11));

                TextView head = new TextView(this);
                head.setText(timeFmt.format(new Date(r.optLong("ts")))
                        + "  →  " + r.optString("to", "?"));
                head.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                head.setTextSize(13.5f);
                head.setTextColor(UiKit.color(this, R.color.text_primary));
                row.addView(head);

                String from = r.optString("from", "");
                String uri = r.optString("uri", "");
                String detail = (from == null || from.isEmpty()) ? uri : from;
                if (detail != null && !detail.isEmpty()) {
                    TextView sub = new TextView(this);
                    sub.setText(detail.length() > 120 ? detail.substring(0, 120) + "…" : detail);
                    sub.setTextSize(12);
                    sub.setTextColor(UiKit.color(this, R.color.text_secondary));
                    sub.setPadding(0, UiKit.dp(this, 2), 0, 0);
                    row.addView(sub);
                }
                recordsBox.addView(row);
            } catch (Exception ignored) {
            }
        }
    }

    private void updateAdvancedSummary(Store.Config c) {
        if (advancedSummary == null) return;
        boolean hasPkg = c.targetPkg != null && !c.targetPkg.isEmpty();
        boolean hasManualUser = userInput != null && !userInput.getText().toString().trim().isEmpty();
        if (hasPkg && hasManualUser) {
            advancedSummary.setText("已启用包名重定向（" + c.targetPkg + "）· 用户 " + c.targetUser);
        } else if (hasPkg) {
            advancedSummary.setText("已启用包名重定向：" + c.targetPkg);
        } else if (hasManualUser) {
            advancedSummary.setText("已手动指定用户 ID：" + c.targetUser);
        } else {
            advancedSummary.setText("包名重定向与手动指定用户 ID（系统分身无需开启）");
        }
    }

    private void setAdvancedExpanded(boolean expanded) {
        advancedExpanded = expanded;
        advancedBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        advancedToggleText.setText(expanded ? "收起 ▴" : "展开 ▾");
    }

    private void setHelpExpanded(boolean expanded) {
        helpExpanded = expanded;
        helpBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
        helpToggleText.setText(expanded ? "收起 ▴" : "展开 ▾");
    }

    // ---------------------------------------------------------------- UI builder

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(UiKit.color(this, R.color.bg_page));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 28));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Header matching ppx MainActivity top bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UiKit.dp(this, 4), UiKit.dp(this, 2),
                UiKit.dp(this, 4), UiKit.dp(this, 12));

        header.addView(UiKit.appIconBadge(this, 44, 14));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.setMarginStart(UiKit.dp(this, 12));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(UiKit.color(this, R.color.text_primary));
        titleCol.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("自定义 NFC 碰一碰响应的支付宝");
        subtitle.setTextSize(12.5f);
        subtitle.setTextColor(UiKit.color(this, R.color.text_secondary));
        subtitle.setPadding(0, UiKit.dp(this, 1), 0, 0);
        titleCol.addView(subtitle);

        header.addView(titleCol, titleColLp);

        TextView aboutBtn = UiKit.pillButton(this, "关于",
                v -> startActivity(new Intent(this, AboutActivity.class)));
        header.addView(aboutBtn);
        root.addView(header);

        // 2. Hero status card
        statusCard = UiKit.card(this, UiKit.color(this, R.color.bg_warn_soft), 20);
        statusCard.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 16),
                UiKit.dp(this, 18), UiKit.dp(this, 16));

        LinearLayout statusTop = new LinearLayout(this);
        statusTop.setOrientation(LinearLayout.HORIZONTAL);
        statusTop.setGravity(Gravity.CENTER_VERTICAL);

        statusHeadline = new TextView(this);
        statusHeadline.setTextSize(17);
        statusHeadline.setTypeface(Typeface.DEFAULT_BOLD);
        statusTop.addView(statusHeadline, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusVersion = new TextView(this);
        statusVersion.setTextSize(12);
        statusVersion.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusVersion.setTextColor(UiKit.color(this, R.color.text_secondary));
        statusTop.addView(statusVersion);
        statusCard.addView(statusTop);

        statusChipRow = new LinearLayout(this);
        statusChipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = UiKit.dp(this, 10);
        statusCard.addView(statusChipRow, chipsLp);

        statusHint = new TextView(this);
        statusHint.setTextSize(12.5f);
        statusHint.setLineSpacing(UiKit.dpF(this, 2), 1f);
        statusHint.setTextColor(UiKit.color(this, R.color.text_secondary));
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = UiKit.dp(this, 10);
        statusCard.addView(statusHint, hintLp);

        root.addView(statusCard);

        // 3. Target selection
        root.addView(UiKit.sectionLabel(this, "碰一碰目标"), sectionLp());
        LinearLayout targetCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);

        LinearLayout targetHeader = new LinearLayout(this);
        targetHeader.setOrientation(LinearLayout.HORIZONTAL);
        targetHeader.setGravity(Gravity.CENTER_VERTICAL);
        targetHeader.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 12),
                UiKit.dp(this, 10), UiKit.dp(this, 10));

        TextView targetSub = new TextView(this);
        targetSub.setText("选择由哪个支付宝响应，修改即时生效");
        targetSub.setTextSize(13);
        targetSub.setTextColor(UiKit.color(this, R.color.text_secondary));
        targetHeader.addView(targetSub, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView rescanBtn = UiKit.textActionButton(this, "重新扫描", v -> {
            usersHint.setVisibility(View.VISIBLE);
            usersHint.setText("正在扫描支付宝实例…");
            requestScan();
        });
        targetHeader.addView(rescanBtn);
        targetCard.addView(targetHeader);
        targetCard.addView(UiKit.divider(this, 16));

        usersListBox = new LinearLayout(this);
        usersListBox.setOrientation(LinearLayout.VERTICAL);
        targetCard.addView(usersListBox);

        usersHint = new TextView(this);
        usersHint.setText("正在扫描支付宝实例…");
        usersHint.setTextSize(12.5f);
        usersHint.setLineSpacing(UiKit.dpF(this, 2), 1f);
        usersHint.setTextColor(UiKit.color(this, R.color.text_secondary));
        usersHint.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8),
                UiKit.dp(this, 16), UiKit.dp(this, 14));
        targetCard.addView(usersHint);
        root.addView(targetCard, cardLp());

        // 4. General switches
        root.addView(UiKit.sectionLabel(this, "常规设置"), sectionLp());
        LinearLayout switchCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        enableSwitch = addSwitchRow(switchCard,
                "启用碰一碰重定向",
                "关闭后 NFC 派发恢复系统默认行为");
        switchCard.addView(UiKit.divider(this, 16));
        historySwitch = addSwitchRow(switchCard,
                "保存碰一碰记录",
                "记录最近 30 条 NFC 派发时间与去向");
        root.addView(switchCard, cardLp());

        // 5. Dispatch records
        root.addView(UiKit.sectionLabel(this, "派发记录"), sectionLp());
        LinearLayout recCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);

        LinearLayout recHeader = new LinearLayout(this);
        recHeader.setOrientation(LinearLayout.HORIZONTAL);
        recHeader.setGravity(Gravity.CENTER_VERTICAL);
        recHeader.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 10),
                UiKit.dp(this, 10), UiKit.dp(this, 10));

        LinearLayout recTitleWrap = new LinearLayout(this);
        recTitleWrap.setOrientation(LinearLayout.HORIZONTAL);
        recTitleWrap.setGravity(Gravity.CENTER_VERTICAL);

        TextView recTitle = new TextView(this);
        recTitle.setText("最近记录");
        recTitle.setTextSize(15.5f);
        recTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        recTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        recTitleWrap.addView(recTitle);

        recordsCountText = new TextView(this);
        recordsCountText.setTextSize(13);
        recordsCountText.setTextColor(UiKit.color(this, R.color.text_secondary));
        recTitleWrap.addView(recordsCountText);

        recHeader.addView(recTitleWrap, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearBtn = UiKit.textActionButton(this, "清空", v -> {
            Store.clearRecords(MainActivity.this);
            renderRecords();
        });
        recHeader.addView(clearBtn);
        recCard.addView(recHeader);
        recCard.addView(UiKit.divider(this, 16));

        recordsBox = new LinearLayout(this);
        recordsBox.setOrientation(LinearLayout.VERTICAL);
        recCard.addView(recordsBox);

        recordsEmpty = new TextView(this);
        recordsEmpty.setText("暂无记录。碰一次商家碰一碰设备后这里会出现派发日志。");
        recordsEmpty.setTextSize(13);
        recordsEmpty.setTextColor(UiKit.color(this, R.color.text_secondary));
        recordsEmpty.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 16));
        recCard.addView(recordsEmpty);
        root.addView(recCard, cardLp());

        // 6. Collapsible advanced settings
        root.addView(UiKit.sectionLabel(this, "高级设置"), sectionLp());
        LinearLayout advCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);

        LinearLayout advHeader = new LinearLayout(this);
        advHeader.setOrientation(LinearLayout.HORIZONTAL);
        advHeader.setGravity(Gravity.CENTER_VERTICAL);
        advHeader.setClickable(true);
        advHeader.setFocusable(true);
        advHeader.setBackground(UiKit.rippleBg(this, Color.TRANSPARENT, 0));
        advHeader.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 14));

        LinearLayout advTitleCol = new LinearLayout(this);
        advTitleCol.setOrientation(LinearLayout.VERTICAL);

        TextView advTitle = new TextView(this);
        advTitle.setText("包名重定向与用户 ID");
        advTitle.setTextSize(15.5f);
        advTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        advTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        advTitleCol.addView(advTitle);

        advancedSummary = new TextView(this);
        advancedSummary.setText("包名重定向与手动指定用户 ID（系统分身无需开启）");
        advancedSummary.setTextSize(12.5f);
        advancedSummary.setTextColor(UiKit.color(this, R.color.text_secondary));
        advancedSummary.setPadding(0, UiKit.dp(this, 2), 0, 0);
        advTitleCol.addView(advancedSummary);

        advHeader.addView(advTitleCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        advancedToggleText = new TextView(this);
        advancedToggleText.setText("展开 ▾");
        advancedToggleText.setTextSize(13);
        advancedToggleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        advancedToggleText.setTextColor(UiKit.color(this, R.color.brand_primary));
        advancedToggleText.setPadding(UiKit.dp(this, 8), 0, 0, 0);
        advHeader.addView(advancedToggleText);
        advHeader.setOnClickListener(v -> setAdvancedExpanded(!advancedExpanded));
        advCard.addView(advHeader);

        advancedBody = new LinearLayout(this);
        advancedBody.setOrientation(LinearLayout.VERTICAL);
        advancedBody.setVisibility(View.GONE);
        advancedBody.addView(UiKit.divider(this, 16));

        pkgModeSwitch = addSwitchRow(advancedBody,
                "包名重定向模式",
                "适用于第三方多开容器（如虚拟机类 App）；系统级双开无需开启此项");
        pkgModeSwitch.setOnCheckedChangeListener((b, checked) -> {
            pkgInput.setVisibility(checked ? View.VISIBLE : View.GONE);
            persist();
        });

        pkgInput = UiKit.styledInput(this, "容器宿主包名，例如 com.xxx.parallel", InputType.TYPE_CLASS_TEXT);
        pkgInput.setVisibility(View.GONE);
        pkgInput.addTextChangedListener(simpleWatcher());
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pkgLp.setMargins(UiKit.dp(this, 16), 0, UiKit.dp(this, 16), UiKit.dp(this, 14));
        advancedBody.addView(pkgInput, pkgLp);

        advancedBody.addView(UiKit.divider(this, 16));

        LinearLayout manualBox = new LinearLayout(this);
        manualBox.setOrientation(LinearLayout.VERTICAL);
        manualBox.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 16));

        TextView manualTitle = new TextView(this);
        manualTitle.setText("手动指定用户 ID（可选）");
        manualTitle.setTextSize(15.5f);
        manualTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        manualTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        manualBox.addView(manualTitle);

        TextView manualDesc = new TextView(this);
        manualDesc.setText("直接填写支付宝分身所在的 Android 用户 ID（如小米分身 999），填写后覆盖上方单选。");
        manualDesc.setTextSize(12.5f);
        manualDesc.setLineSpacing(UiKit.dpF(this, 2), 1f);
        manualDesc.setTextColor(UiKit.color(this, R.color.text_secondary));
        manualDesc.setPadding(0, UiKit.dp(this, 3), 0, UiKit.dp(this, 10));
        manualBox.addView(manualDesc);

        userInput = UiKit.styledInput(this, "例如 999（留空使用上方单选）", InputType.TYPE_CLASS_NUMBER);
        userInput.addTextChangedListener(simpleWatcher());
        manualBox.addView(userInput);

        advancedBody.addView(manualBox);
        advCard.addView(advancedBody);
        root.addView(advCard, cardLp());

        // 7. Help & About section
        root.addView(UiKit.sectionLabel(this, "帮助与关于"), sectionLp());

        LinearLayout helpCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        LinearLayout helpHeader = new LinearLayout(this);
        helpHeader.setOrientation(LinearLayout.HORIZONTAL);
        helpHeader.setGravity(Gravity.CENTER_VERTICAL);
        helpHeader.setClickable(true);
        helpHeader.setFocusable(true);
        helpHeader.setBackground(UiKit.rippleBg(this, Color.TRANSPARENT, 0));
        helpHeader.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 14),
                UiKit.dp(this, 16), UiKit.dp(this, 14));

        TextView helpTitle = new TextView(this);
        helpTitle.setText("使用说明");
        helpTitle.setTextSize(15.5f);
        helpTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        helpTitle.setTextColor(UiKit.color(this, R.color.text_primary));
        helpHeader.addView(helpTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        helpToggleText = new TextView(this);
        helpToggleText.setText("展开 ▾");
        helpToggleText.setTextSize(13);
        helpToggleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        helpToggleText.setTextColor(UiKit.color(this, R.color.brand_primary));
        helpHeader.addView(helpToggleText);
        helpHeader.setOnClickListener(v -> setHelpExpanded(!helpExpanded));
        helpCard.addView(helpHeader);

        helpBody = new TextView(this);
        helpBody.setText(
                "1. 在 LSPosed 管理器中启用本模块，作用域勾选「NFC」(com.android.nfc)，开关一次 NFC 或重启手机。\n"
                        + "2. 开启手机 NFC 并解锁屏幕，触碰商家「碰一下」设备即可验证。\n"
                        + "3. 切换目标后即时生效；派发记录可确认每次触发的实际去向。\n"
                        + "4. 若切到分身后无反应，请确认分身支付宝已登录并已开通碰一碰功能。");
        helpBody.setTextSize(13);
        helpBody.setLineSpacing(UiKit.dpF(this, 3), 1f);
        helpBody.setTextColor(UiKit.color(this, R.color.text_secondary));
        helpBody.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 2),
                UiKit.dp(this, 16), UiKit.dp(this, 16));
        helpBody.setVisibility(View.GONE);
        helpCard.addView(helpBody);
        root.addView(helpCard, cardLp());

        LinearLayout aboutCard = UiKit.card(this, UiKit.color(this, R.color.bg_card), 20);
        aboutCard.addView(UiKit.linkRow(
                this,
                "关于我们",
                "酷安 · GitHub · 更多软件",
                v -> startActivity(new Intent(this, AboutActivity.class))));
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aboutLp.topMargin = UiKit.dp(this, 10);
        root.addView(aboutCard, aboutLp);

        return scroll;
    }

    private Switch addSwitchRow(LinearLayout parent, String titleText, String descText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(UiKit.rippleBg(this, Color.TRANSPARENT, 0));
        row.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 13),
                UiKit.dp(this, 16), UiKit.dp(this, 13));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(UiKit.color(this, R.color.text_primary));
        textCol.addView(title);

        TextView desc = new TextView(this);
        desc.setText(descText);
        desc.setTextSize(12.5f);
        desc.setLineSpacing(UiKit.dpF(this, 2), 1f);
        desc.setTextColor(UiKit.color(this, R.color.text_secondary));
        desc.setPadding(0, UiKit.dp(this, 2), 0, 0);
        textCol.addView(desc);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setSaveEnabled(false);
        styleSwitch(sw);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.setMarginStart(UiKit.dp(this, 12));
        row.addView(sw, swLp);

        sw.setOnCheckedChangeListener((b, checked) -> persist());
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));

        parent.addView(row);
        return sw;
    }

    private void styleSwitch(Switch sw) {
        if (Build.VERSION.SDK_INT >= 23) {
            int brand = UiKit.color(this, R.color.brand_primary);
            int offThumb = UiKit.isNightMode(this) ? 0xFFB0B7C5 : 0xFFFFFFFF;
            int offTrack = UiKit.isNightMode(this) ? 0xFF3A3F4B : 0xFFD4D9E2;
            int onTrack = (brand & 0x00FFFFFF) | 0x55000000;
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{}
            };
            sw.setThumbTintList(new ColorStateList(states, new int[]{brand, offThumb}));
            sw.setTrackTintList(new ColorStateList(states, new int[]{onTrack, offTrack}));
        }
    }

    private LinearLayout.LayoutParams sectionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dp(this, 14);
        return lp;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dp(this, 4);
        return lp;
    }

    private TextWatcher simpleWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                persist();
            }
        };
    }
}
