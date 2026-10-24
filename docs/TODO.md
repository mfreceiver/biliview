# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 系=armeabi-v7a+arm64-v8a）、
> minSdk 23→24 对齐 mitv、buildType/flavor 裁剪（alpha、r8Test、lite 移除，仅 default × debug/release）、
> 包名与 namespace 全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`（9 处源码目录移动 + 1774 处
> 词边界替换，全量编译验证；`dev.aaa1115910.biliapi` 等同根独立包与上游 URL 不在范围）、
> 发版脚本收尾应修项（单调门禁 fail-closed/远端 tag 比对/10# 防八进制、curl 超时、--publish-only
> 校验、assert 补 SDK/debuggable/testOnly 断言、mapping.txt 归档、signing.properties 仅 release
> fail-closed）。实机基线：2026-10-24 用户实测电视可安装当前 release（minSdk 24/arm 双 ABI）。

## 发版纪律变更（待用户确认后执行）

- [ ] release ABI 收敛为仅 armeabi-v7a（用户 2026-10-24 指示"后续只出 32 版 release"）：需同步改
      app/build.gradle.kts 的 abiFilters、scripts/release_assert_apk.sh 硬编码 ABI 集合、
      README「发版纪律」、AGENTS.md「发版 ABI 纪律」四处；APK 体积约减半

## 功能缺口

- [ ] 直播 UI：API 层已齐（`BiliLiveHttpApi`：getRoomPlayInfo / getDanmuInfo / playUrl 等），
      但 app 内无任何直播界面与 ViewModel；需补入口、频道页、播放与弹幕
- [ ] 4K（qn=127 + fourk=1）链路端到端实测：网页版 playurl + 大会员账号在真机的解码表现
      （HEVC/AV1/HDR 硬解机型差异）
- [ ] TV 首页推荐流调优：上游推荐位是否即网页版 `/wbi/index/top/feed/rcmd`（fresh_type=4），
      与预期"网页版推荐内容"核对并调整

## 代码清理（无用功能移除）

- [ ] 上游残留死代码与未用资源清理（注释掉的逻辑、多余 strings/drawable）
- [ ] 非阻塞小项（来自清理/迁移/发版审阅）：
      `GithubApi.getLatestPreReleaseBuild` 仅剩测试引用可删（连同其测试）；
      app/.gitignore 残留 `/r8Test` 行；.github/workflows 产物名为上游遗留（workflow 不运行，可简化）；
      `fException` 的 `$msg` 未参与消息拼接（上游 bug，顺手修）；
      孤儿文件 SmsLoginContent.kt、未用 `ic_launcher_round`、
      ToViewRepository.kt:29 的 `print(data)` 调试输出移除

## 发版与工程化

- [ ] 工具链升级执行（评估已完成 2026-10-24，结论：拆步迁移、发版线暂缓机械对齐 AGP 9.3.1+Gradle 9.7.1）：
      ① 先在 AGP 8.11.1 上把 app/build.gradle.kts:122-133 的 applicationVariants/ApkVariantOutputImpl
      迁到 androidComponents 公共 API（保持 APK 命名与 release_build.sh 契约）；
      ② AGP 9.3.x + Gradle 9.5.0+ + Kotlin 2.4.21 + Compose/serialization 同版 + KSP 2.3.x 作为同一联动门
      （Kotlin 2.4 产物需 R8≥9.1.29，AGP 8.11.1 自带 R8 8.11.18 不满足，禁止先单升 Kotlin；
      需实测 Room/Koin/material-symbols KSP、protobuf 0.9.5、versions 0.52.0 对 Gradle 9 兼容；
      移除 Android 模块 kotlin.android 依赖或显式关 builtInKotlin 过桥；app 与 shared 同 appId 需过
      uniquePackageNames 验证；暂不启用 configuration cache、不动 Compose UI/TV/Media3/Ktor）；
      ③ 过门后 wrapper 单升 9.7.1，再过 minified release R8 strictFullModeForKeepRules 反射回归
      （弹幕/Ktor/serialization/Room/DI/gRPC/播放）与 arm 双 ABI、16KB zipalign、真机门禁。
      依据：KGP 官方矩阵 2.4.20–21 覆盖 AGP≤9.3.1/Gradle≤9.7.0；AGP 9.3.3 含多项 R8 修复，
      迁移时取 9.3 分支最新 patch 而非钉 9.3.1。SDK 36 非升级理由（8.11 已支持）；ABI DSL abiFilters 不受 AGP9 废弃影响
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
