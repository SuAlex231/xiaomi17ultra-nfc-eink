# 小米 17 Ultra NFC 墨水屏刷新工具

基于 [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app) 的 Android 应用，增加小米 17 Ultra 的 ST54L 适配、系统相册选图和四色效果预览。

当前版本：**2.14-android17-test1（预发布）**。

[下载安装包](https://github.com/SuAlex231/xiaomi17ultra-nfc-eink/releases) · [小米 17 Ultra 适配说明](XIAOMI17U.md) · [第三方许可与来源](THIRD_PARTY_NOTICES.md)

## 支持情况

| 项目 | 状态 |
| --- | --- |
| 手机 | 小米 17 Ultra，25128PNA1C / nezha |
| 已测试系统 | Android 16 / HyperOS OS3.0.312.0.WPACNXM：A1 实屏刷新；Android 17 / HyperOS OS4.0.5.0.XPACNXM：ST 检查、7A 设置、前台 ReaderMode 及轮询恢复 |
| NFC 控制器 | ST54L |
| 授权 | Root，或以 Root 运行的 Sui / Shizuku |
| 实体屏幕 | 华为 552×768 四色 A1，已完成实屏刷新并由使用者确认 |
| A0 | 保留上游编码与自动化测试；本适配没有实体 A0 验证记录 |
| 其他手机/系统 | 未验证，不能仅凭手机品牌或芯片名称推断兼容 |

普通 Shell Shizuku 在本机上不能替代 Root 完成 ST 7A 配置。NXP 手机沿用上游 Q-Poll 路线，本适配未增加 NXP 真机验证记录。

2.13 仅匹配 Android 16（SDK 36），升级 Android 17 后会误入 NXP Q-Poll 路径，可能出现 `Checking UID 0 but Package Name is Null`。2.14 接入 SDK 37，并在该机型的系统或 ST 配置不匹配时停止，避免回退到 NXP。Android 17 尚未验证标签通信与实体刷新。

## 功能

- 使用期间由前台应用直接接收 NFC 标签，停止或离开前台后恢复系统 NFC 分发。
- ST 7A 唤醒；保存原轮询配置，结束后恢复并逐字节读回核对。
- 从系统照片选择器选图，仅访问用户选择的照片，无网络权限。
- 全屏图片编辑：亮度、对比度、90° 旋转、水平/垂直镜像；按墨水屏比例拖动和双指缩放裁剪。
- 编辑前后对照、重置本次编辑、取消与还原原图；应用后进入四色预览，不修改相册原文件。
- 全屏四色/黑白预览，清晰色块、照片抖动、文字模式、色块简化和黑白阈值。
- 可关闭的灰阶保护，减少灰色区域误变成红黄；原图对照、缩放、1:1 像素查看和色彩占比。
- 预览由实际打包数据反解；发送前检查屏幕类型、图像长度、SHA-256 和原厂配置。
- 保留板内驱动，不改写 DB/DA，不调整 APC 或射频校准；协议失败后停止，不自动重发 D4。

## 使用

1. 安装 Release 中的 APK，开启 NFC 并解锁手机。在应用中授权 Root，或连接 Root 模式的 Sui/Shizuku。
2. 先检查芯片/NFC，再运行 ST 7A 寻卡。让手机 NFC 感应区与墨水屏线圈稳定贴合。
3. 按实体屏幕选择 A1 或 A0。D1 不能区分两种实体面板，请勿根据 UID 猜测。
4. 可先运行“只读检查屏幕”，确认规格和原厂配置。
5. 点击“从相册选照片”，可从四色预览中的“编辑 / 裁剪图片”调整亮度、对比度、方向和取景；应用后选择墨水屏转换效果，点击“应用到发送预览”。
6. 确认图片、屏幕类型和参数后发送。保持应用在前台及线圈贴合，直到刷新状态确认结束，并目视检查画面。

`68CA` 等低压错误可能发生在数据上传完成之后。请改善贴合与供能，再由用户发起新会话；程序不会自动重发。强制结束进程或掉电可能打断恢复，重新运行检查时会优先处理残留轮询快照。

## 构建

需要 Python 3、JDK 17+ 和 Android SDK。此版本使用 platform `android-35`、build-tools `36.0.0`、JBR 25 构建，target SDK 为 34。无 Gradle 构建期下载，Shizuku 与 Frida 依赖已固定版本。

Windows PowerShell：

```powershell
$env:JAVA_HOME = '你的 JDK 目录'
$env:ANDROID_HOME = '你的 Android SDK 目录'
.\build-windows.ps1
```

其他系统：

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_PLATFORM=android-35
export ANDROID_BUILD_TOOLS=36.0.0
python3 tests/run_java.py
python3 build.py
```

APK 和 SHA-256 位于 `dist/`。首次构建会生成本地调试签名；请保存自己的 `build/debug.keystore`。自行构建的签名与 Release 可能不同，不能保证覆盖安装。

## 验证

- 协议、图像映射、裁切边界、连接恢复、取消及刷新指令不重放等回归通过。
- 灰阶保护覆盖十种全帧模式，校验 A1 打包/预览逐像素一致、红黄原色保留及源图不变。
- 编辑测试覆盖旋转、双向镜像、亮度/对比度、通道截断、透明度和源图不变；手机端验证编辑跳转、组合处理、取消和应用到四色预览。
- 手机端任务校验测试不调用 NFC，覆盖屏幕选择、确认标记、参数、摘要、路径和数据长度。
- 已实测前台 ReaderMode、ISO-DEP、D1、认证、完整上传、D4、DE 忙转空闲，以及原轮询恢复。
- 相册选图、照片回传、四色预览和原图对照经过手机 UI 验证。2.12 新增转换效果尚未进行独立的实屏成像对照，不声称所有照片效果最佳。

## 隐私与许可

应用没有网络权限，不把照片上传服务器。运行日志保存在应用私有目录，可能包含标签 UID，分享前请检查。

本仓库不包含实机原始日志、私人照片、设备序列号、提取的系统库或签名私钥。

本项目包含遵循 **PolyForm Noncommercial 1.0.0** 的协议和参考实现部分，并保留其非商业条款及 Required Notice。Shizuku、Frida 等各依其许可分发，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 `assets/licenses/`。请勿将整体项目视为 MIT/Apache 或可任意商用。
