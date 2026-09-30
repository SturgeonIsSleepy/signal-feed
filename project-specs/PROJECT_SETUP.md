# 项目设置规范

## 目录约定

- `project-specs/`：项目设置说明、Mod 规范、订阅规范和导入文件格式说明。
- `imported-modules/`：可以复制到手机并从应用内导入的文件。`source-subscriptions/` 放 RSS/OPML，`app-packs/` 放主题、合并订阅和内容筛选，`mods/` 放手机运行时或旧规则 ZIP。
- `mods/`：模块源码、编译脚本和源码重建基线；`source-patch-examples/` 存放电脑重建示例，`legacy-configs/` 保留历史翻译器配置。这些文件不属于当前手机导入。
- `app/` 与 `worker/`：Android 应用和 Cloudflare Worker 源码。

新增导入文件时，放进 `imported-modules/` 对应子目录；字段、目录和导入限制写入 `project-specs/import-formats/`，总览链接维护在本目录。

## 本地构建配置

Android 构建使用 JDK 21、Gradle Wrapper 和 Android SDK 36。个人 SDK 路径仅写入根目录 `local.properties`，不要提交该文件或写死机器路径。应用内读取的聚合服务地址通过 `imported-modules/app-packs/` 中的服务配置在手机上导入，不编进 APK。

FCM 客户端参数可通过本机 Gradle 属性提供。Artificial Analysis 等后端密钥只放在 Cloudflare Worker Secret。真实密钥、个人账号数据和本地凭据不得放进示例配置或版本控制。

## 导入格式说明

所有导入格式说明只保存在项目的 `project-specs/import-formats/`，供开发者和 AI 在仓库内阅读。它们不作为 Android assets，不复制到 `app/src/main/assets/`，也不打进 APK。手机端的导入入口只负责选择并解析用户文件。

## APK 修改边界

- 分支对话、子任务和临时工作树只修改源码与项目文档，不构建、替换、签名、安装或提交 APK。
- 只有主对话收到用户明确要求后，才处理 APK 产物。
- Git 忽略所有 `.apk` 文件；GitHub 检查会拒绝包含 APK 变更的分支 Pull Request。
- APK 产物与导入格式文档都不上传到源码仓库。

详细流程见 [APK 工作流与分支边界](APK_WORKFLOW.md)。
