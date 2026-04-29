# MonetCanvas

<div align="center">

一款支持静态 + 动态壁纸管理的 **Material You** 壁纸应用

[![GitHub release](https://img.shields.io/github/v/release/Sephuan/MonetCanvas)](https://github.com/Sephuan/MonetCanvas/releases)
[![License](https://img.shields.io/github/license/Sephuan/MonetCanvas)](LICENSE)

</div>

---

## 简介

MonetCanvas 是一款基于 Android **Material You**（Monet）设计理念的壁纸管理应用。它不仅支持静态壁纸的导入、预览与设置，更支持**视频动态壁纸**，并能从壁纸中智能提取颜色，驱动系统 Monet 主题引擎，让你的整个设备界面与壁纸和谐统一。

## 特性一览

| | 特性 | 说明 |
|---|---|---|
| 🖼️ | **壁纸管理** | 导入、预览、设置静态/动态壁纸，支持网格/列表视图切换 |
| 🎬 | **动态壁纸** | 视频文件设为动态壁纸，后台流畅运行 |
| 🎨 | **Monet 取色** | 智能提取主色/次色/第三色，支持帧位置、区域、色调自定义 |
| 🎭 | **图片调整** | 填充方式、缩放、画布背景色、翻转、亮度/对比度/饱和度 |
| 💾 | **存储与备份** | 私有目录安全存储，支持备份同步与迁移 |
| 🌙 | **深色模式** | 跟随系统 / 亮色 / 暗色三模式切换 |
| 🎨 | **软件配色** | 跟随系统 / 取色规则 / 自选颜色三种主题来源 |
| 🌐 | **多语言** | 简体中文 / English 切换 |
| 🔤 | **字体缩放** | 应用内文字大小可调 |

## 功能特性

### 🖼️ 壁纸管理
- 支持**导入**图片（jpg/png/webp）和视频（mp4/gif/webm）文件作为壁纸
- 网格 / 列表两种布局视图，**4 种网格密度**（小/中/大/列表）自由切换
- **收藏**功能，长按即可收藏喜爱的壁纸（含震动触感反馈）
- 按类型快速筛选：**全部 / 静态 / 动态**，附带计数显示
- 一键**设置**为桌面壁纸、锁屏壁纸或两者同时设置
- **全屏预览**模式，沉浸式查看壁纸效果
- 壁纸**删除**功能，含二次确认对话框
- 壁纸缩略图使用 **Coil** 加载，支持**交叉淡入**动画

### 🎬 动态壁纸
- 支持将**视频文件**通过系统 Live Wallpaper 机制设为动态壁纸
- 基于 **ExoPlayer（Media3）** 实现后台播放，循环流畅
- 动态壁纸引擎（`LiveWallpaperService`）自持播放器，退出应用仍持续运行
- **权限检测与引导**：当系统服务未授予权限时，弹出引导对话框，直达系统设置页
- 支持**全屏预览**，在预览页即可实时观看动态效果
- 设置流程完整：预览页点击设置 → 跳转系统壁纸选择器 → 确认后自动应用
- 动态壁纸卡片带 **PlayCircle 图标**标识，列表页清晰区分

### 🎨 Monet 取色（核心特色）

MonetCanvas 的取色引擎基于 **AndroidX Palette 库**，从壁纸中提取 3 种颜色（主色 / 次色 / 第三色），驱动系统 Material You 主题。

**静态壁纸取色：**
- 使用**自适应降采样**（最大 800px）保留色彩特征同时提升性能
- 采样后的图片经过 **720px 边缘限制**二次降采样后送入 Palette 分析
- 支持 **6 种色调偏好**：
  - `自动`：使用 Palette 主导色
  - `鲜艳`：优先使用 vibrant 色系
  - `柔和`：优先使用 muted 色系
  - `主导色`：使用 population 最高的颜色
  - `偏深色`：优先 dark 色系
  - `偏浅色`：优先 light 色系
- 支持**取色区域**选择：整幅画面 / 中心区域 / 上半部分 / 下半部分
- 支持**手动指定颜色**覆盖自动取色结果

**动态壁纸取色：**
- 基于 `MediaMetadataRetriever` 提取视频帧
- 支持 **4 种帧位置策略**：
  - `起始帧`：取视频开头附近（~250ms）
  - `中间帧`：取视频中间位置
  - `最后帧`：取视频末尾附近
  - `随机帧`：取视频 10%~90% 之间的随机位置
- **多候选帧回退机制**：当首选帧解码失败时，自动尝试备选时间点，极大提升成功率
- 支持同步帧（关键帧）优先解码，失败时回退到精确帧

**取色交互：**
- 取色过程实时显示 **Loading 动画**（CircularProgressIndicator）
- 取色完成后以**彩色圆形**直观展示主色、次色、第三色，带**渐显 + 展开动画**
- 取色规则支持 **ModalBottomSheet** 配置面板，分段按钮、筛选芯片交互
- 规则保存后自动**重新分析**并更新取色预览
- 动态壁纸规则配置更丰富（额外显示帧位置选项），并附带取色说明提示

### 🎭 图片调整

提供丰富的图片编辑功能，调整实时生效，所见即所得：

- **填充方式**（三种，FilterChip 选择）：
  - `覆盖（COVER）`：等比缩放铺满，可拖动 + 缩放调整显示区域
  - `适应（FIT）`：等比缩放留白，可拖动 + 缩放调整位置
  - `拉伸（STRETCH）`：不等比拉伸铺满屏幕
- **缩放调节**：0.2x ~ 3.0x 滑动条控制，实时显示百分比，拉伸模式下自动隐藏
- **画布背景色**：**45+ 种预设颜色**，涵盖纯色、深色系、蓝色系、绿色系、红/橙/粉色系、紫色系、暖色/棕色系、黄/青色系，选中状态带高亮边框 + ✓ 图标
- **镜像翻转**：水平翻转 / 垂直翻转（Switch 开关）
- **色彩调整**（-100 ~ +100 滑动条，三位独立调节）：
  - 亮度（Brightness）
  - 对比度（Contrast）
  - 饱和度（Saturation）
- **一键重置**所有调整参数（仅在有调整时显示，带展开/收起动画）

### 💾 存储与备份
- 壁纸文件安全存储在**应用私有目录**，不受相册删除或外部文件变动影响
- 支持设置**备份目录**，用于壁纸文件的同步与共享
- **一键备份**所有壁纸到备份目录
- **同步功能**：自动从备份目录导入新壁纸 + 清理无效数据库记录
- 切换备份目录时支持**迁移文件**或保留原目录

### 🌙 深色模式
- 三种模式切换：**跟随系统** / **始终亮色** / **始终暗色**
- 与 Material You 配色融合，深色模式下色彩依然舒适
- 基于 `isSystemInDarkTheme()` 实现系统跟随

### 🎨 软件配色
- 三种主题配色来源：
  - **跟随系统**：使用系统 Monet 颜色
  - **取色规则**：从最近设置的壁纸取色规则中提取
  - **自选颜色**：从预设的 10 种 Material You 风格颜色中自由选择
- 基于 **Accompanist SystemUIController** 实现状态栏/导航栏颜色适配，沉浸感更强

### 🌐 多语言
- 支持 **简体中文** 和 **English**
- 语言切换即时生效（提示重启应用）
- 跟随系统语言自动匹配

### 🔤 字体大小
- 支持调整应用内所有文字的显示大小
- 提供多个档位，适配不同视觉需求

## 系统兼容

| 项目 | 说明 |
|------|------|
| **最低兼容** | Android 8.0 (API 26)，覆盖绝大多数设备 |
| **目标 SDK** | Android 15 (API 35) |
| **编译 SDK** | Android 15 (API 35) |
| **核心架构** | MVVM + Hilt DI |
| **数据库** | Room（含 KSP） |
| **偏好存储** | DataStore Preferences |
| **图片加载** | Coil（支持视频帧缩略图） |
| **视频播放** | ExoPlayer (Media3) |
| **取色引擎** | AndroidX Palette |
| **界面框架** | Jetpack Compose + Material 3 |
| **导航** | Navigation Compose（含页面过渡动画） |
| **后台任务** | WorkManager（Hilt 集成） |
| **状态栏适配** | Accompanist SystemUIController |

## 技术架构

- **MVVM 架构**：ViewModel + Repository 模式，数据流通过 StateFlow/Flow 驱动 UI
- **依赖注入**：Hilt（含 Hilt Navigation Compose、Hilt WorkManager）
- **异步处理**：Kotlin Coroutines（Dispatchers.IO 用于取色、文件操作）
- **持久化**：Room 数据库 + DataStore Preferences
- **后台任务**：WorkManager（Hilt 集成）
- **图片加载**：Coil（Compose 原生支持 + Video 扩展模块）
- **视频播放**：ExoPlayer（Media3）
- **取色引擎**：自研 ColorEngine，基于 AndroidX Palette，支持静态/动态壁纸取色
- **系统壁纸**：WallpaperManager API + WallpaperService（Live Wallpaper）
- **UI 框架**：Jetpack Compose + Material 3
- **导航动画**：Navigation Compose 的 AnimatedContentTransitionScope，组合 slideIn/Out + fadeIn/Out + scaleIn/Out

## 用户体验设计

- **🎬 导航动画**：页面切换使用**滑动 + 淡入淡出**组合动画，预览页 → 全屏页使用**缩放展开**效果
- **📱 预测性返回**：使用系统原生 Navigation Compose 的返回手势，**不拦截** PredictiveBackHandler，让 Android 原生预测返回动画（API 33+）自然生效
- **📐 无缝全屏**：`enableEdgeToEdge()` 实现沉浸式全屏体验
- **👆 可拖拽面板**：预览页底部面板支持**手势拖拽**展开/收起，更灵活的内容浏览
- **🎯 入场动画**：壁纸网格列表带**交错淡入 + 弹性上滑**动画（基于 AnimatedVisibility）
- **❤️ 触感反馈**：长按收藏时触发**震动触感**（HapticFeedback）
- **🔄 实时预览**：所有图片调整参数修改后**即时生效**，所见即所得
- **✨ 取色动画**：颜色提取完成后，颜色圆形**渐显 + 展开**动画（AnimatedVisibility）
- **📋 返回横幅**：动态壁纸设置完成后，顶部横幅提示结果（成功/失败状态）
- **🎨 半透明面板**：预览底部面板使用半透明背景，降低遮挡感
- **📏 自适应 UI**：壁纸网格支持 4 种密度等级，适应不同屏幕尺寸

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
