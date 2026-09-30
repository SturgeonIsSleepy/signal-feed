# 手机导入文件

复制文件到手机，从 SignalFeed「设置 → 导入文件」选择，确认识别类型后安装。格式和示例说明见 [导入说明合集](../project-specs/import-formats/README.md)，说明仅保存在项目中。

当前导入分四类：

- 主题包：深浅色配色和附加语言字典
- 订阅：RSS/OPML、聚合服务账号和数据栏目
- 内容筛选：屏蔽、保留与 Breaking 策略
- 手机 Mod：含预编译 DEX 的运行时 ZIP，安装和卸载均在手机完成

## 目录

- `source-subscriptions/`：RSS/Atom JSON 和 OPML
- `app-packs/`：四类中的主题、订阅和内容筛选 JSON，保留兼容的独立服务、Breaking 和数据栏目文件
- `mods/`：格式 3 手机 Mod 和兼容格式 1 规则 ZIP

优先使用 `theme-midnight-ja.json`、`subscription-personal.json`、`content-focus.json` 三个合并示例。默认不会安装示例，服务地址的可达性取决于手机网络。

电脑重建示例移动到项目根的 `mods/source-patch-examples/`；历史翻译器 JSON 保留在 `mods/legacy-configs/`，不能用当前手机入口导入。模块源码与基线在项目根 `mods/`，不要当作手机模块安装。
