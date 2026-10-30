# 小米 17 Ultra 的 Shizuku 免 Root 可行性记录

2026-10-30，设备 nezha，Android 17 / SDK 37，HyperOS OS4.0.5.0.XPACNXM，ST54L。

## 结论

**已验证普通 Shell 可读写 ST 轮询配置；尚未打通无需注入的标签通信，不能宣称已验证 Shizuku 免 Root 刷写成功。**

以下为 2.14 阶段的可行性记录。后续 2.15 按用户要求加入跳过 MIFARE 注入的 Shell 手动试用路径，Root 兼容流程保留；本机仅验证新后端的 UID 2000 查询、7A 设置读回、跳过注入和原轮询恢复，不把这一结果当作完整免 Root 刷写成功。

## 测试身份与边界

测试手机本身已 Root，ADB 为 UID 0。为避免把 Root 能力误算为免 Root，单独建立 UID/GID 2000、SELinux `u:r:shell:s0`、禁止再次提权的测试进程。普通前台探针应用仅声明 NFC 权限，不访问 Root、不注入系统服务。

这是已 Root 设备上的降权能力测试，尚不能替代全新未 Root 设备、实际无线调试 Shizuku 的端到端验收。用户确认“USB 调试（安全设置）”已开启。最初一次回调注册被 WRITE_SECURE_SETTINGS 检查拒绝，后续复测允许；不应据单次拒绝判断所有 Shell 环境都不支持。

## 实测结果

| 项目 | 结果 |
| --- | --- |
| 普通 Shell NFC 状态查询 | 成功 |
| 厂商回调注册 / 释放 | 成功 |
| 暂停 / 恢复轮询 | 成功 |
| 暂停后原生 GET OID 0x1E | 返回有效原轮询配置 |
| OEM SET 0x13 设置 7-bit 0x7A | 成功，读回一致 |
| 原轮询恢复 | 成功，读回逐字节一致 |
| 不注入系统 NFC 的普通前台应用 | 30 秒内未收到标签，未发送 D1 或图像 |
| 相同摆放下的现有 Root 兼容模式对照 | 约 50 毫秒收到 IsoDep / NfcA 标签；结束恢复确认 |

没有执行认证、图像上传或刷新。公开记录不含标签 UID、原始日志或私人图像。

## 剩余问题

现有 Root 路径在 ST 7A 配置之外，还临时处理系统 NFC 的 MIFARE 多协议重选。该注入不能直接由普通 Shizuku/Shell 替代。上述对照与缺少该兼容步骤的表现一致，但两次使用不同前台应用，仅凭这轮实验还不能排除包级策略、时序等其他影响。

要交付真正的免 Root 刷写，需要找到不注入系统 NFC 进程的替代激活方案，然后验证稳定标签回调、ISO-DEP 连接、只读 D1，以及最终完整刷写和原轮询恢复。单独删除 UID 0 检查、保留 Root 预处理，或者把“7A 设置成功”当成“刷写成功”，都不能满足这个目标。

## 参考

- [Shizuku 官方介绍](https://shizuku.rikka.app/introduction/)：以 ADB 或 Root 服务身份桥接系统 API。
- [Shizuku 官方设置说明](https://shizuku.rikka.app/guide/setup/)：小米另有“USB 调试（安全设置）”要求。
- [AOSP NfcService](https://android.googlesource.com/platform/packages/apps/Nfc/+/refs/heads/android15-prebuilt-test/src/com/android/nfc/NfcService.java)：厂商 NCI 与回调接口经过 NFC 管理权限校验；实际可用性以上述本机结果为准。
