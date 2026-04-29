# MonetCanvas

<div align="center">

一款支持静态 + 动态壁纸管理的 **Material You** 壁纸应用

[![GitHub release](https://img.shields.io/github/v/release/Sephuan/MonetCanvas)](https://github.com/Sephuan/MonetCanvas/releases)
[![License](https://img.shields.io/github/license/Sephuan/MonetCanvas)](LICENSE)

</div>

---

## 简介

MonetCanvas 是一款基于 Android Material You 设计理念的壁纸管理应用。它不仅支持静态壁纸的设置与管理，更支持**视频动态壁纸**，并能自动从壁纸中提取颜色，驱动系统 Monet 主题，让你的设备界面与壁纸和谐统一。

## 功能特性

- 🖼️ **壁纸管理** — 导入、预览、设置静态/动态壁纸
- 🎬 **动态壁纸** — 支持视频文件作为动态壁纸
- 🎨 **Monet 取色** — 自动提取壁纸颜色，驱动系统 Material You 主题
- ⚙️ **灵活的取色规则** — 支持取色帧位置、区域、色调偏好等自定义
- 🎭 **图片调整** — 缩放、填充方式、镜像翻转、色彩调整
- 💾 **安全存储** — 壁纸文件存储在应用私有目录，不受外部删除影响
- 🔄 **备份同步** — 支持备份目录切换与壁纸同步
- 🌙 **深色模式** — 支持跟随系统、浅色、深色模式
- 🌐 **多语言** — 支持简体中文 / English

## 下载

[![Release](https://img.shields.io/github/v/release/Sephuan/MonetCanvas)](https://github.com/Sephuan/MonetCanvas/releases/latest)

前往 [Releases](https://github.com/Sephuan/MonetCanvas/releases) 页面下载最新 APK。

## 构建

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17+
- Android SDK 35

### 本地构建

```shell
# 克隆仓库
git clone https://github.com/Sephuan/MonetCanvas.git
cd MonetCanvas

# Debug 构建
./gradlew assembleDebug

# Release 构建（需要签名配置）
./gradlew assembleRelease
```

Release 构建需要配置签名文件，参考 `keystore.properties` 模板。

## 致谢

感谢 [hhad8](https://github.com/hhad8) 提供的相关建议与帮助。

## 开源许可

本项目基于 MIT 许可证开源。详见 [LICENSE](LICENSE) 文件。

## 已知问题

### 🐞 动态壁纸取色可能错误

**表现：** 设置动态壁纸后，系统 Monet 主题颜色可能与壁纸不匹配，出现取色错误的情况。

**临时解决方案：** 再次进入该壁纸的预览页，重新设置一次即可触发重新取色，颜色将恢复正常。

**原因：** 动态壁纸服务中的全局缓存可能导致跨壁纸颜色污染，且取色种子颜色存在双重写入的竞态条件。目前正在修复中。
