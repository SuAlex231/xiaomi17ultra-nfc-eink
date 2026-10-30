# 小米 17 Ultra 适配说明

## 已验证设备

- 小米 17 Ultra：25128PNA1C / nezha。
- Android 16，HyperOS OS3.0.312.0.WPACNXM。
- 活动 NFC 应用为 ST 实现，配置指向 ST54L 固件。
- 实体面板最终确认为华为 552×768 四色 **A1**。

前台适配版本已完成 D1、认证、全图上传、D4 接受及 DE 忙转连续空闲，使用者确认实屏刷新成功。上电等待为 2000 ms，状态查询间隔 500 ms。早期一次按 A0 设置的测试出现低压和花屏，不能当作实体 A0 兼容性证据。

## 工作方式

1. Root/Sui 后端保存 ST 原轮询配置，准备临时 MIFARE 兼容处理并配置 7-bit `7A` 唤醒帧。
2. 前台 Activity 使用公开的 `NfcAdapter.enableReaderMode`，跳过 NDEF 检查，由应用自身 UID 直接接收标签并持有 ISO-DEP。
3. 协议先检查 D1 原厂规格与扫描方向；发送沿用板内驱动，不写 DB/DA。
4. 前台关闭 ISO-DEP、释放 ReaderMode 并确认结束后，Root 后端恢复原配置，逐字节读回一致才删除恢复快照。

本机 HyperOS 会拒绝 Root UID 0 的 ReaderMode 包名检查，也会静默忽略不在前台的 Shell 读卡请求。因此，只看到 Binder 调用正常返回或程序输出 START，不能作为 ReaderMode 生效的证据。当前实现已在系统状态中核对 `mIsReaderMode=true` 与 `mEnableReader=true`，并收到实体标签。

## ST 命令与恢复

- 不向 ST 控制器发送 NXP QTag 命令。
- 本机 Android 16 HAL 不支持 OEM 查询子命令 `0F/01/15`，会返回 `150B`；查询使用 ST 官方定义的原生 OID `0x1E`。
- 设置使用 OEM `0x13` 包装，让 HAL 同步维护自定义轮询状态。原生 GET 包含的 CRC 在交给 OEM SET 前移除，由 HAL 重算；恢复结果用完整原生 GET 数据核对。
- 使用全局 ST 会话锁。恢复快照为 `/data/local/tmp/qp-st-polling.snapshot`，下次检查会优先恢复残留快照。
- 临时 MIFARE 兼容脚本在会话结束后检测停止标记并卸载，另有 330 秒上限。注入或 NFC 服务重启可能影响当时其他 NFC 会话。
- 不修改系统固件、APC、射频校准或屏幕驱动。刷新失败不自动重放图像或 D4。

## 2.12 相册与四色效果

Android 13+ 使用系统 Photo Picker，旧系统回退到相册 ACTION_PICK。无整库读取或网络权限，选图不会自动刷入。

全屏转换界面提供四色清晰、四色照片抖动、黑白文字及黑白抖动，支持色块简化、黑白阈值、原图对照、缩放和色彩占比。

可选灰阶保护将 RGB 最大最小通道差不大于 18 的区域限制到黑白匹配，避免部分中灰被旧最近邻算法映射为红黄。关闭后兼容原算法。预览由实际 A1/A0 打包数据反解，显示逻辑像素排列。

2.12 已完成自动化回归和手机 UI 验证；新增转换效果未做独立的实屏成像对照。

## 2.13 图片编辑与裁剪

恢复亮度、对比度、90° 旋转、水平/垂直镜像，并将屏幕比例裁剪接入同一全屏编辑界面。单指拖动和双指缩放调整裁剪取景，调色不会重置裁剪位置；旋转和镜像后重新取景。

本次编辑基于进入编辑器时的图片快照，滑动参数不累积处理误差；提供编辑前后对照、重置、取消和还原原图。应用编辑后自动返回四色转换，取消不更新发送图，不覆盖相册原文件。

手机端使用内置测试图验证入口、调色、方向、镜像、裁剪拖动、对照、取消和应用后的 A1 发送预览。此轮不启动 NFC 或刷新实体屏幕。

## 2.14 Android 17 识别修复

2.13 使用 `nezha && SDK == 36` 选择 ST 后端。升级 Android 17（SDK 37）后，界面及进程误入 NXP 路径；Root 进程的 ReaderMode 调用被系统包名校验拒绝，停止时也会报告释放失败。

2.14 增加 SDK 37，继续由前台应用自身身份启用和释放 ReaderMode。已知 nezha 设备在系统版本或 ST 固件配置不匹配时停止，不回退到 NXP 厂商指令。设备页与芯片检查显示实际 Android/SDK 版本。

已在 Android 17 / HyperOS OS4.0.5.0.XPACNXM 验证 ST54L 配置、厂商回调和轮询查询，以及 5 秒 7A 寻卡的设置读回、前台 ReaderMode 释放与原轮询逐字节恢复。本轮未收到标签，不发送屏幕 APDU 或图像；Android 17 下的实体刷新仍待验证。Android 16 的既有 A1 实屏验证记录保留。

## 2.15 Shizuku Shell 实验流程

按用户要求接入 UID 2000 的 ST 厂商 Binder 回调、轮询查询/设置与恢复，前台应用继续负责 ReaderMode 和 ISO-DEP。选择 Shizuku 时明确跳过 MIFARE 注入，包括服务实际以 Root 运行的情况；UID 0 不计为免 Root 验证。Root 选项保留原兼容注入。

Shell 不读取应用私有文件：桥接服务复制任务到仅 Shell 可访问的会话目录，通过 Binder 接收应用已释放 ReaderMode 的通知，再恢复轮询。结束标记限定在该会话目录且名称固定。Root 与 Shell 共用 0660 会话锁，分别保存 0600 轮询快照；Shell 发现未恢复的 Root 快照时停止，提示先由 Root 恢复。

已验证生产后端在 UID 2000 下的查询、7A 设置及读回、跳过注入和模拟前台结束后的原轮询逐字节恢复；自动化校验新增会话结束标记的路径边界。实际 Shizuku 服务连接、标签通信和完整刷写由用户手动试用，未声称成功。

## 来源

- 主要 Android 上游：[ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app)，基于提交 `f16adae`，保留其 Git 历史。
- ST Root 配置及 MIFARE 兼容参考：[wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab)，提交 `6ffde495a9eb53acc69c4acdeee74e7fab017f30`，PolyForm Noncommercial 1.0.0。
- ST 命令及 SET 转换核对：[STMicroelectronics/ST54-android-hardware-st-nfc](https://github.com/STMicroelectronics/ST54-android-hardware-st-nfc/tree/st_dev_16)，提交 `429ab3ffe50f4d0496408f2b52c0a7a4736bc559`，Apache-2.0。
- Frida 17.24.0 arm64 injector 及完整许可随包保留；构建时校验固定 SHA-256。

完整第三方来源及许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
