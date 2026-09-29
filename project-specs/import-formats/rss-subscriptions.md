# RSS / Atom 信息源订阅

## 给 AI 的生成要求

请按下面的 SignalFeed JSON 格式生成订阅文件。每项都需要真实可访问的 HTTPS RSS 或 Atom 地址；不要把网站主页猜成 feed 地址。主题使用我提供的名称，或先询问我。只输出一个 UTF-8 JSON 文件，文件名以 `.json` 结尾。

## JSON 格式

根对象只有一个 `subscriptions` 数组，最多 100 项：

```json
{
  "subscriptions": [
    {
      "name": "示例新闻源",
      "url": "https://example.org/feed.xml",
      "topic": "全球",
      "enabled": true
    }
  ]
}
```

每一项字段：

- `name`：必填，1 至 120 个字符，显示在账号列表和帖子上。
- `url`：必填，HTTPS RSS 或 Atom Feed 的完整地址，不能包含用户名或密码。
- `topic`：必填，1 至 32 个字符。可用 `F1`、`AI`、`玩机`、`国内`、`全球` 或自定义主题名。
- `enabled`：可选布尔值，默认 `true`。

相同 URL 会去重。每个订阅成为一个虚拟账号。每个 Feed 项目的原始链接作为帖子链接。应用只解析有标题、原文 HTTPS 链接和可识别发布时间的条目。

也可导入 OPML 文件：使用 OPML 2.0 的 `<opml><body><outline ... /></body></opml>` 结构，每个 Feed 的 `outline` 提供 `xmlUrl`、`title` 或 `text`。`category` 会用作主题，缺失时为“其他”。OPML 文件扩展名为 `.opml` 或 `.xml`。

不要输出 Markdown 代码围栏包住的 JSON，不要加入注释、尾随逗号或真实凭据。
