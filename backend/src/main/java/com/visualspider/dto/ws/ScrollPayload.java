package com.visualspider.dto.ws;

/**
 * 可视化预览滚动页面:deltaY 为正向下滚、为负向上滚(像素)。
 * 后端 window.scrollBy(0, dy) 后重推视口截图,点击坐标仍对齐当前视口。
 */
public record ScrollPayload(int dy) {
}
