# BiliView

哔哩哔哩第三方客户端（Android TV 为主，附带移动端界面），个人自用分支。

基于开源项目 [aaa1115910/bv](https://github.com/aaa1115910/bv)（MIT License）二次开发，本仓库相对上游的调整：

- 品牌更名为 BiliView，applicationId 改为 `cn.vectory.biliview`
- 移除上游用户黑名单机制（远程黑名单下载 + 登录/启动强杀）
- 移除大陆区域网络检测与 RegionBlock 拦截页
- 移除上游 README 中的分发限制声明；版本号自 0.1.0 起独立演进

本仓库为个人本地编译自用，不对外分发。

## 构建要求

- JDK 21（工具链见 `buildSrc` 的 `AppConfiguration.jdk`）
- Android SDK：compileSdk 36 / build-tools 35+
- 根目录 `local.properties` 写入 `sdk.dir=<SDK 路径>`（不入库）
- 首次克隆后初始化子模块：`git submodule update --init --depth 1`

```shell
./gradlew :app:assembleDefaultDebug     # 调试包（仅 x86，供本机模拟器）
./gradlew :app:assembleDefaultRelease   # 发版包（armeabi-v7a + arm64-v8a 二合一）
```

## 发版纪律

- **release**：单一 universal APK，armeabi-v7a（arm32）+
  arm64-v8a（arm64）合并打包，**不含 x86 / x86_64**；
- **debug**：仅含 x86，供本机 Android 模拟器安装调试；
- ABI 约束由 `app/build.gradle.kts` 中 `buildTypes.configureEach` 的 `ndk.abiFilters` 强制；
- 产物命名：`BiliView_<versionCode>_<versionName>.<buildType>_<flavor>_<abi>.apk`；
- minSdk 24（Android 7.0+），与个人另一 TV 项目 mitv（fongmi-tv）设备基线对齐；
- 发版经 `scripts/release_build.sh`（main+净树与版本门禁、签名指纹钉定、`APK/` 归档 + SHA256SUMS、
  Gitea release 发布）；签名密钥与 mitv 同款（`signing.properties`，不入库）。

## 许可

上游 [aaa1115910/bv](https://github.com/aaa1115910/bv) 采用 MIT License，本仓库保留其
LICENSE 与版权声明，二次修改部分同样以 MIT 提供。
