package com.sephuan.monetcanvas.data.model

enum class WallpaperType {
    STATIC,  // 静态: jpg, png, webp
    LIVE     // 动态: mp4, gif, webm
}

enum class GridSize(val columns: Int, val label: String) {
    SMALL(4, "小"),
    MEDIUM(3, "中"),
    LARGE(2, "大"),
    LIST(1, "列表")
}

enum class FilterType(val label: String) {
    ALL("全部"),
    STATIC("静态"),
    LIVE("动态"),
    FAVORITES("收藏")
}