# SignalFeed 手机代码模块规范

本文规定 SignalFeed 运行时手机模块 ZIP 的格式、制作、运行和卸载行为。它适用于格式版本 3、宿主 API 1。格式 1 规则包和格式 2 电脑源码包的完整定义见[Mod 格式总规范](CODE_MOD_SPEC.md)。

## 1. 选择正确的 Mod 类型

| 需求 | 包类型 | 应用方法 |
|---|---|---|
| 隐藏关键词或账号 | 规则包，formatVersion 1 | 手机导入后立即启用 |
| 改 Kotlin、Compose、Manifest、依赖或其他 APK 资源 | 源码补丁，formatVersion 2 | 电脑从指定基线重编译 APK，再安装 |
| 手机安装、停用、卸载代码 | 运行时模块，formatVersion 3 | ZIP 内的 DEX 由 SignalFeed 动态加载，重启应用生效 |

格式 3 可在应用权限范围内扩展代码；它不是 Android 系统级模块，不能声明新 Manifest 权限、替换宿主已加载的类、改系统签名或获得 Root。要改这类宿主构建内容，新版使用项目 API 1 构建手机模块。

## 2. ZIP 目录

所有路径都相对 ZIP 根目录。`mod.json` 和 `classes.dex` 必须直接位于根目录。

```text
my-mod.zip
├── mod.json
├── classes.dex
├── assets/                 # 可选，模块自己的数据文件
│   └── theme.json
└── lib/                    # 可选，JNI 原生库
    └── arm64-v8a/libsample.so
```

不要把 `api1/`、项目名目录或源码目录包在最外层。DEX 包只含模块自己的实现类；不得把宿主的 `SignalMod` 接口一并打进 DEX，否则会造成类加载器接口身份不一致。当前宿主只读取 ZIP 根目录的单个 `classes.dex`，不支持 `classes2.dex`、JAR/APK 容器或 ZIP 内的另一个嵌套 ZIP。

## 3. `mod.json`

UTF-8 JSON 对象，不需要 BOM，不允许注释。当前字段如下。

| 字段 | 类型 | 必需 | 规则 |
|---|---|---|---|
| `formatVersion` | 整数 | 是 | 固定为 `3` |
| `apiVersion` | 整数 | 是 | 当前固定为 `1` |
| `id` | 字符串 | 是 | `[A-Za-z0-9_-]{1,64}`；用于替换同 ID 模块 |
| `name` | 字符串 | 是 | 1–80 字符，用于设置页显示 |
| `version` | 字符串 | 是 | 1–30 字符，由模块作者递增；宿主不作语义版本比较 |
| `entryClass` | 字符串 | 是 | DEX 内实现 `SignalMod` 的完整 Java 类名；匹配 `^[A-Za-z_][A-Za-z0-9_.$]+$` |

示例：

```json
{
  "formatVersion": 3,
  "apiVersion": 1,
  "id": "custom-home",
  "name": "自定义首页",
  "version": "1.0.0",
  "entryClass": "example.CustomHomeMod"
}
```

## 4. 宿主扩展接口 API 1

接口源码：[SignalMod.java](../app/src/main/java/cc/ccwu/signalfeed/modapi/SignalMod.java)。Android Java 接口完整限定名为 `cc.ccwu.signalfeed.modapi.SignalMod`。模块入口必须有可见的无参构造器，并实现此接口。

```java
public interface SignalMod {
    default void onAttach(Activity activity, File moduleDirectory) {}
    default String transformFeed(String feedJson) { return feedJson; }
    default String rewriteUrl(String url) { return url; }
    default View createHome(Activity activity) { return null; }
    default void onDetach() {}
}
```

- `onAttach`：模块被加载后调用。`moduleDirectory` 指模块私有目录，其中包含该模块的 `assets/`、`lib/` 和 `classes.dex`。不要长期静态持有旧 Activity。注册的监听和自行创建的线程应在 `onDetach` 清理。
- `transformFeed`：输入是 UTF-8 JSON 数组，数组元素对应宿主 `FeedPost`：`post` 对象包含 id、body、publishedAt、importance、confidence、breaking、originalUrl 等字段；`account` 对象包含 id、name、handle、followed、muted、weight；另有 `sourceLinks` 和 `topicIds`。返回必须还是合法 JSON 数组，并保留其他模块需要的字段。可过滤、改写、重排帖子。结果仅影响当前内存中的信息流，不写回 Room；格式错误会停用该模块。
- `rewriteUrl`：传入待请求的绝对 URL。必须返回以 `https://` 开头的 URL。可用于宿主 API 和文件订阅请求；返回非法值或抛出异常会停用此模块。
- `createHome`：返回 Android `View` 时，该模块接管首页内容；返回 `null` 让下一个模块尝试。多个模块按 `id` 字典序运行，第一个非空首页生效。宿主底部导航和设置页仍保留。可以使用 ComposeView，但需自行处理生命周期。
- `onDetach`：Activity 销毁、配置变化或宿主卸载模块时调用。Activity 重建会重新加载模块，回调必须可重复。

代码在应用进程内以 SignalFeed 的 Android 权限运行；模块不是隔离沙箱。模块可以读写其能访问的应用数据、使用网络和修改显示。只安装自己信任来源的代码。模块不能改变 APK 安装包本身或系统权限声明。

## 5. ZIP 限制与失败处理

导入器最多接受 512 个文件，解压后总计不超过 32 MiB；清单不超过 64 KiB。仅允许 `mod.json`、`classes.dex`、`assets/`、`lib/` 路径。拒绝路径穿越、反斜线和冒号路径。宿主记录 DEX SHA-256，并在加载前校验且设置为只读。Android 14 以上对动态代码的只读要求参见[官方说明](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)。

ZIP 必须先进入未安装暂存状态，用户确认后才成为已安装模块。相同 `id` 的新模块替换旧模块。停用保留文件；卸载删除该模块目录；两者都要完全退出并重新打开应用，让旧 ClassLoader 和对象退出进程。卸载不会清除宿主数据库、偏好或模块对共享数据所做的修改。模块对远端服务的操作也无法由卸载撤销。

若代码异常，宿主停用对应模块。若模块在回调中导致进程崩溃，恢复标记会在下次启动时停用最近执行中的模块。完全无法进入主界面时，从桌面单独启动“SignalFeed 恢复”，它在独立进程中运行，不加载模块；选择停用全部后重新打开应用。

## 6. Java 模块从源码打包

项目内的 [API 1 接口](../app/src/main/java/cc/ccwu/signalfeed/modapi/SignalMod.java) 和构建脚本会编译接口桩、编译模块源码、将模块类转成单个 DEX，并生成正确的 ZIP 根目录结构。示例源码与清单分别是 `../mods/runtime-example/src/WelcomeMod.java` 和 `../mods/runtime-example/mod.json`。

```powershell
$Sdk = $env:ANDROID_SDK_ROOT
# 如果环境变量未设置，把下一行改成已安装 Android SDK 的目录
# $Sdk = 'C:\Android\Sdk'
.\mods\api1\build-module.ps1 `
  -Manifest .\mods\runtime-example\mod.json `
  -Source .\mods\runtime-example\src `
  -Output .\mods\runtime-home-example.zip `
  -Sdk $Sdk
```

SDK 需要包含一个 Android 平台及 Build Tools，JDK 需要能运行 `javac`、`jar`。脚本自动选择已安装的最高平台和带 `d8.bat` 的 Build Tools。模块源码目录可有多个 `.java` 文件，它们会一起编译。第三方 Java 依赖需在 DEX 前自行解包或编译为模块类；构建脚本不会把依赖 JAR 合并进 DEX。不要把 API JAR 当作 D8 输入，只通过 `--classpath` 引用宿主接口。

可选资源放在 `mods/api1/assets/`，原生库按设备 ABI 放在 `mods/api1/lib/arm64-v8a/` 等目录；脚本会复制到模块 ZIP 的 `assets/` 和 `lib/`。编译前先确认 `entryClass` 与 DEX 中的类路径一致，再安装到测试机验证。不要把不可信 DEX 安装到保存重要账号或数据的设备。

生成的模块 ZIP 复制到手机，在 **设置 → Mod 管理 → 安装手机模块 ZIP** 选择、检查信息并确认。退出应用并重新打开后生效。出错时先停用模块；无法进入主界面则从桌面启动独立恢复入口。
## 7. 版本兼容

`formatVersion` 是 ZIP 清单结构版本，`apiVersion` 是宿主接口版本，两者不可互换。当前文档不承诺 API 1 以外的模块兼容。若宿主更改接口方法、回调顺序或数据 JSON，应发布新的 API 文档和兼容实现；不能只改 `version` 字符串让旧 DEX 自动适配。

