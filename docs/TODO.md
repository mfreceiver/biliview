# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 仅 armeabi-v7a）、
> minSdk 23→24 对齐 mitv、buildType/flavor 裁剪（alpha、r8Test、lite 移除，仅 default × debug/release）、
> 4K 链路实测通过、TV 首页推荐流核对完成、直播链路落地（2026-10-25：BiliLiveRepository 接线、
> LivePlayerActivity + LivePlayerScreen + LivePlayerViewModel、LiveDataWebSocket 实时弹幕、清晰度菜单）、
> R8 规则遗留盲区复盘关闭（slf4j 已证伪、gdx 真机弹幕正常、
> VLC 链路已删，2026-10-25；consumer-rules 空文件并入死代码清理）、上游残留死代码/未用资源首轮清理
> （2026-10-25：重复死注释 1 处、失效 ByteDance maven 1 条、无用 string `confirm` 1 条、无引用的
> adaptive banner 链 3 件；其余 drawable/string/array/color/style 逐项验证均有引用）。
> 包名与 namespace 全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`（9 处源码目录移动 + 1774 处
> 词边界替换，全量编译验证；`dev.aaa1115910.biliapi` 等同根独立包与上游 URL 不在范围）、
> 发版脚本收尾应修项（单调门禁 fail-closed/远端 tag 比对/10# 防八进制、curl 超时、--publish-only
> 校验、assert 补 SDK/debuggable/testOnly 断言、mapping.txt 归档、signing.properties 仅 release
> fail-closed）。实机基线：2026-10-24 用户实测电视可安装当前 release（minSdk 24，当时为 arm 双 ABI，后收敛为仅 armeabi-v7a）。

## 功能缺口

- [ ] 直播真机验证（入口 / 频道页 / 播放页 / 弹幕已落地，2026-10-25）：
      ① 弹幕协议字段校准：`DANMU_MSG` 的 color / uname / uid 实机抽样核对，brotli 分片拼接边界；
      ② FLV 实机稳定性：长时间播放、切后台回前台重连、弱网退避重连表现；
      ③ 清晰度切换：`g_qn_desc` 选项与实机可切换范围一致性、切换后不黑屏

## 代码清理（无用功能移除）

- [ ] 剩余需人工判断的死代码残迹（主体已清，见 git 历史 790accf/c8c77ab）：
      ① `app/mobile/.../component/player/VideoPlayerPages.kt:134-138` 注释掉的 `VideoPlayerUgcSectionsFilter(...)`
      调用——受此影响同文件 `:184 private fun VideoPlayerUgcSectionsFilter` 与 `:203 VideoPlayerUgcSectionsFilterChip`
      全仓无调用方（UGC 分节筛选 UI 未接线）。是「重新接回筛选 UI」还是「连定义一起删」属功能取舍，需人工定；
      ② `app/mobile/.../component/reply/CommentItem.kt:115-127` 注释掉的点赞按钮块（`//TODO like comment`，
      内含 `*//*TODO*//*` 嵌套注释 hack），上游未实现评论点赞，保留待定；
      ③ `//TODO("Not yet implemented")` IDE 脚手架残迹：`app/shared/.../component/BvPlayerPreview.kt` ×9、
      `player/tv/.../BvPlayer.kt` onIdle 内 1 处（语义上仍是「未实现」标记，保留）。

## 发版与工程化

- [ ] 工具链升级收尾（主体已完成 2026-10-24：Gradle 8.14.3→9.7.1、AGP 8.11.1→9.3.1、Kotlin 2.2.0→2.2.10、
      KSP 2.2.0-2.0.2→2.3.12、protobuf 插件 0.9.5→0.10.0，对齐 fongmi-tv/dshdroid 本机基线；APK 命名迁
      androidComponents.onVariants；assembleDebug/Release + release_assert_apk.sh 全绿。偏离原计划的说明：
      Kotlin 取 2.2.10 而非 2.4.21——为对齐本机基线且绕开 R8≥9.1.29 约束，升 2.4 时重评；AGP 钉 9.3.1——
      9.3.3 未验证且无必要，取 9.3 最新 patch 时一并回归）。2026-10-24 二次收尾：10 个 Android 模块已迁到
      AGP 9 内置 Kotlin（删 org.jetbrains.kotlin.android；AGP 9.3.1 的 POM 依赖 kotlin-gradle-plugin:2.2.10，
      故 catalog 的 kotlin=2.2.10 只喂 compose/serialization 子插件与纯 JVM 模块，必须与内置版本一致）；
      :app:shared 独立为 cn.vectory.biliview.shared（:app 独占 cn.vectory.biliview，R/BuildConfig 引用随之
      迁移 82 个文件，shared 的 manifest 里 .BVApp 改全限定）。2026-10-25 验证：versions 插件 0.52.0
      在 Gradle 9.7.1 下 dependencyUpdates 可跑通（BUILD SUCCESSFUL，报告 build/dependencyUpdates/report.txt）。
      接线：仅根 build.gradle.kts `alias(gradleLibs.plugins.versions)` 应用，故报告只覆盖根插件 classpath
      （AGP 9.3.1→9.6.0-alpha01、Kotlin 2.2.10→2.5.0-Beta2、versions 0.52.0→0.66.0、Gradle 9.7.1→9.8.2 RC），
      Android 模块的库依赖不在报告中。已知限制：0.52.0 在任务执行期调用 Task.project，Gradle 10 将报错、
      且与 configuration cache 不兼容；升 Gradle 10 或启用配置缓存前需升 versions 插件（候选 0.66.0，本次未升）。
      后续升级项：Kotlin 2.4（需 classpath 覆盖 KGP 才能抬内置 Kotlin 版本）、AGP 9.3 最新 patch；
      暂不启用 configuration cache
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
