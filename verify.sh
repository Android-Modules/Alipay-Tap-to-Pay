#!/bin/bash
# Verify the module after enabling it in LSPosed and rebooting.
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

echo "=== device ==="
$ADB devices

echo ""
echo "=== AlipayNfc logs (hook load + dispatch) ==="
$ADB logcat -d | grep -E "AlipayNfc" | tail -30

echo ""
echo "=== nfc process loaded? ==="
$ADB shell "ps -A | grep com.android.nfc" 2>/dev/null | head -3

echo ""
echo "=== module app status ==="
$ADB shell "dumpsys package com.dalong.alipaynfc | grep -E 'versionName|firstInstallTime' | head -3"
