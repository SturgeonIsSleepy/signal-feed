# SignalFeed Mod 格式总规范

SignalFeed 有三类 ZIP Mod。先按需要选择格式；三种格式不能互换。

| 格式 | 用途 | 安装与移除 |
|---|---|---|
| 1：规则包 | 按关键词或账号 ID 隐藏帖子 | 手机导入、启停、删除，立即生效 |
| 2：源码包 | 替换、新增或删除宿主源码和资源 | 电脑从固定基线构建 APK，再覆盖安装；当前手机统一导入入口不接收此格式 |
| 3：运行时模块 | 通过宿主公开 API 改信息流、请求地址或首页 | 手机导入预编译 DEX，退出并重开 App 生效；详见[运行时模块规范](MOBILE_MOD_SPEC.md) |

规则包不是可执行代码。源码包构建和运行时模块都能执行代码，拥有相应的构建或应用权限；只安装可信作者的文件。

## 格式 1：规则包

### ZIP 结构

ZIP 根目录只能有一个 `mod.json`，不得放源码目录或其他文件。压缩包最多 32 个条目，解压内容最多 256 KiB。

```text
filter.zip
└── mod.json
```

### 清单字段

UTF-8 JSON，不带 BOM。必填字段为 `formatVersion`、`id`、`name`、`version`。

| 字段 | 类型 | 限制 |
|---|---|---|
| `formatVersion` | 整数 | 固定为 `1` |
| `id` | 字符串 | 必须匹配 `[A-Za-z0-9_-]{1,64}`；相同 ID 导入时替换原规则 |
| `name` | 字符串 | 去除首尾空格后为 1–60 字符 |
| `version` | 字符串 | 去除首尾空格后为 1–30 字符 |
| `hideKeywords` | 字符串数组 | 可省略；最多 200 项，每项去除首尾空格后 1–120 字符，重复值合并 |
| `hideAccounts` | 字符串数组 | 可省略；最多 200 项，每项去除首尾空格后 1–120 字符，填写稳定的 Account ID，重复值合并 |
| `enabled` | 布尔值 | 可选，默认 `true`；导入时启用 |

示例：

```json
{
  "formatVersion": 1,
  "id": "hide-chatter",
  "name": "隐藏低信息帖子",
  "version": "1.0.0",
  "hideKeywords": ["仅供参考", "转发抽奖"],
  "hideAccounts": ["account-id-to-hide"]
}
```

关键词按帖子正文作不区分大小写的包含匹配；账号规则按 Account ID 完全匹配。规则包不修改帖子、数据库或来源数据。

## 格式 2：源码包

源码包本身不在手机上动态运行。它声明要对干净源码基线进行的整文件操作，电脑构建器逐项校验后生成新的 APK。当前构建器只接受与项目版本相符的 `signalfeed-0.7.0` 基线，目标限于 `app/src/main/` 下的文件和 `app/build.gradle.kts`；不能修改 Worker、Gradle Wrapper 或项目级构建文件。0.6.0 是历史基线，当前构建器不接受它。应用后续升级时，应另建并固定新基线，不能混用不同基线的包；不要更改 applicationId 或降低 versionCode。

### ZIP 结构

`mod.json` 位于 ZIP 根目录。每个 `replace` 文件都放在 `files/` 下，并保留项目相对路径；`delete` 操作不需要对应文件。

```text
my-source-mod.zip
├── mod.json
└── files/
    └── app/src/main/java/cc/ccwu/signalfeed/FeedUi.kt
```

源码包读取器限制压缩 ZIP 原始大小最多 16 MiB、最多 512 个条目、解压内容最多 32 MiB，`mod.json` 最多 256 KiB。构建器也会限制 ZIP 路径、重复文件和总展开大小。

### 清单

```json
{
  "formatVersion": 2,
  "type": "source-patch",
  "baseId": "signalfeed-0.7.0",
  "id": "my-ui",
  "name": "我的界面",
  "version": "1.0.0",
  "files": [
    {
      "path": "app/src/main/java/cc/ccwu/signalfeed/FeedUi.kt",
      "action": "replace",
      "beforeSha256": "替换为干净基线原文件的 64 位 SHA-256"
    }
  ]
}
```

| 字段 | 类型 | 限制 |
|---|---|---|
| `formatVersion` | 整数 | 固定为 `2` |
| `type` | 字符串 | 固定为 `source-patch` |
| `baseId` | 字符串 | 固定为 `signalfeed-0.7.0`，必须匹配构建器基线 |
| `id` | 字符串 | `[A-Za-z0-9_-]{1,64}`；同一请求中不能重复 |
| `name` | 字符串 | 1–60 字符 |
| `version` | 字符串 | 1–30 字符 |
| `files` | 数组 | 1–256 项；一个包内路径不能重复 |

每个 `files` 项由以下字段组成：

- `path`：项目相对路径。只允许 `app/src/main/` 下的路径或 `app/build.gradle.kts`，不允许绝对路径、反斜线、冒号或 `..`。`app/src/main/assets/code-mod...` 是保留路径。
- `action`：`replace` 或 `delete`。`replace` 表示整文件替换，不是按行合并；新增文件也用 `replace`。
- `beforeSha256`：对基线中现有文件必填，填写其 SHA-256；新增文件填 `null` 或省略。构建器会检查文件是否存在以及摘要是否与基线吻合，防止包套用到意外版本。

用 PowerShell 计算原文件摘要：

```powershell
Get-FileHash -Algorithm SHA256 .\path\to\baseline-file
```

同一重建请求中，两个包不能修改同一路径；发生冲突时构建失败。需要联合修改同一文件时，把修改整合在一个源码包内。

### 用电脑重建源码包

当前统一导入入口只安装格式 1 和格式 3 的 ZIP。格式 2 的手机管理页面尚未接入设置，因此不能在手机上导入或导出重建请求。电脑构建器接受包含 `request.json` 与 `packages/<源码包 SHA-256>.zip` 的重建请求 ZIP；请求中的 `mods` 数组列出每个源码包的清单和 `sha256`。准备并核对请求后，将其放到包含 `tools/rebuild-mods.ps1` 的项目目录。构建器不会在手机上运行源码包。

```powershell
# 构建并通过已连接的 Android 设备覆盖安装
pwsh -File tools/rebuild-mods.ps1 `
  -Request .\signalfeed-rebuild.zip `
  -AllowCodeMods `
  -Install
```

需要 JDK、Android SDK、Gradle 所需依赖以及已连接且允许调试的设备。SDK 路径通过项目本机文件 `local.properties` 提供，该文件不应上传。只构建、不安装时省略 `-Install`，然后自行核对 APK 再安装。

### 移除与代码恢复

从重建请求的 `mods` 数组中移除对应包后重新构建。工具从只读基线复制一份全新源码，再应用当前清单中的包；它不在上一次 Mod 代码上打反向补丁。构建成功并覆盖安装后，已移除包的源码和包清单不会进入新 APK。

恢复全部基线代码时，使用空清单重新构建：

```json
{"formatVersion":1,"baseId":"signalfeed-0.7.0","mods":[]}
```

空请求不需要 `-AllowCodeMods`。构建或安装失败时，当前已安装 APK 不会被工具主动卸载。覆盖安装保留 App 数据；源码恢复不会撤销 Mod 先前写入的数据库、偏好、文件或远端服务变化。基线按版本固定保存，不应编辑或用旧基线覆盖更高版本的数据。

## 格式 3：运行时 DEX 模块

手机直接安装预编译代码，重启后使用宿主 API 1。模块只可通过 `SignalMod` 暴露的入口扩展首页、帖子变换和 HTTPS 请求地址，不可热替换 APK 已加载的类、系统权限或 Android Manifest。详见[运行时 DEX 模块规范](MOBILE_MOD_SPEC.md)。

## 选择依据

- 只需要隐藏内容：使用格式 1。
- 需要改 Compose 页面、Manifest、资源或应用源码：使用格式 2，且必须为正确基线制作源码包。
- 需要手机直接安装、启停和卸载代码：使用格式 3，但实现只能落在宿主 API 提供的扩展点内。
