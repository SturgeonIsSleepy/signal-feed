# 信息源订阅文件

在设置 → 导入文件中导入 JSON 或 OPML，随后在信息源订阅中管理。样例保存在 `../imported-modules/source-subscriptions/`。

```json
{"subscriptions":[{"name":"OpenAI 官方动态","url":"https://openai.com/news/rss.xml","topic":"AI"}]}
```

url 必须是 HTTPS RSS/Atom 地址，name 为显示名，topic 可使用 F1、AI、玩机、国内、全球或自定义名称，长度为 1 至 32 个字符；省略时为“其他”。可选 enabled 默认为 true。按规范化 URL 去重，最多 100 个订阅。OPML 读取 outline 的 xmlUrl、title/text 和 category；缺少 category 时归到“其他”。

订阅在手机抓取并写入 Room，不上传云端。正文保留信息流提供的公开内容，摘要型 RSS 无法补出原网站全文。仅收录带原文 HTTPS 链接和有效发布时间的条目。导入后及首页刷新时更新，单个源失败保留缓存并标记错误；不会因为自定义来源名称包含“官方”就提升可信度。

停用或删除订阅会隐藏对应帖子，保留缓存以便重新订阅。可以导出 JSON 备份到其他设备，文件订阅总开关位于功能与回退。

项目提供[通用 JSON 样例](../imported-modules/source-subscriptions/example.json)和[Tibo 公开 RSS 镜像样例](../imported-modules/source-subscriptions/tibo-x.json)。Tibo 样例使用第三方公开镜像，不是 X 官方 API；镜像停机、延迟或格式变化时订阅会失败。RSS 解析不保证兼容网页 HTML、需要 Cookie/登录的私有 feed 或 JSON Feed。
