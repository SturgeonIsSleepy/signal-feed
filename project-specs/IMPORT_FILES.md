# SignalFeed 导入文件

新安装的应用没有信息源、筛选规则、翻译器、Breaking 规则、数据栏目或主题。可导入文件统一保存在项目的 `imported-modules/`，复制到手机后从「设置 → 导入文件」选择。应用识别类型并显示确认框；移除配置会立即停用，缓存保留。

每类导入的完整字段规范和给 AI 的生成指令见 [导入格式说明索引](import-formats/README.md)。说明只存放在项目文件中，不打包到 APK。

## 信息源

RSS/Atom 使用 JSON 或 OPML 文件，格式见 [订阅文件规范](SUBSCRIPTIONS.md)。样例在 `../imported-modules/source-subscriptions/`。聚合服务文件示例为 `../imported-modules/app-packs/source-signalfeed.json` 和 `source-news.json`。其 `apiBaseUrl` 是 HTTPS 服务地址，`accounts` 是明确订阅的账号 ID 列表。可同时导入多份文件；同一服务地址的账号合并订阅，不同服务分别保存同步进度。不同服务不能使用相同账号 ID，重复导入同一文件会更新它。没有导入服务文件时，应用不连接聚合后端。赛历和 AI 原服务数据也需要此文件。

## 筛选规则

`../imported-modules/app-packs/filter-quiet.json` 演示 `rules` 数组。每条规则至少包含一个匹配条件：`accounts`、`topics`、`keywords` 或 `bodyRegex`。`action` 为 `hide` 或 `allow`，按文件导入顺序和规则顺序，首个命中的规则生效；没有命中则显示原帖。规则仅影响本机显示，不修改原始内容。

## 翻译器

`../imported-modules/app-packs/translator-device.json` 启用设备端翻译。`translator-worker.json` 指向支持翻译接口的 HTTPS 服务，可设置 `fallback: "device"`。应用没有默认翻译器；导入后详情页才显示翻译按钮。多个文件存在时使用列表首项，因此一次只保留一个翻译器。

## Breaking

`../imported-modules/app-packs/breaking-f1.json` 演示账号、主题、关键词、正文正则和最低重要度。一个文件内的非空条件必须同时满足，多个文件之间任一满足即生效。没有导入规则时不请求通知权限，也不会订阅 Breaking 推送。

Tibo 示例需先导入 `../imported-modules/source-subscriptions/tibo-x.json`，再导入 `../imported-modules/app-packs/breaking-tibo-quota-reset.json`。规则的账号 ID 对应这份订阅的 URL；若修改订阅地址，应同步更新规则中的 `accounts`。

## 数据栏目

`../imported-modules/app-packs/` 中的 `data-f1-calendar.json`、`data-f1-points.json` 和 `data-ai-models.json` 打开原有数据页，需要聚合服务提供相应接口。`data-custom-calendar.json`、`data-custom-ranking.json` 和 `data-custom-cards.json` 演示任意日程、榜单和卡片。自定义栏目可在文件内放 `rows`，也可设置 HTTPS `url` 读取返回 `rows` 数组的 JSON；`rowsKey`、`titleKey`、`valueKey`、`detailKey`、`linkKey`、`timeKey` 可映射字段。文件导入的内容立即离线可见。

## 主题与 Mod

`../imported-modules/app-packs/theme-blue.json` 提供基础配色：`primary`、`background`、`surface`、`text`，均为 `#RRGGBB`。主题文件移除后恢复默认配色。运行时 ZIP Mod 从统一入口安装，在「Mod 管理」停用与卸载，规范见 `MOBILE_MOD_SPEC.md`。运行时 Mod 可以改动界面和数据流，Android 系统及已安装 APK 本身仍不能由 Mod 直接替换；需要重新打包 APK 的改动继续使用源码 Mod。可导入 ZIP 放在 `../imported-modules/mods/`。
