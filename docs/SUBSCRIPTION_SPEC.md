# SignalFeed 信息源订阅文件规范

本规范适用于“设置 → 信息源订阅”的本地文件导入和导出。文件只是订阅清单；文章由 App 直接从 RSS/Atom 地址拉取并写入本机 Room，不需要后台账号或额外付费 API。

## 1. JSON 格式

UTF-8 JSON，最大导入文件为 1 MiB。根节点必须为对象且包含 `subscriptions` 数组；每个文件最多 100 条。`url` 和 `name` 必填；`topic` 可省略并默认为“全球”；`enabled` 可省略并默认为 `true`。

```json
{
  "subscriptions": [
    {
      "name": "OpenAI 官方动态",
      "url": "https://openai.com/news/rss.xml",
      "topic": "AI",
      "enabled": true
    },
    {
      "name": "F1 新闻",
      "url": "https://example.org/f1/feed.xml",
      "topic": "F1"
    }
  ]
}
```

| 字段 | 类型 | 限制 |
|---|---|---|
| `subscriptions` | 数组 | 必须存在，最多 100 条；空数组表示没有文件订阅 |
| `name` | 字符串 | 必须非空，最多 120 字符；仅为用户自定义显示名称，不代表可信级别 |
| `url` | 字符串 | 必须是 HTTPS RSS/Atom 绝对地址，不能含用户名或密码；不接受 HTTP |
| `topic` | 字符串 | `F1`、`AI`、`玩机`、`国内`、`全球`；省略为 `全球`，其他值报错 |
| `enabled` | 布尔值 | 可选，默认 `true`；`false` 会保留订阅但跳过抓取并隐藏其帖子 |

同一导入文件内按规范化后的 HTTPS URL 去重；已存在的相同 URL 不重复添加。导入使用合并，不会删除文件中未提及的订阅。导出包含当前全部文件订阅和启用状态。

## 2. OPML 格式

接受 OPML 1.0/2.0 XML。包含 RSS 地址的 `<outline>` 必须有 `xmlUrl` 属性；显示名称优先使用 `title`，否则用 `text`，都缺失时用 URL。`category` 可填上述主题之一；缺失或不支持时归入“全球”。例如：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>我的订阅</title></head>
  <body>
    <outline text="AI">
      <outline text="OpenAI 官方动态"
               title="OpenAI 官方动态"
               type="rss"
               xmlUrl="https://openai.com/news/rss.xml"
               category="AI" />
    </outline>
  </body>
</opml>
```

导入器逐项读取带 `xmlUrl` 的 outline；不继承父目录 `category`，所以需要正确主题时，应在每条 RSS outline 上显式设置 `category`。未知值使用“全球”。OPML 只用于导入，导出统一写为 JSON。

## 3. 被订阅地址必须返回的内容

地址须可直接通过 HTTPS 获取公开 RSS 2.0、RDF/RSS 或 Atom XML。每条文章至少需有标题、HTTPS 原文链接和可解析的发布时间。支持常见 RSS `pubDate`、Atom `published`/`updated` 和 RDF `dc:date`，可解析 RFC 1123 日期或 ISO-8601 日期。缺失任一必要字段的文章跳过。

正文从 RSS `content:encoded` 或 `description`、Atom `content` 或 `summary` 中读取，转换 HTML 为文本。只返回摘要的 feed 就只能显示摘要；App 不会自动访问文章页猜测或补齐全文。每个 feed 响应最多 4 MiB、每次最多读 100 篇有效条目；每个 URL 请求超时为 20 秒。

## 4. 本地存储、去重和可信标记

订阅列表保存在 App 私有偏好中，不上传 Worker。订阅文章保存在 Room，与云端帖子处于同一首页信息流。账号 ID 由订阅 URL 的 SHA-256 前 12 字节生成；帖子 ID 再由规范化原文 URL 的 SHA-256 前 12 字节生成。同一订阅重复抓取更新相同帖子，不重复新建；不同订阅的相同原文不会自动合并。

订阅帖子统一标为 `UNCONFIRMED`、重要性 55、非 Breaking。名称中出现“官方”不会提高可信度，订阅源之间也不会自动互相核验。Following 与 For You 沿用首页排序规则。

停用订阅或删除订阅只隐藏帖子并停止后续抓取，已有帖子和来源缓存留在本机，以便重新添加同 URL 后继续显示。恢复订阅总开关会重新抓取仍启用的订阅。首页刷新会按列表顺序逐个请求每个启用来源；失败的来源标为 `ERROR`，已有内容保留，其他来源继续处理。

## 5. 添加、调试和分发

1. 确认 URL 在手机网络环境可直接返回 XML，而不是登录页、反爬验证 HTML 或跳转到网页首页。
2. 确认文章每条含永久 HTTPS 链接和有效时间；相对链接应按 feed URL 能正确解析。
3. 将条目按本规范写入 JSON；文件过大或某条字段不合法时，整份文件会拒绝导入。
4. 在 App 中选择订阅文件，预览来源名和主题后确认。新订阅随即开始抓取；失败可在账号与来源状态中排查，并在网络恢复后刷新。
5. 跨设备迁移使用 JSON 导出文件；文章缓存不会随订阅文件传输，目标设备首次抓取后才出现内容。

项目提供[JSON 样例](../subscriptions/example.json)。RSS 解析只依赖公开 feed，不保证兼容网页 HTML、需要 Cookie/登录的私有 feed、JSON Feed 或网页抓取规则。
