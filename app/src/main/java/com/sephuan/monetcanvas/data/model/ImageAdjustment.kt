package com.sephuan.monetcanvas.data.model

import androidx.compose.ui.graphics.Color

/**
 * 填充方式
 */
enum class FillMode {
    COVER,  // 覆盖：图片铺满，可左右移动
    FIT,    // 填充：图片完整显示在框内，可上下移动
    FREE    // 自由：可缩放 + 任意方向移动
}

/**
 * 图片调整参数
 */
data class ImageAdjustment(
    // 镜像
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,

    // 色彩调整 (范围 -1f ~ 1f，0f = 不调整)
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,

    // 背景色（FIT 模式下图片不铺满时显示）
    val backgroundColor: Color = Color.Black,

    // 填充方式
    val fillMode: FillMode = FillMode.COVER,

    // 位移和缩放（用户触屏操作的结果）
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
) {
    /**
     * 是否有任何调整（用于判断是否显示"重置"按钮）
     */
    val hasAnyAdjustment: Boolean
        get() = mirrorHorizontal ||
                mirrorVertical ||
                brightness != 0f ||
                contrast != 0f ||
                saturation != 0f ||
                backgroundColor != Color.Black ||
                fillMode != FillMode.COVER ||
                offsetX != 0f ||
                offsetY != 0f ||
                scale != 1f

    companion object {
        val DEFAULT = ImageAdjustment()

        // 背景色预设
        val BACKGROUND_COLORS = listOf(
            Color.Black,
            Color.White,
            Color(0xFF1A1A2E),  // 深蓝黑
            Color(0xFF16213E),  // 藏青
            Color(0xFF0F3460),  // 深蓝
            Color(0xFF1B1B2F),  // 暗紫黑
            Color(0xFF2C3333),  // 深灰绿
            Color(0xFF3D0000),  // 深红
        )
    }
}