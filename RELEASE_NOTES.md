# 2.14-android17-test1

修复小米 17 Ultra 升级 Android 17 后，2.13 未进入 ST54L 后端的问题。

## 修复内容

- 接入 nezha / Android 17（SDK 37）的 ST 后端，保留 Android 16（SDK 36）。
- 避免误入 NXP Q-Poll，继续由前台应用自身身份启用和释放 ReaderMode，处理此前的 `Checking UID 0 but Package Name is Null` 错误路径。
- 已知 nezha 设备在系统版本或 ST 配置不匹配时停止，禁止回退到 NXP 厂商指令。
- 设备页与芯片检查显示实际 Android / SDK 版本。
- 保留相册选图、编辑裁剪、四色转换和效果预览。

## 验证范围

Android 17 / HyperOS OS4.0.5.0.XPACNXM / ST54L 已完成厂商回调、轮询查询和 5 秒 7A 寻卡流程检查：7A 设置读回一致，前台 ReaderMode 正常释放，原轮询恢复后逐字节读回一致。

本轮未收到标签，未发送屏幕 APDU 或图像，不代表 Android 17 下的标签通信与实体刷新已验证。此前 Android 16 的实体 A1 刷图记录保留。协议、图像处理及 Android 16/17 识别回归通过。

## 使用

安装本版，保持手机解锁，在设备页选择 Root，先点击“检查芯片 / NFC”，界面应显示“ST 7A”。再进行寻卡与屏幕检查。D1 不能区分 A0/A1，请按实物选择。

应用没有网络权限。APK 与已发布版本沿用同一本地开发签名，提供 SHA-256 校验文件。Root / Root 模式 Sui 仍为 ST 后端要求，普通 Shell Shizuku 不足以替代。

第三方来源与非商业条款见仓库 THIRD_PARTY_NOTICES.md 及 APK assets/licenses/。未改动屏幕驱动或射频校准，不自动重发刷新指令。
