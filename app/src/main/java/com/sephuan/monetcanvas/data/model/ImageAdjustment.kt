package com.sephuan.monetcanvas.data.model

import androidx.compose.ui.graphics.Color

enum class FillMode {
    COVER,
    FIT,
    FREE
}

data class ImageAdjustment(
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val backgroundColor: Color = Color.Black,
    val fillMode: FillMode = FillMode.COVER,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
) {
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

        val BACKGROUND_COLORS = listOf(
            // 纯色
            Color.Black,
            Color.White,
            Color(0xFF808080),  // 中灰
            Color(0xFF404040),  // 深灰
            Color(0xFFC0C0C0),  // 浅灰

            // 深色系
            Color(0xFF1A1A2E),  // 深蓝黑
            Color(0xFF16213E),  // 藏青
            Color(0xFF0F3460),  // 深蓝
            Color(0xFF1B1B2F),  // 暗紫黑
            Color(0xFF2C3333),  // 深灰绿
            Color(0xFF3D0000),  // 深红
            Color(0xFF1A1A1A),  // 近黑
            Color(0xFF0D1117),  // GitHub 深色
            Color(0xFF1E1E2E),  // Catppuccin 深色

            // 蓝色系
            Color(0xFF1565C0),  // 深蓝
            Color(0xFF2196F3),  // 标准蓝
            Color(0xFF42A5F5),  // 浅蓝
            Color(0xFF0288D1),  // 亮蓝
            Color(0xFF01579B),  // 极深蓝
            Color(0xFF1A237E),  // 靛蓝

            // 绿色系
            Color(0xFF2E7D32),  // 深绿
            Color(0xFF4CAF50),  // 标准绿
            Color(0xFF00695C),  // 深青绿
            Color(0xFF009688),  // 青绿
            Color(0xFF1B5E20),  // 极深绿

            // 红/橙/粉
            Color(0xFFB71C1C),  // 深红
            Color(0xFFE91E63),  // 粉红
            Color(0xFFC62828),  // 红色
            Color(0xFFFF5722),  // 橙红
            Color(0xFFFF9800),  // 橙色
            Color(0xFFE65100),  // 深橙

            // 紫色系
            Color(0xFF6A1B9A),  // 深紫
            Color(0xFF9C27B0),  // 紫色
            Color(0xFF4A148C),  // 极深紫
            Color(0xFF7B1FA2),  // 中紫
            Color(0xFF311B92),  // 深靛紫

            // 暖色/棕色
            Color(0xFF795548),  // 棕色
            Color(0xFF4E342E),  // 深棕
            Color(0xFF3E2723),  // 极深棕
            Color(0xFF5D4037),  // 中棕
            Color(0xFF8D6E63),  // 浅棕

            // 黄/青
            Color(0xFFFFC107),  // 琥珀
            Color(0xFFCDDC39),  // 柠檬
            Color(0xFF00BCD4),  // 青色
            Color(0xFF006064),  // 深青
            Color(0xFFFFEB3B),  // 黄色
        )
    }
}