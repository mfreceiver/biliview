# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 系=armeabi-v7a+arm64-v8a）、
> minSdk 23→24 对齐 mitv。

## 功能缺口

- [ ] 直播 UI：API 层已齐（`BiliLiveHttpApi`：getRoomPlayInfo / getDanmuInfo / playUrl 等），
      但 app 内无任何直播界面与 ViewModel；需补入口、频道页、播放与弹幕
- [ ] 4K（qn=127 + fourk=1）链路端到端实测：网页版 playurl + 大会员账号在真机的解码表现
      （HEVC/AV1/HDR 硬解机型差异）
- [ ] TV 首页推荐流调优：上游推荐位是否即网页版 `/wbi/index/top/feed/rcmd`（fresh_type=4），
      与预期"网页版推荐内容"核对并调整

## 代码清理（无用功能移除）

- [ ] Firebase Crashlytics / google-services 全链路移除：插件 alias、`CrashlyticsExtension`
      配置、`AppConfiguration.googleServicesAvailable`、相关依赖与初始化（当前无
      google-services.json 时空操作，但代码与插件仍在构建链上）
- [ ] buildType / flavor 裁剪：评估移除 alpha、r8Test buildType 与 lite flavor
      （个人使用仅需 default × debug/release）
- [ ] 上游残留死代码与未用资源清理（注释掉的逻辑、多余 strings/drawable）

## 包名与命名空间迁移

- [ ] namespace 与 Kotlin 包名全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`：
      各模块 `namespace`、604 个 .kt 文件 package/import（含 79 处 `dev.aaa1115910.bv.R`）、
      `buildSrc AppConfiguration.appId`；建议脚本化一次完成 + 全量编译验证
      （manifest provider authority、Room/DataStore 无包名耦合可平移，protobuf 生成类除外需验证）

## 发版与工程化

- [ ] 配置 release 签名：`signing.properties` + keystore（当前无该文件时 release 产物 unsigned）
- [ ] 移植 mitv（fongmi-tv）式发版脚本 `scripts/release_build.sh`：main+干净树门禁、
      版本号唯一/单调门禁、签名证书指纹钉定（fail-closed）、APK 归档 + SHA256SUMS、
      release notes + annotated tag、`--publish` 发布到 Gitea release；
      配套 `release_assert_apk.sh` 断言（签名指纹 / 包名 cn.vectory.biliview / 版本 /
      ABI 集合 = arm32+arm64）
- [ ] 工具链对齐 mitv 评估：AGP 8.11.1 / Gradle 8.14.3 / Kotlin 2.2.0 →
      AGP 9.3.1 / Gradle 9.7.1（compileSdk/targetSdk 36 vs 35）；需评估 Compose 编译器
      与依赖联动后统一，不盲目升级
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
