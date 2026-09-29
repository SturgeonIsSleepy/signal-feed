# 手机运行时 Mod ZIP

## 给 AI 的生成要求

请按 SignalFeed「手机模块」格式生成模块。先实现所需行为，再按本说明和项目内 `mods/api1/build-module.ps1` 编译 Java 源码为 DEX，并打包成 ZIP。单独输出 Java 源码或 `mod.json` 还不能被手机直接安装；手机导入要求 ZIP 里已经有 `classes.dex`。不要编入 `SignalMod` 接口本身。

## ZIP 结构

```text
my-module.zip
├── mod.json
├── classes.dex
├── assets/                 # 可选模块资源
└── lib/arm64-v8a/          # 可选原生库
```

`mod.json` 示例：

```json
{
  "formatVersion": 3,
  "apiVersion": 1,
  "id": "compact-home",
  "name": "简洁首页",
  "version": "1.0.0",
  "entryClass": "example.CompactHomeMod"
}
```

ZIP 根目录必须直接包含 `mod.json` 和单个 `classes.dex`。清单需有上面的全部字段；模块入口必须有公开无参构造器并实现 `cc.ccwu.signalfeed.modapi.SignalMod`。

API 1 提供 `onAttach(Activity, File)`、`transformFeed(String)`、`rewriteUrl(String)`、`createHome(Activity)`、`onDetach()`。页面需在 `createHome` 返回 Android `View`，空值表示不接管首页。信息流变换接收并返回 JSON 数组。URL 改写必须返回 HTTPS。资源放在模块目录的 `assets/`；清理监听和线程写在 `onDetach`。

宿主最多接受 512 个文件、解压后 32 MiB、清单 64 KiB，只允许 `mod.json`、`classes.dex`、`assets/` 和 `lib/`。不支持多 DEX、JAR 或 APK 作为模块。卸载时删除模块文件，需退出并重开应用让代码从进程卸载。

模块运行在应用进程内并拥有应用权限。它能改动应用显示和行为，但不能替换 APK、声明新的 Android 权限或修改系统。只安装可信来源的模块。若 AI 不能提供编译后 DEX，请让它输出完整 Java 项目源码和准确构建命令，并在有 Android SDK 的构建环境生成 ZIP。
