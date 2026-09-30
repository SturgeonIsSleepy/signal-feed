# Breaking 推送规则

当前建议将条件放在 [内容筛选包](filter-rules.md) 的 `breaking` 数组中，与屏蔽策略一起导入。数组成员只需要匹配字段，不需要单独的版本、ID 或名称。以下独立 JSON 为兼容格式，在应用中仍归入「内容筛选」。

## 给 AI 的生成要求

请将我的推送条件转换成 SignalFeed Breaking JSON。仅填写我明确给出的账号 ID、主题、关键词或正则。至少提供一个筛选条件或正的重要性门槛，不要生成“全部帖子都推送”的配置。文件扩展名为 `.json`。

## JSON 格式

```json
{
  "formatVersion": 1,
  "id": "f1-important-decisions",
  "name": "F1 重要决定",
  "description": "只推送 FIA 的重要判罚和决定",
  "accounts": ["fia"],
  "topics": ["F1"],
  "keywords": ["penalty", "decision"],
  "bodyRegex": "(?i)stewards.{0,40}decision",
  "minimumImportance": 60
}
```

- `formatVersion`：必填整数 `1`。
- `id`：必填，1 至 64 位英文字母、数字、下划线或短横线。
- `name`：必填，1 至 80 个字符。
- `description`：可选说明。
- `accounts`、`topics`、`keywords`：可选字符串数组。同类中任一值匹配即可。
- `bodyRegex`：可选 JVM 正则，最多 240 个字符，在正文前 5,000 字符内搜索。
- `minimumImportance`：可选整数 0 至 100；大于 0 可单独构成筛选门槛。

同一配置中所有非空条件类别都要匹配，重要性也必须达到门槛。多个 Breaking 配置之间按“任意一个命中”处理。账号和主题 ID 必须与已导入信息源一致。应用只有导入 Breaking 规则后才会请求通知权限并安排本地定时检查；还需有至少一个可刷新的来源。
