# 信息源订阅文件

在设置 → 信息源订阅中导入 JSON 或 OPML。JSON 示例见 `example.json`。

```json
{"subscriptions":[{"name":"OpenAI 官方动态","url":"https://openai.com/news/rss.xml","topic":"AI"}]}
```

url 必须是 HTTPS RSS/Atom 地址，name 为显示名，topic 为 F1、AI、玩机、国内、全球之一。可选 enabled 默认为 true。按规范化 URL 去重，最多 100 个订阅。OPML 读取 outline 的 xmlUrl、title/text 和 category；未知分类归到全球。

订阅在手机抓取并写入 Room，不上传云端。正文保留信息流提供的公开内容，摘要型 RSS 无法补出原网站全文。仅收录带原文 HTTPS 链接和有效发布时间的条目。导入后及首页刷新时更新，单个源失败保留缓存并标记错误；不会因为自定义来源名称包含“官方”就提升可信度。

停用或删除订阅会隐藏对应帖子，保留缓存以便重新订阅。内置云端账号同页可启停。可以导出 JSON 备份到其他设备，文件订阅总开关位于功能与回退。
