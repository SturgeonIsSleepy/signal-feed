# 内容筛选：屏蔽、保留与 Breaking

把筛选和推送策略合并为一个 UTF-8 JSON 文件，可安装多份，在「设置 → 内容筛选」移除。规则判断始终使用原文、原始账号与主题 ID，界面翻译不会改变匹配结果。

```json
{
  "formatVersion": 1,
  "type": "content-filter",
  "id": "personal-focus",
  "name": "个人关注策略",
  "description": "屏蔽预告，推送 F1 重要消息",
  "rules": [
    { "action": "hide", "keywords": ["敬请期待", "抽奖转发"] },
    { "action": "allow", "accounts": ["f1"] }
  ],
  "breaking": [
    { "topics": ["F1"], "minimumImportance": 80 }
  ]
}
```

## 文件字段

- `formatVersion`：必填整数 `1`。
- `type`：建议 `content-filter`。
- `id`：必填，1 至 64 位英文字母、数字、下划线或短横线，相同 ID 再导入会替换旧包并放到判断顺序末尾。
- `name`：必填，1 至 80 字符。
- `description`：可选。
- `rules`：可选，最多 100 条屏蔽或保留规则。
- `breaking`：可选，最多 100 条 Breaking 条件。

两种数组不能同时为空。单个文件最大 1 MiB，最多保留 100 个筛选包。

## 屏蔽与保留

每条 `rules` 必填 `action`：`hide` 隐藏，`allow` 保留并停止后续导入规则判断。匹配条件：

- `accounts`：账号 ID 数组。
- `topics`：主题 ID 数组。
- `keywords`：关键词数组，在正文搜索，大小写不敏感。
- `bodyRegex`：JVM 正则，最长 240 字符，仅搜索原文前 5,000 字符。

至少一个有效条件。同类数组任一项命中即可，不同条件类别必须同时满足。按包导入顺序、包内规则顺序判断，第一个命中决定结果；都未命中时显示原帖。`allow` 不会解除账号屏蔽或其他独立功能的屏蔽，例如旧 ZIP 关键词规则。

## Breaking

每个 `breaking` 对象可以用上述条件，并增加整数 `minimumImportance`（0 至 100）。条件同时满足、重要度达标才命中，不同规则和包之间任意命中即生效。至少提供一个条件或正的重要度门槛。

嵌入数组的规则不需要单独的 `formatVersion`、`id` 或 `name`。规则选出的帖子在首页标记 Breaking，不要求后端原先已标记；通知还需关注该账号、未被屏蔽、有可同步来源和系统通知权限。没有 Breaking 规则时通知关闭。通知使用定时同步和已配置的 FCM，不能保证即时送达。

## 兼容

旧的独立筛选、[Breaking JSON](breaking-rules.md) 仍可从统一入口导入，归入「内容筛选」。[格式 1 规则 ZIP](rule-mod-zip.md) 在同一页面启停与移除，不能放入上述 JSON 数组。手机代码扩展使用格式 3 Mod。

给 AI 的要求：只填写用户指定的真实账号、主题和条件；不要猜 ID，不要生成匹配全部正文或高耗时的正则。检查正则和 JSON 转义，屏蔽内容不要使用已经翻译后的文本。
