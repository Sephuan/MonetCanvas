package com.sephuan.monetcanvas.data.model

/**
 * 核心需求1：取色帧位置
 */
enum class FramePickPosition {
    FIRST,      // 起始帧
    MIDDLE,     // 中间帧
    LAST,       // 最后一帧
    RANDOM      // 随机帧
}

/**
 * 取色区域
 */
enum class ColorRegion {
    FULL_FRAME,     // 整帧分析
    CENTER,         // 中心区域
    TOP_HALF,       // 上半部分
    BOTTOM_HALF,    // 下半部分
    CUSTOM          // 自定义(预留)
}

/**
 * 色调偏好
 */
enum class TonePreference {
    AUTO,               // 自动
    VIBRANT,            // 鲜艳
    MUTED,              // 柔和
    DOMINANT,           // 主导色
    DARK_PREFERRED,     // 偏深色
    LIGHT_PREFERRED     // 偏浅色
}

/**
 * 完整的 Monet 取色规则数据模型
 */
data class MonetRule(
    val framePosition: FramePickPosition = FramePickPosition.FIRST,
    val colorRegion: ColorRegion = ColorRegion.FULL_FRAME,
    val tonePreference: TonePreference = TonePreference.AUTO,
    val manualOverrideColor: Int? = null  // 如果不为null，则直接使用该颜色，忽略上面所有自动规则
)