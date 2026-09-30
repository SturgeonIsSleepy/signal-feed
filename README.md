# SignalFeed

个人信息流聚合 Android App。Android 使用 Kotlin、Jetpack Compose、Room、Retrofit 和 OkHttp；服务端使用 Cloudflare Workers 与 D1。

## 功能

- X 风格首页：For You、Following、主题筛选、账号关注/屏蔽与来源详情
- 中英文界面与主题包附加语言，设备端自动翻译及原文切换
- 浅色、深色和跟随系统；滚动收起顶栏与背景模糊
- F1 赛历、积分与赛果；车手和车队逐站积分图
- AI 模型榜：柱状比较、多维评分、雷达图和均值对照
- RSS/Atom 文件订阅，JSON 与 OPML 导入、JSON 导出
- 四类统一导入：主题包、订阅、内容筛选、手机端运行时 DEX Mod
- Room 离线缓存、内容过滤、逐项行为回退
- Cloudflare Worker 同步、D1 去重、F1/AI 数据和 Breaking 通知

## 项目结构

- `app/`：Android 客户端与宿主模块 API
- `worker/`：TypeScript Worker、来源适配器、D1 迁移
- `project-specs/`：项目设置规范、导入文件格式和 Mod 规范
- `imported-modules/`：可导入文件，分为订阅、应用配置和 ZIP Mod
- `mods/`：Mod 源码示例、源码包基线和 DEX 打包脚本
- `tools/`：本机构建与打包脚本
- `release-artifacts/`：本地 APK 版本，不进入源码仓库
- `mod-builds/`、`downloads/`：本地构建记录与第三方工具，均不进入源码仓库
- `AGENTS.md`：所有代码对话共用的项目边界和协作规则

扩展入口见[规范索引](project-specs/EXTENSIONS.md)。

导入格式文档仅保存在 `project-specs/import-formats/`，不会打进 APK。APK 只允许主对话在收到用户明确要求后处理；分支对话规则见 [AGENTS.md](AGENTS.md) 和 [APK 工作流](project-specs/APK_WORKFLOW.md)。

## 构建

使用 JDK 21、Android SDK 36 和 Gradle Wrapper。SDK 路径放在本机忽略文件 `local.properties`，不要提交个人路径。

```powershell
./gradlew.bat :app:assembleDebug
```

聚合服务地址在安装后通过应用内导入配置，不需要编进 APK。FCM 客户端配置保存在本机未跟踪配置中，Artificial Analysis 密钥保存在 Worker Secret；不要写入源码或提交记录。

Worker 开发命令：在 `worker/` 安装依赖后运行 `npm test`、`npm run typecheck`、`npm run db:local`、`npm run dev`。部署前配置自己的 Cloudflare D1 绑定和 Secrets。

## 文档

- [项目设置规范](project-specs/PROJECT_SETUP.md)
- [导入说明合集与可修改示例](project-specs/import-formats/README.md)
- [APK 工作流与分支边界](project-specs/APK_WORKFLOW.md)
- [Mod 格式总规范](project-specs/CODE_MOD_SPEC.md)
- [手机运行时 DEX 模块 API 1](project-specs/MOBILE_MOD_SPEC.md)
- [信息源订阅格式规范](project-specs/SUBSCRIPTION_SPEC.md)
- [源码 Mod 干净基线](mods/baselines/signalfeed-0.7.0.zip)
- [运行时 DEX 模块示例](imported-modules/mods/runtime-home-example.zip)
- [订阅 JSON 示例](imported-modules/source-subscriptions/example.json)
- [全部导入文件](imported-modules/)

运行时模块以宿主权限执行；源码 Mod 只能对匹配的干净基线重建 APK。只导入可信代码。卸载 Mod 会恢复 APK 代码，但不会回滚它写入的数据库、偏好或远端服务数据。
