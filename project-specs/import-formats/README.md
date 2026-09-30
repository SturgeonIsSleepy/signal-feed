# SignalFeed 导入说明合集

所有文件从「设置 → 导入文件」选择。应用先识别类型，显示名称与内容摘要，确认后安装。导入文件归为四类，不再单独导入翻译器或语言包。

说明只放在项目中，不打进 APK。示例不会自动安装，新安装的 App 不连接任何信息源。

## 四类文件

| 类别 | 包含内容 | 格式与管理位置 |
|---|---|---|
| 主题包 | 浅色与深色配色、附加界面语言字典 | JSON，设置 → 主题包；语言在「语言与外观」选择 |
| 订阅 | RSS/Atom、聚合服务账号、自定义数据栏目 | JSON 或 OPML，设置 → 订阅 |
| 内容筛选 | 屏蔽、保留、Breaking 条件 | JSON，设置 → 内容筛选；兼容旧格式 1 规则 ZIP |
| 手机 Mod | 页面、信息流与运行逻辑扩展 | 含预编译 DEX 的格式 3 ZIP，设置 → 手机 Mod |

## 详细字段

- [主题包与语言字典](theme.md)
- [统一订阅包](subscription-pack.md)
  - [RSS / Atom 与 OPML](rss-subscriptions.md)
  - [聚合服务及账号](source-service.md)
  - [自定义日程、榜单与卡片](data-panels.md)
- [内容筛选与 Breaking](filter-rules.md)
  - [Breaking 条件细节及旧文件](breaking-rules.md)
  - [旧格式规则 ZIP](rule-mod-zip.md)
- [可直接在手机安装的 Mod ZIP](runtime-mod-zip.md)

## 可直接修改的示例

- [深浅色主题与日语字典](../../imported-modules/app-packs/theme-midnight-ja.json)
- [信息源和数据栏目合并订阅](../../imported-modules/app-packs/subscription-personal.json)
- [屏蔽与 Breaking 合并策略](../../imported-modules/app-packs/content-focus.json)
- [手机运行时 Mod](../../imported-modules/mods/runtime-home-example.zip)

## 语言与自动翻译

在「设置 → 语言与外观」选择中文、English 或主题包提供的语言。自动翻译默认开启，目标语言跟随界面语言，可以单独关闭；详情页也能查看原文。首次翻译需联网下载对应语言模型，完成后设备端翻译可离线使用。译文缓存按原文和目标语言区分。

账号标识、链接、数值不翻译；PDF 原始页面保持原样。未知或不支持的语言、下载失败时保留原文，不隐藏内容。机器翻译可能误译专业术语，需精确引用时查看原文。

## 兼容与回退

旧的服务、RSS、数据栏目 JSON 仍归入「订阅」，旧的筛选和 Breaking JSON 仍归入「内容筛选」。主题替换后立即生效，移除恢复默认配色；移除选中的附加语言后，界面回到中文。订阅和筛选可同时导入多份，移除配置保留帖子缓存。

旧翻译器 JSON 不再接受导入，改用内置翻译设置，见 [翻译说明](translator.md)。格式 2 源码补丁与重建请求不能在手机安装，存放在 `mods/source-patch-examples/`，见 [电脑重建说明](source-patch-zip.md)。手机 Mod 卸载后需重启应用，代码才从进程卸载；模块自行修改的数据不自动回滚。

## 给 AI 的生成要求

> 请按本合集对应类别的字段规范生成 SignalFeed 导入文件。使用我提供的真实账号、主题 ID 和 HTTPS 地址，不要猜测接口或密钥。JSON 必须可以解析。订阅包含数据栏目时使用 subscription-pack，筛选与 Breaking 合并为 content-filter，语言放入 theme-pack。手机 Mod 必须输出包含 mod.json 和预编译 classes.dex 的格式 3 ZIP。说明缺失的必填信息；不要把源码补丁或 APK 伪装成手机 Mod。

JSON/OPML 最大 1 MiB，ZIP 的限制见相应说明。不要将真实密钥、私人订阅地址交给 AI 或写进公开示例。
