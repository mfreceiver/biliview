# AGENTS.md — biliview（BiliView）

本文件约束在此仓库工作的人与 AI 代理。全局/实例级约定见 `~/.dsh/AGENTS.md`，冲突时以用户直接指示 > 本文件。

## 项目身份

- BiliView：[aaa1115910/bv](https://github.com/aaa1115910/bv)（MIT）的个人自用二次开发分支，Android TV 为主。
- 本仓库 git 历史已重新初始化（单根提交 import 上游 @0e454c0），**不能直接 merge 上游分支**；上游跟进见 `docs/TODO.md`「上游同步策略」。
- 姊妹项目：`/home/mar/personal_projects/fongmi-tv`（mitv）。工程流程、设备基线（minSdk 24 / JDK 21）尽量与它对齐；其 `scripts/release_build.sh` + `release_assert_apk.sh` 是发版脚本的移植蓝本。

## 命名双层约定（改动前必读）

- 对外安装身份：`applicationId = cn.vectory.biliview`（定义于 `buildSrc/src/main/kotlin/AppConfiguration.kt`）。
- 各模块 `namespace` 与全部 Kotlin 包名已于 2026-10-23 一次性脚本化迁移为 `cn.vectory.biliview`（由 `AppConfiguration.appId` 派生，R/BuildConfig 锚点同步；上游 `dev.aaa1115910.bv` 仅存在于迁移前的 git 历史）。迁移覆盖 `dev.aaa1115910.bv` 完整词边界；**不**迁移同根独立包 `dev.aaa1115910.biliapi` / `dev.aaa1115910.bilisubtitle` / `dev.aaa1115910.symbols` / `dev.aaa1115910.m3qrcode`，也不改 GitHub 仓库 URL（aaa1115910/bv）与 gradle group。

## 构建环境

- JDK 21（gradle 工具链要求；本机 `/home/mar/jdk/jdk-21.0.12+8`，`~/.gradle/gradle.properties` 已设 `org.gradle.java.home`；系统默认 java 版本更高，仅作启动器）。
- `local.properties` 写 `sdk.dir=/home/mar/Android/Sdk`（不入库）；compileSdk 36 / minSdk 24 / targetSdk 36。
- 首次克隆：`git submodule update --init --depth 1`（`libs` = aaa1115910/bv-libs，含 av1/ffmpeg 解码 AAR；
  子模块里的 `libVLC` 目录本仓库不再 include/依赖，VLC 解码链路已于 2026-10-24 整体移除）。
- 常用任务：`./gradlew :app:assembleDefaultDebug`、`./gradlew :app:assembleDefaultRelease`。
- 维度：flavor `default` × buildType `debug`/`release`（alpha、r8Test buildType 与 lite flavor 已裁剪移除）。

## Kotlin 与 AGP 9 纪律（勿回退）

- 10 个 Android 模块（app 系 ×4、player 系 ×5、utils）编译走 **AGP 9 内置 Kotlin**，**禁止再应用
  `org.jetbrains.kotlin.android`**（会与 AGP 自注册的 `kotlin` 扩展撞名：`Cannot add extension with name 'kotlin'`）。
- 内置 Kotlin 版本由 AGP 决定（AGP 9.3.1 → Kotlin 2.2.10，见其 POM 的 `kotlin-gradle-plugin` 依赖）；
  catalog 里 `kotlin = "2.2.10"` 只用于 `org.jetbrains.kotlin.plugin.compose` / `…plugin.serialization` 子插件
  与纯 JVM 模块的 `kotlin.jvm`，**必须与内置版本一致**。抬 Kotlin 版本只能靠 buildscript classpath 覆盖 KGP。
- KSP 需 ≥2.3.10（内置 Kotlin 下的 R 类解析修复，google/ksp#2857）；当前 2.3.12。
- 禁止再引入 AGP 9 的 `builtInKotlin` / `newDsl` / `uniquePackageNames` 逃生口：它们都是「第三方插件
  仍在读旧 DSL」的过桥配置，回退会以扩展撞名 / `BaseExtension` CCE / manifest namespace 冲突复现。
- namespace 唯一：`:app` = `AppConfiguration.appId`（`cn.vectory.biliview`），`:app:shared` =
  `cn.vectory.biliview.shared`（2026-10-24 拆分，R/BuildConfig 引用在 shared 及其消费者里都用 `.shared.` 前缀；
  shared 的 manifest 是应用 manifest 的实际来源，其中 `.BVApp` 必须写全限定名）。

## 发版 ABI 纪律（强制，勿删）

- `debug`：仅 `x86`（本机模拟器用）。
- `release`：仅 `armeabi-v7a`（2026-10-24 收敛，电视实机验证通过），**禁止 arm64-v8a / x86/x86_64**。
- 由 `app/build.gradle.kts` 的 `buildTypes.configureEach { ndk { abiFilters … } }` 强制；改动 ABI 集合须先更新 README「发版纪律」与本条。
- 版本：`buildSrc` `AppConfiguration` 的 major/minor/patch，**核心固定三位**；修复（hotfix）也走 patch 位递增（如 0.2.1 → 0.2.2），不得另起第四位；`versionCode = git rev-list --count HEAD`（依赖提交计数，勿手工指定版本号）；产物命名 `BiliView_<versionCode>_<versionName>.<buildType>_<flavor>_<abi>.apk`。
- release 签名：根目录 `signing.properties`（不入库，三键 storeFile/storePassword/keyAlias，keyAlias=release，keystore 与 mitv 同款）；缺该文件时产物 unsigned。发版只经 `scripts/release_build.sh`（版本唯一/单调、证书指纹钉定 fail-closed、APK/ 归档 + SHA256SUMS、Gitea 发布），**禁止手打 tag 发版**；ABI 集合同时硬编码于 `release_assert_apk.sh`，改动 ABI 须同步该脚本。

## 已移除项（勿以任何理由恢复）

- 用户黑名单机制全链路（BlacklistUtil、blacklist.proto/raw、downloadBlacklist 任务、启动/登录强杀）。
- 大陆区域拦截（RegionBlockScreen tv+mobile、NetworkUtil、MainActivity 启动网络检测）。
- VLC 解码链路（2026-10-24 整体删除：LibVLCDownloaderDialog、VlcLibsApi、libVLCVersion 常量与 player 的
  BuildConfig 字段、`include(":libs:libVLC")`、version catalog 的 vlc 条目、proguard 的 org.videolan keep）。
  要接回需连同 gradle 依赖、下载器 UI、runtime so 加载一起重新评估，不要只恢复其中一段。
- 上游 README 的「禁止大陆使用/境内分发」声明；本仓库定位个人本地编译自用，不对外分发。

## 工作纪律

- 待办唯一登记处：`docs/TODO.md`（fongmi 风格：完成后立即移除条目）。
- 提交推送到 `origin main`（http://192.168.3.252:11330/mfreceiver/biliview.git ）。
- B 站 API 语义参考 bilibili-API-collect 社区文档；涉及风控面（buvid3、WBI 签名、412/-352 退避、请求频率）的改动保持保守，勿引入高频轮询或批量请求。
- 与上游 bv 的差异是刻意的：发现"和上游不一样"不等于 bug，先查本文件与 git 历史。
