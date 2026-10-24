# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 仅 armeabi-v7a）、
> minSdk 23→24 对齐 mitv、buildType/flavor 裁剪（alpha、r8Test、lite 移除，仅 default × debug/release）、
> 包名与 namespace 全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`（9 处源码目录移动 + 1774 处
> 词边界替换，全量编译验证；`dev.aaa1115910.biliapi` 等同根独立包与上游 URL 不在范围）、
> 发版脚本收尾应修项（单调门禁 fail-closed/远端 tag 比对/10# 防八进制、curl 超时、--publish-only
> 校验、assert 补 SDK/debuggable/testOnly 断言、mapping.txt 归档、signing.properties 仅 release
> fail-closed）。实机基线：2026-10-24 用户实测电视可安装当前 release（minSdk 24，当时为 arm 双 ABI，后收敛为仅 armeabi-v7a）。

## 功能缺口

- [ ] 直播 UI：API 层已齐（`BiliLiveHttpApi`：getRoomPlayInfo / getDanmuInfo / playUrl 等），
      但 app 内无任何直播界面与 ViewModel；需补入口、频道页、播放与弹幕
- [ ] 4K（qn=127 + fourk=1）链路端到端实测：网页版 playurl + 大会员账号在真机的解码表现
      （HEVC/AV1/HDR 硬解机型差异）
- [ ] TV 首页推荐流调优：上游推荐位是否即网页版 `/wbi/index/top/feed/rcmd`（fresh_type=4），
      与预期"网页版推荐内容"核对并调整

## 代码清理（无用功能移除）

- [ ] 上游残留死代码与未用资源清理（注释掉的逻辑、多余 strings/drawable）
- [ ] VLC 解码链路现状（2026-10-24 核实，决定「删干净」还是「接回来」）：
      `PlayerType` 枚举只有 `Media3`（无 VLC 选项）；`LibVLCDownloaderDialog` 在 PlayerTypeSetting.kt 里
      实例化但 `showLibVLCDownloaderDialog` 从未置 true（永不显示）；`VlcLibsApi` 与
      `AppConfiguration.libVLCVersion`（player/build.gradle.kts:19 的 BuildConfig 字段）无其它消费者；
      `:libs:libVLC` 只在 settings 里 include、无模块依赖，AAR 不进包 —— 整条链路是死的
- [ ] 非阻塞小项（来自清理/迁移/发版审阅）：
      `GithubApi.getLatestPreReleaseBuild` 仅剩测试引用可删（连同其测试）；
      app/.gitignore 残留 `/r8Test` 行；.github/workflows 产物名为上游遗留（workflow 不运行，可简化）；
      `fException` 的 `$msg` 未参与消息拼接（上游 bug，顺手修）；
      孤儿文件 SmsLoginContent.kt、未用 `ic_launcher_round`、
      ToViewRepository.kt:29 的 `print(data)` 调试输出移除

## 发版与工程化

- [ ] 工具链升级收尾（主体已完成 2026-10-24：Gradle 8.14.3→9.7.1、AGP 8.11.1→9.3.1、Kotlin 2.2.0→2.2.10、
      KSP 2.2.0-2.0.2→2.3.6、protobuf 插件 0.9.5→0.10.0，对齐 fongmi-tv/dshdroid 本机基线；APK 命名迁
      androidComponents.onVariants；assembleDebug/Release + release_assert_apk.sh 全绿。偏离原计划的说明：
      Kotlin 取 2.2.10 而非 2.4.21——为对齐本机基线且绕开 R8≥9.1.29 约束，升 2.4 时重评；AGP 钉 9.3.1——
      9.3.3 未验证且无必要，取 9.3 最新 patch 时一并回归）。剩余项：
      ① 发版前实机门禁：minified release 的 R8 反射回归冒烟。已按反射面排查加固（2026-10-24）：
      akdanmaku 整库 keep、`-keep class * extends com.google.protobuf.GeneratedMessage { *; }`
      （gRPC 错误路径经 Class.forName(服务端 typeUrl) + Any.unpack 反射取 getDefaultInstance，
      旧规则 `-keep class bilibili.rpc.**` 只覆盖 24 个生成包里的 1 个）、
      `-keepattributes Signature`（泛型 serializer 查找必需，库自带规则不保）、
      SourceFile/LineNumberTable（崩溃栈可读，代价 +1.8MB APK）。待实测：弹幕/登录/推荐流/播放/搜索/
      历史 Room，以及 gRPC 错误路径（登录失败、-352 退避、播放报错）是否返回正常文案而非 CNFE；
      ② AGP 10 逃生口债务：移除 android.builtInKotlin=false（11 个模块去 kotlin.android 改内置 Kotlin）与
      android.newDsl=false（android{} 块按新 DSL 重写）；
      ③ android.uniquePackageNames=false 债务：:app 与 :app:shared 共用 namespace cn.vectory.biliview，
      长期解法是给 shared 单独 namespace（牵动 R/BuildConfig 引用），或永久保留并在 AGENTS.md 注明刻意决定；
      ④ versions 插件 0.52.0 在 Gradle 9 下未跑过 dependencyUpdates，待确认；
      ⑤ 后续升级项：Kotlin 2.4、AGP 9.3 最新 patch；暂不启用 configuration cache
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）
- [ ] R8 规则的遗留盲区（2026-10-24 加固后复盘，发货 APK 的 mapping 取证）：
      ① ~~slf4j ServiceLoader~~ 已证伪并关闭：R8 内建 ServiceLoader 重写把服务文件一起改名，
      APK 内 `META-INF/services/rs9` 内容 = `kl4 qta`，解析为 com.gitlab.mvysny.slf4j.HandroidServiceProvider
      与 org.slf4j.simple.SimpleServiceProvider，两者都在 dex 里，日志走 logcat 正常 —— 无需 keep；
      ② com.badlogic.gdx 20 个类全部被改名（akdanmaku 引擎依赖 gdx 类型），gdx 若有反射/JNI 回调面，
      需真机确认弹幕是否正常；
      ③ LibVLC 空转 keep 已删（规则保护不到任何类）；VLC 解码要不要接见「代码清理」条目；
      ④ consumer-rules.pro：删掉 app/{shared,tv,mobile} 三个未接线的空文件；player/{core,shared,tv,mobile}、
      player、utils 六处仍由 consumerProguardFiles 引用，留作占位——将来依赖库需要专属规则时写在这里

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
