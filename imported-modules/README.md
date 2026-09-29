# 可导入文件

本目录集中存放可以复制到手机并从 SignalFeed「设置 → 导入文件」导入的文件。应用会先识别类型，再请用户确认。格式说明统一放在项目根目录的 `project-specs/`。

- `source-subscriptions/`：RSS / Atom JSON 订阅文件。
- `app-packs/`：聚合服务、筛选规则、翻译器、Breaking、数据栏目和主题配置。
- `mods/`：可在手机端安装的规则或运行时 ZIP Mod，以及仅供电脑重建的源码补丁示例。

`mods/` 是源码、打包工具和基线目录；这里收纳的是最终导入包。源码重建基线留在 `mods/baselines/`，不要当作手机模块安装。
