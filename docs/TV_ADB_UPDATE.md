# TCL 电视 ADB 覆盖安装流程（biliview / mitv 通用）

适用：TCL 75Q10G Pro（MT5879，Android 11）实测通过；同代 TCL/雷鸟机型大概率通用。
原理：TCL 在 system_server 定制了 `OverseasAppConfig` 安装验证器，拦截所有非商店
sideload（报 `INSTALL_FAILED_VERIFICATION_FAILURE`；`persist.tcl.installapk.*` 属性、
关闭 `package_verifier_enable`、禁用 tcl.c2dm.client 均无效）。唯一有效绕法是
**临时禁用系统包安装器**，装完立即恢复。

## 一次性准备（电视上）

1. 开启 ADB：设置 → 系统/关于 → 系统信息页，遥控器依次按 **上、下、左、右**，
   弹出隐藏 ADB 开关，打开。
2. 查电视 IP：设置 → 网络 → 网络详情。（本机已绑定静态 IP：192.168.3.32，2026-10-26
   已续租生效；改绑后可用 `adb shell svc wifi disable && sleep 5 && adb shell svc wifi enable`
   强制电视重新 DHCP——`cmd wifi disconnect` 被 TCL 定制权限拒绝，svc 可用）

## 每次更新（电脑上）

```bash
export PATH=$PATH:~/Android/Sdk/platform-tools
IP=192.168.3.32

adb kill-server 2>/dev/null
adb connect $IP:5555          # 首次电视会弹 RSA 授权框，用遥控器勾选确认
adb devices                    # 确认状态为 device（不是 unauthorized）

# 核心三步：禁用安装器 → 覆盖安装 → 立即恢复
adb -s $IP:5555 shell "pm disable-user --user 0 com.android.packageinstaller"
adb -s $IP:5555 install -r <APK路径>     # Success 即成功；-r 覆盖安装保留全部数据
adb -s $IP:5555 shell "pm enable com.android.packageinstaller"
```

失败排查：
- `unauthorized`：电视上没确认 RSA 弹框，等弹框或重连。
- `INSTALL_FAILED_VERIFICATION_FAILURE`：确认 packageinstaller 真的处于
  disabled 状态（`adb shell pm list packages -d | grep packageinstaller`）。
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`：签名不一致（比如误装了 debug 包），只能卸载重装。

## 注意事项

- **装完必须恢复 packageinstaller**，否则系统 OTA 更新会卡死。
- 同签名 `-r` 覆盖安装**不丢任何应用数据**（登录态/设置全在）；这正是本流程的价值。
- biliview 的 debug 包只有 x86 ABI（模拟器用），电视是 armeabi-v7a，
  电视只能装 `assembleDefaultRelease` 产物。
- 电视重启后 ADB 开关可能复位，需重新用「上下左右」开启。
- 账号库（biliview ≥0.3.1，设置→账号备份目录）是本流程之外的双保险：
  即使被迫卸载重装，授权目录后冷启动可自动恢复上次登录账号。

## 实测记录

- 2026-10-26：biliview 0.2.1(versionCode 19) → 0.3.1(51) 覆盖安装成功，数据保留。
