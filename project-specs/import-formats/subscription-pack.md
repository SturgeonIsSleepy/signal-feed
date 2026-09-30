# 统一订阅包

把信息源和数据栏目放在一个 JSON 文件中，统一识别为「订阅」。可以导入多份包，组件分别在「RSS/OPML」「聚合服务」「数据栏目」中管理和移除。

```json
{
  "formatVersion": 1,
  "type": "subscription-pack",
  "name": "个人订阅",
  "sources": [
    {
      "formatVersion": 1,
      "id": "my-service",
      "name": "我的聚合服务",
      "apiBaseUrl": "https://feed.example.org/",
      "accounts": ["f1", "cn", "global"]
    }
  ],
  "subscriptions": [
    { "url": "https://example.org/feed.xml", "name": "技术动态", "topic": "技术" }
  ],
  "panels": [
    {
      "formatVersion": 1,
      "id": "my-events",
      "name": "日程",
      "kind": "calendar",
      "rows": [{ "title": "活动", "startAt": "2026-10-01T12:00:00Z", "detail": "活动说明" }]
    }
  ]
}
```

## 字段和限制

- `formatVersion`：必填整数 `1`。
- `type`：必填 `subscription-pack`，用于识别合并订阅。
- `name`：可选，显示在安装确认框中，不是包的唯一标识。
- `sources`：可选服务数组，每项须符合 [服务格式](source-service.md)，每项自己的 `formatVersion` 为 `1`。
- `subscriptions`：可选 RSS 数组，每项须符合 [RSS 格式](rss-subscriptions.md)。
- `panels`：可选数据栏目数组，每项须符合 [栏目格式](data-panels.md)，每项自己的 `formatVersion` 为 `1`。

至少提供一个组件。文件最大 1 MiB；服务、RSS、栏目分别最多 100 项，包含已有安装项。每个栏目最多 500 行。地址必须是 HTTPS，不含用户名或密码。

确认前验证所有组件。服务和栏目以各自 `id` 更新；RSS 以 URL 去重，已有订阅保留原名称、主题和启停状态。订阅包不保留独立安装记录，移除时选择对应组件即可。不同服务不能复用同一账号 ID，同一服务的账号订阅可以合并。

示例地址仅说明结构，使用时替换成真实可访问接口；不要把网页地址填入 RSS URL。导入数据栏目并不会自动创建服务采集器。
