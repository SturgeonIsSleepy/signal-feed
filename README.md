# SignalFeed

个人信息流聚合 Android App。Android 使用 Kotlin、Jetpack Compose、Room、Retrofit 和 OkHttp；服务端使用 Cloudflare Workers 与 D1。

## 功能

- X 风格首页：For You、Following、主题筛选、账号关注/屏蔽与来源详情
- F1 赛历、积分与赛果；车手和车队逐站积分图
- AI 模型榜：柱状比较、多维评分、雷达图和均值对照
- RSS/Atom 文件订阅，JSON 与 OPML 导入、JSON 导出
- Mod：规则包、手机端运行时 DEX 模块，以及从干净基线重建 APK 的源码包
- Room 离线缓存、内容过滤、逐项行为回退
- Cloudflare Worker 同步、D1 去重、F1/AI 数据和 Breaking 通知

## 项目结构

- `app/`：Android 客户端与宿主模块 API
- `worker/`：TypeScript Worker、来源适配器、D1 迁移
- `docs/`：Mod 与信息源订阅的格式规范
- `mods/`：Mod 示例、源码包基线和 DEX 打包脚本
- `subscriptions/`：JSON/OPML 导入示例

扩展入口见[规范索引](docs/EXTENSIONS.md)。

## 构建

使用 JDK 21、Android SDK 36 和 Gradle Wrapper。SDK 路径放在本机忽略文件 `local.properties`，不要提交个人路径。

```powershell
./gradlew.bat :app:assembleDebug
```

设置 `apiBaseUrl` Gradle 属性以构建连接自己 Worker 的版本。FCM 和 Artificial Analysis 凭据使用本机未跟踪配置或 Worker Secret；不要写入源码、提交记录或 APK。

Worker 开发命令：在 `worker/` 安装依赖后运行 `npm test`、`npm run typecheck`、`npm run db:local`、`npm run dev`。部署前配置自己的 Cloudflare D1 绑定和 Secrets。

## 文档

- [Mod 格式总规范](docs/CODE_MOD_SPEC.md)
- [手机运行时 DEX 模块 API 1](docs/MOBILE_MOD_SPEC.md)
- [信息源订阅格式规范](docs/SUBSCRIPTION_SPEC.md)
- [源码 Mod 干净基线](mods/baselines/signalfeed-0.7.0.zip)
- [运行时 DEX 模块示例](mods/runtime-home-example.zip)
- [订阅 JSON 示例](subscriptions/example.json)

运行时模块以宿主权限执行；源码 Mod 只能对匹配的干净基线重建 APK。只导入可信代码。卸载 Mod 会恢复 APK 代码，但不会回滚它写入的数据库、偏好或远端服务数据。
