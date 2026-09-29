# SignalFeed 导入格式

本目录是项目文档，说明文件不会复制到 APK，也不能在安装后的应用中查看。用户导入文件仍从应用「设置 → 导入文件」选择，应用识别类型并在安装前显示确认框。下面按文件类型列出说明。

把对应说明交给 AI 时，可以直接提出：

> 请严格按照这份 SignalFeed 导入格式说明，根据我随后提供的需求生成文件。只输出完整文件内容，不要猜测账号 ID、主题 ID、接口地址或密钥；缺少必填信息时先列出需要我补充的字段。JSON 必须可解析，文件扩展名和 ZIP 目录结构必须符合说明。

| 文件类型 | 文件说明 |
|---|---|
| RSS / OPML 信息源 | [rss-subscriptions.md](rss-subscriptions.md) |
| 聚合服务信息源 | [source-service.md](source-service.md) |
| 筛选规则 | [filter-rules.md](filter-rules.md) |
| 翻译器 | [translator.md](translator.md) |
| Breaking 规则 | [breaking-rules.md](breaking-rules.md) |
| 数据栏目 | [data-panels.md](data-panels.md) |
| 主题美化 | [theme.md](theme.md) |
| ZIP 规则 Mod | [rule-mod-zip.md](rule-mod-zip.md) |
| 手机运行时 Mod ZIP | [runtime-mod-zip.md](runtime-mod-zip.md) |
| 源码补丁 ZIP（需电脑重建） | [source-patch-zip.md](source-patch-zip.md) |

这些格式用于应用导入器，不是 RSS 网站、Cloudflare Worker 或 Android APK 的通用规范。不要把真实 API Key、密码、私人订阅链接或其他密钥写入文件后交给 AI。
