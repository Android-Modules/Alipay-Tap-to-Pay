package com.dalong.alipaynfc.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.dalong.alipaynfc.C;
import com.dalong.alipaynfc.data.Store;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * GUI: pick which Alipay instance answers NFC tap-to-pay, toggle redirect, view records.
 */
public class MainActivity extends Activity {

    private TextView statusText;
    private RadioGroup targetGroup;
    private Switch enableSwitch;
    private Switch historySwitch;
    private LinearLayout pkgModeRow;
    private Switch pkgModeSwitch;
    private EditText pkgInput;
    private EditText userInput;
    private LinearLayout recordsBox;
    private TextView recordsEmpty;
    private LinearLayout usersBox;
    private TextView usersHint;

    private BroadcastReceiver receiver;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);

    // User entries shown in the radio list: {id, label}
    private final List<int[]> userChoices = new ArrayList<>();
    private final List<String> userLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (C.ACTION_SCAN_RESULT.equals(action) || C.ACTION_HOOK_ALIVE.equals(action)) {
                    runOnUiThread(MainActivity.this::refreshAll);
                } else if (C.ACTION_RECORD.equals(action)) {
                    runOnUiThread(() -> renderRecords());
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(C.ACTION_SCAN_RESULT);
        f.addAction(C.ACTION_HOOK_ALIVE);
        f.addAction(C.ACTION_RECORD);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, android.content.Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }

        // Hooks publish state via Settings.Global; observe changes for live UI refresh.
        android.database.ContentObserver observer = new android.database.ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                runOnUiThread(MainActivity.this::refreshAll);
            }
        };
        getContentResolver().registerContentObserver(
                android.provider.Settings.Global.getUriFor(C.GKEY_ALIVE), true, observer);
        getContentResolver().registerContentObserver(
                android.provider.Settings.Global.getUriFor(C.GKEY_USERS), true, observer);
        getContentResolver().registerContentObserver(
                android.provider.Settings.Global.getUriFor(C.GKEY_RECORDS), true, observer);

        renderAll();
        requestScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAll();
        requestScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) unregisterReceiver(receiver);
    }

    private void requestScan() {
        try {
            // Action-only broadcast: the hooks' dynamically registered receivers
            // (com.android.nfc / system_server) listen for this.
            sendBroadcast(new Intent(C.ACTION_REQUEST_SCAN));
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- persistence

    private void persist() {
        Store.Config c = new Store.Config();
        c.enabled = enableSwitch.isChecked();
        c.targetUser = currentSelectionUserId();
        c.targetPkg = pkgModeSwitch.isChecked() ? pkgInput.getText().toString().trim() : "";
        c.keepHistory = historySwitch.isChecked();
        Store.saveConfig(this, c);
        try {
            sendBroadcast(new Intent(C.ACTION_CONFIG_CHANGED));
        } catch (Exception ignored) {
        }
    }

    private int currentSelectionUserId() {
        // Manual user id (advanced) overrides the radio selection.
        if (userInput != null) {
            String manual = userInput.getText().toString().trim();
            if (!manual.isEmpty()) {
                try {
                    return Integer.parseInt(manual);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        int checkedId = targetGroup.getCheckedRadioButtonId();
        // Radio ids encode the index in userChoices, offset by 1000.
        int idx = checkedId - 1000;
        if (idx >= 0 && idx < userChoices.size()) return userChoices.get(idx)[1];
        return 0;
    }

    private void selectUserInUi(int userId) {
        for (int i = 0; i < userChoices.size(); i++) {
            if (userChoices.get(i)[1] == userId) {
                targetGroup.check(1000 + i);
                return;
            }
        }
        if (!userChoices.isEmpty()) targetGroup.check(1000); // fall back to main Alipay
    }

    // ---------------------------------------------------------------- rendering

    private void refreshAll() {
        renderUsers();
        renderStatus();
        renderRecords();
        renderSwitches();
    }

    private void renderAll() {
        refreshAll();
    }

    private void renderSwitches() {
        Store.Config c = Store.loadConfig(this);
        enableSwitch.setChecked(c.enabled);
        historySwitch.setChecked(c.keepHistory);
        pkgModeSwitch.setChecked(!c.targetPkg.isEmpty());
        pkgInput.setText(c.targetPkg);
        pkgInput.setVisibility(pkgModeSwitch.isChecked() ? View.VISIBLE : View.GONE);
        boolean known = false;
        for (int[] choice : userChoices) {
            if (choice[1] == c.targetUser) {
                known = true;
                break;
            }
        }
        if (known) {
            userInput.setText("");
            selectUserInUi(c.targetUser);
        } else if (c.targetUser != 0) {
            android.util.Log.i("AlipayNfcUI", "renderSwitches: unknown target " + c.targetUser);
            userInput.setText(String.valueOf(c.targetUser));
        } else {
            userInput.setText("");
            selectUserInUi(0);
        }
    }

    private void renderStatus() {
        try {
            String raw = Store.getAlive(this);
            android.util.Log.i("AlipayNfcUI", "alive raw=" + raw
                    + " users=" + Store.getUsers(this));
            JSONObject alive = new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
            long sys = alive.optLong("android", 0);
            long nfc = alive.optLong("com.android.nfc", 0);
            boolean sysOk = sys > 0;
            boolean nfcOk = nfc > 0;
            String msg;
            int color;
            if (sysOk && nfcOk) {
                msg = "● 模块已激活：system_server ✓  NFC 服务 ✓";
                color = 0xFF1B8A3A;
            } else if (sysOk) {
                msg = "○ system_server ✓  NFC 服务待触发（开启 NFC 后生效）";
                color = 0xFFB36600;
            } else if (nfcOk) {
                msg = "○ NFC 服务已激活 ✓  system_server 待重启手机后生效";
                color = 0xFFB36600;
            } else {
                msg = "○ 未检测到注入，请在 LSPosed 中启用并重启手机";
                color = 0xFFB36600;
            }
            statusText.setText(msg);
            statusText.setTextColor(color);
        } catch (Exception e) {
            statusText.setText("○ 状态未知");
        }
    }

    private void renderUsers() {
        try {
            targetGroup.removeAllViews();
            userChoices.clear();
            userLabels.clear();

            // Main Alipay is always present (user 0).
            userChoices.add(new int[]{0, 0});
            userLabels.add("主支付宝（默认）");

            String json = Store.getUsers(this);
            if (!json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                JSONArray users = root.optJSONArray("users");
                if (users != null) {
                    for (int i = 0; i < users.length(); i++) {
                        JSONObject u = users.getJSONObject(i);
                        int id = u.optInt("id", -1);
                        if (id == 0) continue; // already added as main
                        if (!u.optBoolean("alipay", false)) continue;
                        userChoices.add(new int[]{id, id});
                        String name = u.optString("name", "");
                        userLabels.add(id == 999 ? "多开支付宝（用户分身 999）"
                                : "支付宝实例（用户 " + id + (name.isEmpty() ? "" : "，" + name) + "）");
                    }
                }
            }

            boolean scanned = !json.isEmpty();
            // Always surface the currently selected target so the choice stays visible
            // even before the system_server scan populates the list.
            Store.Config cur = Store.loadConfig(this);
            boolean known = false;
            for (int[] choice : userChoices) {
                if (choice[1] == cur.targetUser) {
                    known = true;
                    break;
                }
            }
            if (!known && cur.targetUser != 0) {
                userChoices.add(new int[]{cur.targetUser, cur.targetUser});
                userLabels.add(cur.targetUser == 999 ? "多开支付宝（用户 999）"
                        : "支付宝实例（用户 " + cur.targetUser + "）");
            }

            usersHint.setVisibility(
                    scanned && userChoices.size() > 1 ? View.GONE : View.VISIBLE);
            usersHint.setText(scanned
                    ? "未发现其他支付宝实例。如果本机使用第三方多开容器，请展开“高级”使用包名模式。"
                    : "重启手机后自动扫描；也可以在“高级”里手动指定用户 ID。");

            targetGroup.setOnCheckedChangeListener(null);
            targetGroup.clearCheck(); // RadioGroup keeps stale mCheckedId after removeAllViews;
                                      // without this, check() is short-circuited and the new
                                      // radio never renders as selected.
            Store.Config cfg = Store.loadConfig(this);
            android.util.Log.i("AlipayNfcUI", "renderUsers: choices=" + userChoices.size()
                    + " target=" + cfg.targetUser);
            for (int i = 0; i < userChoices.size(); i++) {
                RadioButton rb = new RadioButton(this);
                rb.setText(userLabels.get(i));
                rb.setId(1000 + i);
                rb.setTextSize(16);
                rb.setPadding(dp(4), dp(10), dp(4), dp(10));
                targetGroup.addView(rb, new RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                if (userChoices.get(i)[1] == cfg.targetUser) {
                    targetGroup.check(1000 + i);
                    android.util.Log.i("AlipayNfcUI", "renderUsers: checked index " + i);
                }
            }
            targetGroup.setOnCheckedChangeListener((group, checkedId) -> persist());
        } catch (Exception e) {
            usersHint.setText("解析扫描结果失败: " + e);
        }
    }

    private void renderRecords() {
        recordsBox.removeAllViews();
        JSONArray records = Store.getRecords(this);
        recordsEmpty.setVisibility(records.length() == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < records.length(); i++) {
            try {
                JSONObject r = records.getJSONObject(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(8), dp(8), dp(8), dp(8));

                TextView head = new TextView(this);
                head.setText(timeFmt.format(new Date(r.optLong("ts")))
                        + "  →  " + r.optString("to", "?"));
                head.setTypeface(Typeface.DEFAULT_BOLD);
                head.setTextSize(14);
                row.addView(head);

                String from = r.optString("from", "");
                String uri = r.optString("uri", "");
                TextView sub = new TextView(this);
                String detail = from == null || from.isEmpty() ? uri : from;
                sub.setText(detail.length() > 120 ? detail.substring(0, 120) + "…" : detail);
                sub.setTextSize(12);
                sub.setAlpha(0.7f);
                row.addView(sub);

                recordsBox.addView(row);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- UI builder

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("碰一碰切换");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView logo = new TextView(this);
        logo.setText("📶");
        logo.setTextSize(26);
        header.addView(logo);
        root.addView(header);

        statusText = new TextView(this);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(6), 0, dp(12));
        root.addView(statusText);

        // Card: target selection
        LinearLayout card1 = card();
        card1.addView(sectionTitle("碰一碰目标"));
        card1.addView(sectionDesc("选择 NFC 碰一碰（碰一下）时由哪个支付宝响应。修改即时生效，无需重启。"));
        usersHint = sectionDesc("正在扫描支付宝实例…");
        card1.addView(usersHint);
        targetGroup = new RadioGroup(this);
        targetGroup.setOrientation(RadioGroup.VERTICAL);
        card1.addView(targetGroup);
        root.addView(card1, cardLp());

        // Card: switches
        LinearLayout card2 = card();
        enableSwitch = switchRow(card2, "启用碰一碰重定向", "关闭后 NFC 派发恢复系统默认行为");
        historySwitch = switchRow(card2, "保存碰一碰记录", "每次碰一碰派发都会记录到下方列表");
        root.addView(card2, cardLp());

        // Card: records
        LinearLayout card3 = card();
        LinearLayout recHeader = new LinearLayout(this);
        recHeader.setOrientation(LinearLayout.HORIZONTAL);
        recHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView recTitle = sectionTitle("碰一碰记录");
        recTitle.setPadding(0, 0, dp(8), 0);
        recHeader.addView(recTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clearBtn = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        clearBtn.setText("清空");
        clearBtn.setOnClickListener(v -> {
            Store.clearRecords(MainActivity.this);
            renderRecords();
        });
        recHeader.addView(clearBtn);
        card3.addView(recHeader);
        recordsBox = new LinearLayout(this);
        recordsBox.setOrientation(LinearLayout.VERTICAL);
        card3.addView(recordsBox);
        recordsEmpty = sectionDesc("暂无记录。碰一次商家碰一碰设备后这里会出现派发日志。");
        card3.addView(recordsEmpty);
        root.addView(card3, cardLp());

        // Card: advanced
        LinearLayout card4 = card();
        pkgModeSwitch = new Switch(this);
        pkgModeSwitch.setText("高级：包名重定向模式");
        pkgModeSwitch.setTextSize(15);
        pkgModeSwitch.setOnCheckedChangeListener((b, checked) -> {
            pkgInput.setVisibility(checked ? View.VISIBLE : View.GONE);
            persist();
        });
        card4.addView(pkgModeSwitch, rowLp());
        card4.addView(sectionDesc("适用于第三方多开容器（如虚拟机类 App）：启用后请填写容器内支付宝的宿主包名。系统级双开（用户分身）无需开启此项。"));
        pkgInput = new EditText(this);
        pkgInput.setHint("容器宿主包名，例如 com.xxx.parallel");
        pkgInput.setInputType(InputType.TYPE_CLASS_TEXT);
        pkgInput.setTextSize(14);
        pkgInput.setVisibility(View.GONE);
        pkgInput.addTextChangedListener(simpleWatcher());
        card4.addView(pkgInput, rowLp());

        card4.addView(sectionTitle("手动指定用户 ID（可选）"));
        card4.addView(sectionDesc("直接填写支付宝分身所在的 Android 用户 ID（如小米分身 999），填写后覆盖上方单选。"));
        userInput = new EditText(this);
        userInput.setHint("例如 999（留空使用上方单选）");
        userInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        userInput.setTextSize(14);
        userInput.addTextChangedListener(simpleWatcher());
        card4.addView(userInput, rowLp());
        root.addView(card4, cardLp());

        // Card: help
        LinearLayout card5 = card();
        card5.addView(sectionTitle("使用说明"));
        card5.addView(sectionDesc(
                "1. 在 LSPosed 管理器中启用本模块，作用域勾选“Android 系统”和“NFC”，然后重启手机。\n"
                + "2. 打开支付宝碰一碰功能，手机触碰商家“碰一下”设备即可验证。\n"
                + "3. 切换目标后立即生效；记录列表可确认每次派发的实际去向。\n"
                + "4. 若切到多开后碰一碰无反应，请检查多开支付宝已登录并已开通碰一碰功能。"));
        root.addView(card5, cardLp());

        // Rescan button
        Button rescan = new Button(this);
        rescan.setText("重新扫描支付宝实例");
        rescan.setOnClickListener(v -> {
            usersHint.setText("正在扫描支付宝实例…");
            requestScan();
        });
        root.addView(rescan, rowLp());
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedBg());
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        return lp;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(2), 0, dp(6));
        return tv;
    }

    private TextView sectionDesc(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(0, dp(4), 0, dp(4));
        tv.setAlpha(0.75f);
        return tv;
    }

    private Switch switchRow(LinearLayout parent, String text, String desc) {
        Switch sw = new Switch(this);
        sw.setText(text);
        sw.setTextSize(15);
        sw.setPadding(0, dp(6), 0, dp(2));
        parent.addView(sw, rowLp());
        TextView d = sectionDesc(desc);
        d.setPadding(0, 0, 0, dp(8));
        parent.addView(d);
        sw.setOnCheckedChangeListener((b, checked) -> persist());
        return sw;
    }

    private GradientDrawable roundedBg() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(14));
        TypedArray a = obtainStyledAttributes(new int[]{android.R.attr.colorBackgroundFloating});
        int color = a.getColor(0, Color.WHITE);
        a.recycle();
        d.setColor(color);
        return d;
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
