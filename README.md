# Alipay-Tap-to-Pay

支付宝碰一碰模块，解决多开碰一碰切换问题。

# 支付宝碰一碰切换（AlipayNfcSwitch）

LSPosed 模块：切换安卓手机「支付宝碰一碰（碰一下）」NFC 派发由**主支付宝**还是**多开支付宝**（系统分身/多开容器）响应。

- 支持系统级应用分身（同包名不同 userId，如小米 XSpace user 999）
- 支持第三方多开容器（包名重定向模式，高级选项）
- 图形界面切换，配置即时生效（无需重启），并保存碰一碰派发记录

已在 **Redmi K90 / HyperOS 3（Android 16, API 36）/ LSPosed 2.1.1** 上逆向验证并开发。

## 原理

碰一碰的本质是 **NFC NDEF 标签派发**：支付宝为 `alipays://`、`https://render.alipay.com`、`qr.alipay.com`、`n.alipay.cn` 等注册了 `android.nfc.action.NDEF_DISCOVERED` 过滤器（目标 `SchemeLauncherActivity`）。

HyperOS 的派发链路（反编译 `/system/app/Nfc_st/Nfc_st.apk` 确认）：

```
NfcDispatcher.dispatchTag()
  └─ DispatchInfo.tryStartActivity()
       ├─ 仅在「当前用户」(user 0) 解析接收者 → 主支付宝永远胜出
       └─ context.startActivityAsUser(rootIntent, UserHandle.CURRENT)
            rootIntent = NfcRootActivity（携带 launchIntent extra）
```

`NfcRootActivity` 会读取 `launchIntentUserHandle` extra 并把真正的派发 intent 启动到对应用户 —— 小米官方机制只覆盖 `getEnabledProfiles()`（工作资料），**应用分身（user 999）不在其中**。

本模块 hook `com.android.nfc` 进程的 `ContextImpl.startActivityAsUser`：
- 命中支付宝 NDEF 派发时，改写 `launchIntentUserHandle` extra + 外层 UserHandle → 复用小米官方的跨用户派发机制，把碰一碰拉起到分身里的支付宝；
- 或改写 `launchIntent` 的包名（第三方多开容器模式）。

同时 hook `system_server` 扫描各用户中支付宝的安装情况，报告给 App 图形界面。

## 项目结构

```
AlipayNfcSwitch/
├── app/                          # 模块 APK（Java，零第三方依赖）
│   └── src/main/java/com/dalong/alipaynfc/
│       ├── C.java                # 常量（广播/配置键）
│       ├── ui/MainActivity.java  # 图形界面（传统 View，跟随深色模式）
│       ├── data/Store.java       # 配置 + 记录存储（SharedPreferences）
│       ├── data/ScanReceiver.java# 接收 hook 侧广播（扫描结果/记录/心跳）
│       ├── provider/PrefsProvider.java  # hook 侧兜底配置通道
│       ├── tap/TapInstrumentation.java  # 调试辅助：UiAutomation 点击
│       └── xp/
│           ├── Entry.java        # Xposed 入口（按包名分发）
│           ├── HookConfig.java   # 配置读取（XSharedPreferences → Provider 兜底）
│           ├── NfcHook.java      # NFC 派发重定向（com.android.nfc 进程）
│           ├── SystemHook.java   # 用户扫描 + 广播中继（system_server）
│           └── XLog.java         # 日志（logcat tag: AlipayNfc + LSPosed 日志）
├── xposed-stub/                  # Xposed API 编译期 stub（不打包）
└── gradle wrapper 等
```

配置通过 `xposedsharedprefs=true`（LSPosed 世界可读）+ ContentProvider 双通道读取，每次派发前自动刷新（带 2 秒缓存），App 修改配置后广播通知立即生效。

hook → App 的状态/记录走 **Settings.Global 通道**（`com.android.nfc` 持有 `WRITE_SECURE_SETTINGS`，写入 `alipaynfc_alive/users/records` 三个键），App 用 ContentObserver 实时监听——不依赖后台广播（HyperOS 会拦截后台拉起）。清空记录、请求扫描通过 action-only 广播由 hook 进程的动态接收器处理。

## 构建与安装

```bash
export JAVA_HOME=~/.local/share/mise/installs/java/17
./gradlew :app:assembleRelease --no-daemon
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 启用（需要一次手动操作）

1. 手机打开 **LSPosed 管理器** → 底部「模块」→ 找到 **碰一碰切换** → 打开开关
   （作用域会自动推荐勾选「NFC」和「Android 系统」，无需改动）
2. **重启手机**
3. 打开「碰一碰切换」App：顶部显示「模块已激活：system_server ✓ NFC 服务 ✓」即成功

## 使用

- 界面「碰一碰目标」中选择：主支付宝 / 多开支付宝（用户 999 等）
- 修改**即时生效**，无需重启
- 「碰一碰记录」卡片实时显示每次 NFC 派发的去向（时间、URI、重定向目标）
- 分身里的支付宝需已登录且已开通碰一碰功能

### 手动指定用户 ID

若 App 未扫描到分身（system_server hook 未启用时），在「高级」卡片中直接填写用户 ID（如 `999`）。

### 第三方多开容器

开启「包名重定向模式」并填写容器宿主包名。要求容器能处理支付宝碰一碰的 NDEF intent。

## 验证 / 调试

```bash
# 模块加载与派发日志（tag: AlipayNfc，同时写入 LSPosed 日志）
adb logcat -d | grep AlipayNfc

# 关键日志：
#   loaded in com.android.nfc            ← hook 已注入 NFC 服务
#   nfc dispatch: action=... data=...    ← 碰一碰派发被拦截
#   redirected: ... -> user 999          ← 重定向生效
```

真实测试：手机 NFC 开启 → 解锁 → 用主屏碰一碰商家「碰一下」设备 → 观察拉起的是哪个支付宝，以及 App 记录列表。

## 已知限制

- 需要重启手机才生效（LSPosed 模块通用）
- 关闭「启用碰一碰重定向」开关即恢复系统默认行为
- XSpace（分身）内的支付宝若未登录，拉起后无付款能力属正常现象
