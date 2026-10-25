# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 仅 armeabi-v7a）、
> minSdk 23→24 对齐 mitv、buildType/flavor 裁剪（alpha、r8Test、lite 移除，仅 default × debug/release）、
> 4K 链路实测通过、TV 首页推荐流核对完成、R8 规则遗留盲区复盘关闭（slf4j 已证伪、gdx 真机弹幕正常、
> VLC 链路已删、consumer-rules 空文件已处理，2026-10-25）。
> 包名与 namespace 全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`（9 处源码目录移动 + 1774 处
> 词边界替换，全量编译验证；`dev.aaa1115910.biliapi` 等同根独立包与上游 URL 不在范围）、
> 发版脚本收尾应修项（单调门禁 fail-closed/远端 tag 比对/10# 防八进制、curl 超时、--publish-only
> 校验、assert 补 SDK/debuggable/testOnly 断言、mapping.txt 归档、signing.properties 仅 release
> fail-closed）。实机基线：2026-10-24 用户实测电视可安装当前 release（minSdk 24，当时为 arm 双 ABI，后收敛为仅 armeabi-v7a）。

## 功能缺口

- [ ] 直播 UI：API 层已齐（`BiliLiveHttpApi`：getRoomPlayInfo / getDanmuInfo / playUrl 等），
      但 app 内无任何直播界面与 ViewModel；需补入口、频道页、播放与弹幕

## 代码清理（无用功能移除）

- [ ] 上游残留死代码与未用资源清理（注释掉的逻辑、多余 strings/drawable）

## 发版与工程化

- [ ] 工具链升级收尾（主体已完成 2026-10-24：Gradle 8.14.3→9.7.1、AGP 8.11.1→9.3.1、Kotlin 2.2.0→2.2.10、
      KSP 2.2.0-2.0.2→2.3.12、protobuf 插件 0.9.5→0.10.0，对齐 fongmi-tv/dshdroid 本机基线；APK 命名迁
      androidComponents.onVariants；assembleDebug/Release + release_assert_apk.sh 全绿。偏离原计划的说明：
      Kotlin 取 2.2.10 而非 2.4.21——为对齐本机基线且绕开 R8≥9.1.29 约束，升 2.4 时重评；AGP 钉 9.3.1——
      9.3.3 未验证且无必要，取 9.3 最新 patch 时一并回归）。2026-10-24 二次收尾：10 个 Android 模块已迁到
      AGP 9 内置 Kotlin（删 org.jetbrains.kotlin.android；AGP 9.3.1 的 POM 依赖 kotlin-gradle-plugin:2.2.10，
      故 catalog 的 kotlin=2.2.10 只喂 compose/serialization 子插件与纯 JVM 模块，必须与内置版本一致）；
      :app:shared 独立为 cn.vectory.biliview.shared（:app 独占 cn.vectory.biliview，R/BuildConfig 引用随之
      迁移 82 个文件，shared 的 manifest 里 .BVApp 改全限定）。剩余项：
      ① versions 插件 0.52.0 在 Gradle 9 下未跑过 dependencyUpdates，待确认；
      ② 后续升级项：Kotlin 2.4（需 classpath 覆盖 KGP 才能抬内置 Kotlin 版本）、AGP 9.3 最新 patch；
      暂不启用 configuration cache
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
