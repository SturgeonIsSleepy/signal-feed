# 电脑重建型代码与资源 Mod ZIP

这是历史电脑工具格式，不属于当前手机四类导入。示例保存在 `mods/source-patch-examples/`，手机扩展请制作 [格式 3 运行时 Mod](runtime-mod-zip.md)。

## 给 AI 的生成要求

请针对当前 SignalFeed 源码基线制作格式 2 的 `source-patch` ZIP。先读取项目的 `project-specs/CODE_MOD_SPEC.md` 和当前 `CODE_BASE`，不要编造基线 ID。ZIP 必须包含合法 `mod.json` 及清单声明的补丁文件。这个 ZIP 不能从手机统一导入入口安装；它需要纳入重建请求，再由电脑构建工具生成新 APK。

清单核心示例：

```json
{
  "formatVersion": 2,
  "type": "source-patch",
  "baseId": "signalfeed-0.7.0",
  "id": "compact-settings",
  "name": "简洁设置页",
  "version": "1.0.0",
  "files": [
    {"path": "app/src/main/java/cc/ccwu/signalfeed/Example.kt", "action": "replace"}
  ]
}
```

完整字段、允许操作（新增或替换使用 `replace`，删除使用 `delete`）、ZIP 文件布局、哈希计算、基线兼容规则和电脑重建流程，以项目的 [CODE_MOD_SPEC.md](../CODE_MOD_SPEC.md) 为准。示例基线仅供说明，必须核对当前项目实际基线；替换基线现有文件时，`beforeSha256` 必须依据基线原文件计算，不能用占位文字提交。

如果 AI 无法读取源码树或计算哈希，应先生成补丁文件清单草案并指出缺少的源码和基线信息，不得声称 ZIP 已能导入。代码包应只修改完成需求所需文件。
