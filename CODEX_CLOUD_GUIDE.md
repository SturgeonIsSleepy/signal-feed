# SignalFeed 的 Codex Cloud 使用指南

本文说明如何在 Codex Cloud 中为 `SturgeonIsSleepy/signal-feed` 准备环境、开始任务和检查改动。Codex Cloud 在云端为任务提供独立工作区；同一个任务可继续使用自己的保存状态，新任务从已发布的环境设置开始。保存的工作状态不能替代 Git 提交。

## 创建或选择环境

1. 在 ChatGPT 网页版或桌面版打开 Codex，新建任务并选择 **Work in → Cloud**。
2. 在 **Select environment** 中选择已有的已发布环境；首次使用时选择 **Create environment**。
3. 连接 GitHub，选择 `SturgeonIsSleepy/signal-feed`，然后点 **Get started**。让 Codex 检查仓库并准备依赖；按项目要求确认 **JDK 21、Android SDK 36**，以及运行 `worker/` 所需的 Node.js 和 npm。
4. 检查环境准备报告、安装步骤和测试结果。确认没有遗漏后保存并点 **Publish**。
5. 环境显示 **Environment published** 后，选择 **Start a new task**。之后可从这个已发布环境开始新的任务。

环境设置是可复用的工具和依赖配置；发布环境不等于提交代码。若修改安装设置，重新检查并发布后，再用新任务验证。

## 在 Cloud 中开始任务

选中已发布的 SignalFeed 环境后，用自然语言说明要完成的结果。任务描述越具体，越容易得到可检查的改动：

- **目标**：要修复或实现什么。
- **范围**：相关模块、屏幕、Worker 路由或文档路径。
- **约束**：需要保持的行为、数据格式或仓库规则。
- **验收**：要运行哪些检查，以及最终要报告什么。

可以直接这样开始：

> 在 `signal-feed` 中继续处理这个问题：……
> 先阅读 `AGENTS.md` 和相关项目规范，再定位实现。只改完成目标所需的源码或文档；运行对应检查，最后汇报改动文件、验证结果和未解决事项。

也可以使用更完整的模板：

~~~text
目标：
范围：
约束：遵守 AGENTS.md；不要修改无关模块；不要把密钥写入文件或提交。
验收：运行相关测试/类型检查，检查最终 diff，并说明结果。
~~~

## SignalFeed 的常用检查

### Worker

在 `worker/` 按仓库说明安装所需依赖，然后运行与改动相关的检查：

~~~bash
cd worker
npm test
npm run typecheck
~~~

需要验证本地 D1 迁移时，可使用 `npm run db:local`；需要手动运行 Worker 时，可使用 `npm run dev`。这些操作用于本地开发，不代表已部署到 Cloudflare。

### Android

Android 项目使用 JDK 21、Android SDK 36 和 Gradle Wrapper。Cloud 环境未配置齐全时，先让 Codex报告缺失项并补齐环境，再运行适合此次源码改动的检查。遵守下面的 APK 边界，不要为了验证普通源码或文档改动而生成安装包。

## 仓库边界与密钥

- 每次开始任务先读根目录 `AGENTS.md`；涉及构建配置时也读 `project-specs/PROJECT_SETUP.md`，涉及 APK 时读 `project-specs/APK_WORKFLOW.md`。
- 普通 Cloud 分支任务只修改源码或项目文档。只有主对话在当前任务明确收到 APK 请求后，才处理 APK；不得构建、替换、签名、安装或提交 APK，也不要通过改名或压缩绕过检查。
- 导入文件格式说明只放在 `project-specs/import-formats/`，不要复制进 Android assets 或 APK。
- 不要把密钥、个人凭据、`local.properties`、`app/google-services.json` 或 `.dev.vars` 写入提交。FCM 本机配置应保持未跟踪；Artificial Analysis 等后端密钥使用 Cloudflare Worker Secret。
- Cloud 环境若需要网络访问，只开启任务所需的包管理器或域名；需要服务凭据时使用环境支持的 Secret 配置，不要把明文凭据放进提示词、源码或日志。
- 除非任务明确要求部署，否则只做本地验证，不部署 Worker 或改动线上资源。

## 检查和交付

完成后先看 Cloud 任务的总结、测试结果和逐文件 diff。要求 Codex 说明未运行的检查及原因；发现问题时在同一任务中继续修正并复测。确认改动符合预期后，再按需要提交或创建 Pull Request。新任务从已发布环境重新开始；要继续尚未完成的工作，请回到原来的 Cloud 任务。

## 官方说明

- [Codex Cloud](https://learn.chatgpt.com/docs/cloud)
- [Cloud environments](https://learn.chatgpt.com/docs/environments/cloud-environments)
- [Prompting Codex](https://learn.chatgpt.com/docs/prompting)