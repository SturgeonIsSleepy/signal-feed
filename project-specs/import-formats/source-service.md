# 聚合服务与账号订阅

## 给 AI 的生成要求

请根据我指定的 SignalFeed 聚合服务生成一个 UTF-8 JSON 文件。必须使用我给出的 HTTPS API 根地址，并且 `accounts` 只能填该服务实际提供的账号 ID。不要根据显示名称猜 ID；如果我没有提供服务支持的 ID 列表，先向我索取。文件扩展名为 `.json`。

## JSON 格式

```json
{
  "formatVersion": 1,
  "name": "国内与全球热点",
  "apiBaseUrl": "https://feed.example.org/",
  "accounts": ["cn", "global"]
}
```

- `formatVersion`：必填整数，固定为 `1`。
- `name`：可选，用于说明用途。
- `id`：可选，文件的稳定标识；省略时由地址和账号列表生成。相同 `id` 再次导入会更新原文件。
- `apiBaseUrl`：必填，服务 API 根 URL，必须是 HTTPS。末尾斜线可有可无。
- `accounts`：必填，1 至 100 个唯一账号 ID 字符串。仅列表里的账号会显示、允许订阅和参加本机 Breaking 匹配。

服务需实现 SignalFeed API：`GET {apiBaseUrl}/v1/sync?cursor=...`。赛历页和模型榜原有栏目分别需要 `GET /v1/f1` 和 `GET /v1/ai`。导入这份文件不会创建新的服务器采集器，也不会让服务器自动发现账号；它只连接服务，并筛选应用要接收的账号。

可以同时安装多份聚合服务文件。同一 API 地址的账号会合并订阅，只请求该服务一次；不同地址各自保持同步进度。不同服务不能重复使用相同账号 ID。移除某份文件仅取消其中的订阅，其他文件保留。

可选择把账号清单提供给 AI，例如：

```text
jiangnan, wuxing, f1, fia, openai, radar, cn, global,
media-chinanews, media-france24, media-aljazeera, media-un
```

这只是当前示例聚合服务的账号 ID。自建服务必须使用该服务自己的 ID。
