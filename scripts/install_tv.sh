#!/usr/bin/env bash
# BiliView TV 安装脚本：adb 推 APK 到 TCL 电视并安装（含 packageinstaller 绕过，流程源自 mitv 项目 AGENTS.md）。
# 用法：scripts/install_tv.sh <apk路径> [tv地址，默认 192.168.3.31:5555]
set -euo pipefail
APK="${1:?用法: scripts/install_tv.sh <apk> [tv_addr]}"
TV="${2:-192.168.3.31:5555}"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"

[[ -f "$APK" ]] || { echo "❌ APK 不存在: $APK" >&2; exit 1; }

"$ADB" connect "$TV"
restore() {
  "$ADB" -s "$TV" shell pm enable com.android.packageinstaller >/dev/null 2>&1 || true
}
trap restore EXIT
# TCL 国内版固件在框架层拦截 adb 安装第三方 APK，需临时禁用 packageinstaller
"$ADB" -s "$TV" shell pm disable-user --user 0 com.android.packageinstaller >/dev/null
"$ADB" -s "$TV" install -r "$APK"
restore
trap - EXIT
echo "✅ 已安装到 $TV：$APK"
