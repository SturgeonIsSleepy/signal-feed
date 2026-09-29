# 配色主题

## 给 AI 的生成要求

请根据我描述的视觉风格生成 SignalFeed 配色主题 JSON。为每个颜色提供完整不透明的 `#RRGGBB` 值；正文与背景需保持足够对比度。当前导入器接受配色，不接受图片、字体文件、布局代码或 APK。文件扩展名为 `.json`。

```json
{
  "formatVersion": 1,
  "name": "雾蓝",
  "primary": "#216BCE",
  "background": "#F5F8FD",
  "surface": "#FFFFFF",
  "text": "#142032"
}
```

- `formatVersion`：必填整数 `1`。
- `name`：可选主题名。
- `primary`：主色，用于按钮、选中状态和链接。
- `background`：页面背景。
- `surface`：卡片和内容表面。
- `text`：主要文字颜色。

四种颜色均可省略，省略时保留应用默认色；提供时必须为 `#` 加六位十六进制数字，例如 `#A1B2C3`。一次只启用一个主题。移除主题后恢复默认配色。
