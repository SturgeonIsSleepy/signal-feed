# SignalFeed 协作规则

## APK 边界

- 分支对话、子任务和临时工作树只能改源码或项目文档，不得构建、替换、签名、安装或提交 APK。
- 只有主对话在本轮收到用户明确要求后，才能处理 APK 产物。
- 不得把 `.apk` 文件放进提交或上传到 GitHub。仓库 `.gitignore` 和 Pull Request 检查会拦截 APK 文件。
- 导入格式说明放在 `project-specs/import-formats/`，不得作为 App assets 或打进 APK。

细节见 [APK 工作流与分支边界](project-specs/APK_WORKFLOW.md)。
