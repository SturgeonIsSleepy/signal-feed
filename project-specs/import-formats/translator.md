# 翻译器配置

## 给 AI 的生成要求

请根据我选择的翻译方式生成 SignalFeed 翻译器 JSON。设备端翻译只需本地模式。云端模式必须使用我提供的 HTTPS 服务地址；不要猜接口，不要在文件里放 API Key、密码或令牌。文件扩展名为 `.json`。

## 设备端翻译

```json
{
  "formatVersion": 1,
  "id": "device-translator",
  "name": "设备端翻译",
  "mode": "device"
}
```

设备端使用 Google ML Kit 下载语言模型并在本机翻译，首次使用需要网络下载支持的语言包。

## 云端翻译

```json
{
  "formatVersion": 1,
  "id": "my-translation-service",
  "name": "我的翻译服务",
  "mode": "worker",
  "url": "https://translate.example.org/",
  "fallback": "device"
}
```

- `formatVersion`：必填整数 `1`。
- `id`：必填，1 至 64 位英文字母、数字、下划线或短横线。
- `name`：必填，1 至 80 个字符。
- `mode`：必填，`device` 或 `worker`。
- `url`：`worker` 模式必填的 HTTPS 服务根地址。
- `fallback`：可选，设为 `device` 时云端失败后尝试设备端翻译。

当前客户端对云端服务执行 `POST {url}/v1/translate?postId=...&sourceHash=...`，期望响应 JSON 含相同的 `sourceHash` 和非空 `body`。服务端应只翻译已发布帖子的内容，并校验请求中的原文哈希。导入的第一个翻译器生效，建议只保留一个配置。
