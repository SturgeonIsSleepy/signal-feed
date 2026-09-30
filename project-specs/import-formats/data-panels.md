# 自定义数据栏目

数据栏目现在归入「订阅」，建议放在 [统一订阅包](subscription-pack.md) 的 `panels` 数组中，与来源一起安装。下面的单个栏目 JSON 仍兼容导入，管理位置为「设置 → 订阅 → 数据栏目」。可以同时安装多个栏目，数据页使用顶部标签切换。

## 给 AI 的生成要求

请将我需要展示的日程、排名、卡片或列表整理为 SignalFeed 数据栏目 JSON。优先把给定内容直接写入 `rows`，这样离线也能显示。只有我提供了真实 HTTPS JSON 接口时才填写 `url`。不要猜 API 地址或密钥。文件扩展名为 `.json`。

## 本地内容示例

```json
{
  "formatVersion": 1,
  "id": "my-events",
  "name": "我的日程",
  "description": "近期活动安排",
  "kind": "calendar",
  "rows": [
    {
      "title": "活动名称",
      "startAt": "2026-10-01T12:00:00Z",
      "detail": "地点或补充信息",
      "url": "https://example.org/event"
    }
  ]
}
```

## 字段

- `formatVersion`：必填整数 `1`。
- `id`：必填，1 至 64 位英文字母、数字、下划线或短横线。同 ID 会替换。
- `name`：必填，1 至 80 个字符，用作栏目标签。
- `description`：可选栏目说明。
- `kind`：必填，`cards`（普通卡片）、`calendar`（日程）、`leaderboard`（带比例条的排名）、`f1-calendar`、`f1-points` 或 `ai-models`。
- `rows`：可选，最多 500 个对象。对象字段由下面的映射项指定。静态数据请提供此字段。
- `url`：可选 HTTPS JSON 地址。请求结果必须是一个 JSON 对象，里面有数组字段（默认字段名 `rows`）；接口失败时继续显示文件内的 `rows`。
- `rowsKey`：可选，接口 JSON 中数组的字段名，默认 `rows`。
- `titleKey`、`valueKey`、`detailKey`、`linkKey`、`timeKey`：可选行字段映射，默认分别为 `title`、`value`、`detail`、`url`、`startAt`。

日历时间接受 Unix 毫秒数字或 ISO-8601 字符串，例如 `2026-10-01T12:00:00Z`。排行榜 `valueKey` 应为数字；所有行按最大数值绘制比例条。卡片和日程中的链接需为 HTTPS 才可打开。

`f1-calendar`、`f1-points`、`ai-models` 会显示聚合服务的现有接口栏目，需要另行导入聚合服务 JSON。它们不能把任意格式的赛历或模型数据自动转换成 F1/AI 接口格式；自定义日程和排名请用 `calendar` 或 `leaderboard` 加 `rows`。
