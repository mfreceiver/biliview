# TODO

> 只记录待完成任务；完成后立即从本文件移除。
> 已完成基线（不在此列，见 git 历史）：黑名单机制移除、大陆区域拦截移除（RegionBlockScreen×2 +
> NetworkUtil）、README 重写、发版 ABI 纪律落地（debug=x86 / release 系=armeabi-v7a+arm64-v8a）、
> minSdk 23→24 对齐 mitv、buildType/flavor 裁剪（alpha、r8Test、lite 移除，仅 default × debug/release）、
> 包名与 namespace 全量迁移 `dev.aaa1115910.bv` → `cn.vectory.biliview`（9 处源码目录移动 + 1774 处
> 词边界替换，全量编译验证；`dev.aaa1115910.biliapi` 等同根独立包与上游 URL 不在范围）。

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

- [ ] 配套收尾（发版脚本主体已落地并通过审阅）：修复审阅发现的应修项——版本单调门禁可绕过
      （tag 格式不符静默放行、未比对远端 tag、前导零八进制陷阱）、signing.properties 缺键时
      debug 构建被拖死、curl 无超时、--publish-only 未校验 SHA256SUMS 含当前归档/tag annotated、
      assert 补 debuggable/testOnly/min/targetSdk/单签名者断言、mapping.txt 归档
      （origin 已统一为 http://192.168.3.252:11330/mfreceiver/biliview.git）
- [ ] 工具链对齐 mitv 评估：AGP 8.11.1 / Gradle 8.14.3 / Kotlin 2.2.0 →
      AGP 9.3.1 / Gradle 9.7.1（compileSdk/targetSdk 36 vs 35）；需评估 Compose 编译器
      与依赖联动后统一，不盲目升级
- [ ] 上游同步策略：与 aaa1115910/bv@develop 的更新如何跟进（本仓库历史已重新初始化，
      无法直接 merge，需 vendor 重导入或 patch 流程）

## 风控与账号安全

- [ ] 真机长期使用观测：buvid3/WBI 签名行为、412/-352 退避是否生效、登录方式收敛
      （网页版二维码）、多账号切换与同时在线客户端数 ≤2 纪律执行情况
