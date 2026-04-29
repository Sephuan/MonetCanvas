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

### 🖼️ 壁纸管理
- 支持**导入**图片和视频文件作为壁纸
- 网格/列表两种视图布局，可自由调整网格大小
- **收藏**功能，长按即可收藏喜爱的壁纸
- 按类型（全部/静态/动态）快速筛选
- 一键**设置**为桌面壁纸、锁屏壁纸或两者同时设置

### 🎬 动态壁纸
- 支持将**视频文件**设为动态壁纸（通过系统 Live Wallpaper 机制）
- 激活后自动在后台运行，播放流畅
- 支持全屏预览，实时查看效果
- 动态壁纸服务权限检测与引导，兼容不同设备

### 🎨 Monet 取色
- 自动从壁纸中提取**主色、次色、第三色**，与系统 Material You 主题联动
- 动态壁纸支持自定义**取色规则**：
  - **帧位置**：起始帧 / 中间帧 / 最后帧 / 随机帧
  - **取色区域**：整幅画面 / 中心区域 / 上半部分 / 下半部分
  - **色调偏好**：自动 / 鲜艳 / 柔和 / 主导色 / 偏深色 / 偏浅色
- 取色结果实时预览，直观展示颜色提取效果
- 设置壁纸后自动将取色结果写入系统，驱动全局 Monet 主题

### 🎭 图片调整
- **填充方式**：覆盖（拖动+缩放）/ 适应（拖动+缩放）/ 拉伸（铺满屏幕）
- **缩放调节**：手动滑动条控制缩放比例，与手势缩放同步
- **画布背景色**：自定义壁纸未覆盖区域的颜色
- **镜像翻转**：水平翻转 / 垂直翻转
- **色彩调整**：亮度 / 对比度 / 饱和度 独立调节
- **一键重置**所有调整参数

### 💾 存储与备份
- 壁纸文件安全存储在**应用私有目录**，不受相册删除或外部文件变动影响
- 支持设置**备份目录**，用于壁纸文件的同步与共享
- **一键备份**所有壁纸到备份目录
- **同步功能**：自动从备份目录导入新壁纸 + 清理无效记录
- 切换备份目录时支持**迁移**或保留原目录文件

### 🌙 深色模式
- 三种模式切换：**跟随系统** / **始终亮色** / **始终暗色**
- 与 Material You 配色融合，深色模式下色彩依然舒适

### 🎨 软件配色
- 三种主题配色来源：
  - **跟随系统**：使用系统 Monet 颜色
  - **取色规则**：从最近设置的壁纸取色规则中提取
  - **自选颜色**：从预设的 10 种 Material You 风格颜色中自由选择

### 🌐 多语言
- 支持 **简体中文** 和 **English**
- 语言切换即时生效（提示重启）
- 跟随系统语言自动匹配

### 🔤 字体大小
- 支持调整应用内所有文字的显示大小
- 提供多个档位，适配不同视觉需求

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
