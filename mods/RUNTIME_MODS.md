# 手机运行时模块（格式 3，API 1）

安装位置：设置 → Mod 管理 → 安装手机模块 ZIP。确认可信来源后安装，退出并重新打开应用即生效。启停、升级和卸载同样重开生效，不需要电脑重编译应用。

ZIP 根目录包含 `mod.json`、`classes.dex`，可选 `assets/` 和 `lib/<ABI>/`。模块作者需提前编译 DEX，用户只导入 ZIP。示例：`mods/runtime-home-example.zip`，它用原生 View 替换首页。

```json
{"formatVersion":3,"apiVersion":1,"id":"sample-home","name":"自定义首页示例","version":"1.0","entryClass":"sample.WelcomeMod"}
```

入口类实现 `cc.ccwu.signalfeed.modapi.SignalMod`，接口源码在 `app/src/main/java/cc/ccwu/signalfeed/modapi/SignalMod.java`，模块不要重复打包接口。可用扩展点：

- `onAttach(Activity, File)`：获得 Activity 和模块目录，可使用应用权限、网络、存储以及 Android API
- `createHome(Activity)`：提供任意原生 View 作为首页，底部设置入口保留
- `transformFeed(String)`：接收并返回 FeedPost JSON 数组，可过滤、修改和重排数据（不直接改数据库）
- `rewriteUrl(String)`：修改后台同步及文件订阅请求的 HTTPS 地址
- `onDetach()`：释放自行注册的监听、线程和资源

多个模块按 id 排序执行；首页采用第一个返回非空 View 的模块。异常回调会停用该模块；桌面独立的“SignalFeed 恢复”入口不加载模块，可以停用全部模块并结束主进程后重新打开。

卸载删除模块专属目录，包括 DEX、资源和本地库。退出主进程后旧类和对象不再保留，下次启动只加载剩余模块。应用数据继续保留。模块以应用本身的权限执行，不是安全沙箱；任意恶意代码或它对共享数据、外部服务的修改不能保证靠卸载撤销。系统权限声明和已经安装的 APK 类不能由这个插件接口热替换；没有 Root 系统模块级别的权限。

Android 14 及以上要求动态加载文件为只读，本实现写入 DEX 前设为只读并在加载时检查摘要。
参考：https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading

旧格式 1 过滤包继续支持；格式 2 源码补丁保留电脑工具，绑定旧 0.6 基线，不应覆盖安装到新版数据上。
