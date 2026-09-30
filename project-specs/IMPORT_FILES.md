# SignalFeed 导入与阅读设置

新安装的 App 没有信息源、筛选策略、数据栏目、主题或 Mod。界面内置中文与英文，设备端翻译默认开启。所有示例只保存在项目中，需要复制到手机后自行导入。

从「设置 → 导入文件」选择文件，应用自动识别四类内容，显示名称、类型和摘要，确认后安装。完整字段和生成要求见 [导入说明合集](import-formats/README.md)，说明不会打进 APK。

## 四类导入

| 类别 | 文件 | 示例 |
|---|---|---|
| 主题包 | 深浅色配色与语言字典 JSON | [午夜蓝和日语](../imported-modules/app-packs/theme-midnight-ja.json) |
| 订阅 | RSS/OPML、服务账号与数据栏目；可合并 JSON | [个人合并订阅](../imported-modules/app-packs/subscription-personal.json) |
| 内容筛选 | 屏蔽、保留与 Breaking 合并 JSON | [个人关注策略](../imported-modules/app-packs/content-focus.json) |
| 手机 Mod | 格式 3 ZIP，预编译 DEX 与资源 | [首页模块](../imported-modules/mods/runtime-home-example.zip) |

服务与 RSS、筛选包、数据栏目都支持多份。重复服务或栏目 ID 更新配置，RSS 按 URL 去重。管理入口分别为「主题包」「订阅」「内容筛选」「手机 Mod」，只有一个文件选择入口。

## 阅读与外观

「语言与外观」提供应用语言、自动翻译、浅色/深色/跟随系统、模糊顶栏和滚动隐藏顶栏。附加语言放在主题包中；关闭自动翻译不改变界面语言，详情可单独查看原文。首次翻译需要联网下载模型；无法翻译时保留原文。PDF 原始页面保持原样。

「功能与回退」保留现有交互开关。删除导入配置只停用配置并保留缓存；手机 Mod 安装、启停和卸载后需退出重开，让代码彻底从进程卸载。模块写入的数据不自动回滚。

## 兼容文件

旧的独立聚合服务、RSS 和栏目 JSON 归入订阅，独立 Breaking 与规则 JSON 归入内容筛选，格式 1 规则 ZIP 也可在内容筛选页启停与移除。

独立翻译器 JSON 不再导入，历史示例放在 `mods/legacy-configs/`。电脑源码补丁和恢复重建请求放在 `mods/source-patch-examples/`，不能在手机安装。修改宿主或系统权限声明仍需重建 APK；具体边界见 [手机模块规范](MOBILE_MOD_SPEC.md)。
