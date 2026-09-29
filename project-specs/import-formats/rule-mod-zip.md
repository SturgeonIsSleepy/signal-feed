# ZIP 关键词规则包

## 给 AI 的生成要求

请生成一个 ZIP 文件，根目录只包含 `mod.json`，用于 SignalFeed 手机端规则包导入。只能屏蔽关键词或账号，不能包含 Java、DEX、图片或其他文件。先确认我要屏蔽的准确词语和账号 ID。规则文件名为 `mod.json`。

## ZIP 内的 mod.json 示例

```json
{
  "formatVersion": 1,
  "id": "hide-low-value-posts",
  "name": "屏蔽低价值内容",
  "version": "1.0.0",
  "hideKeywords": ["纯抽奖", "求关注"],
  "hideAccounts": ["account-id"]
}
```

- ZIP 根目录必须且只能有 `mod.json`。
- `formatVersion` 固定为整数 `1`。
- `id` 为 1 至 64 位英文字母、数字、下划线或短横线；同 ID 会替换原包。
- `name` 为 1 至 60 个字符；`version` 为 1 至 30 个字符。
- `hideKeywords`、`hideAccounts` 可省略或为空数组，每项 1 至 120 个字符，每类最多 200 项。
- 命中任意关键词或账号即隐藏帖子。卸载规则包会恢复显示，不会删除原帖。

请提供 ZIP 二进制文件，而不只是代码围栏里的 JSON。如果当前 AI 无法创建附件，请先输出 `mod.json` 的原文，并明确说明还需要将该文件单独压缩为 ZIP。
