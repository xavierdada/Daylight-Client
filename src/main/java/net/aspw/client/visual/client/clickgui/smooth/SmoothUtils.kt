package net.aspw.client.visual.client.clickgui.smooth

import net.aspw.client.visual.font.UiFont
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.opengl.GL11
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 设一个跟着当前 GUI 缩放走的裁剪框（未缩放 GUI 坐标）。
 *
 * 不能直接用 `RenderUtils.makeScissorBox`：那个把坐标乘上原版 scaleFactor 就完了，也就是
 * "从屏幕原点缩放"。而 ClickGUI 的矩阵是把 `(cx, cy)` 当锚点缩放的
 * （`translate(cx,cy) scale(z) translate(-cx,-cy)`），缩放不为 1 时两者差
 * `cx * (1 - z)`，裁剪框会整体跑偏。这里先按同一个锚点把坐标映射到屏幕坐标，再转成帧缓冲
 * 像素。宽高夹到 >= 0：glScissor 收到负数会报 GL_INVALID_VALUE。
 */
fun makeGuiScissorBox(x1: Float, y1: Float, x2: Float, y2: Float) {
    val zoom = SmoothConstants.uiScale
    if (zoom <= 0f) return

    val scaled = ScaledResolution(Minecraft.getMinecraft())
    val factor = scaled.scaleFactor
    val centerX = scaled.scaledWidth / 2f
    val centerY = scaled.scaledHeight / 2f

    val screenX1 = centerX + (x1 - centerX) * zoom
    val screenX2 = centerX + (x2 - centerX) * zoom
    val screenY1 = centerY + (y1 - centerY) * zoom
    val screenY2 = centerY + (y2 - centerY) * zoom

    val width = ceil((screenX2 - screenX1) * factor).toInt().coerceAtLeast(0)
    val height = ceil((screenY2 - screenY1) * factor).toInt().coerceAtLeast(0)
    GL11.glScissor(
        floor(screenX1 * factor).toInt(),
        floor((scaled.scaledHeight - screenY2) * factor).toInt(),
        width,
        height
    )
}

/**
 * 画一行在行内垂直居中的文字。
 *
 * 偏移量用 [UiFont.lineHeight]：关 PackFont 时它就是原来那个 `FontRenderer.FONT_HEIGHT`
 * （`GameFontRenderer` 在构造时把它改写成 TTF 的高度，所以数值不变，观感保持原样）；
 * 开 PackFont 后它是原版 9px 字形缩放后的高度，否则文字会贴到行的下边缘。
 *
 * 仍然不给阴影：Smooth GUI 画在浅色面板上，近乎全黑的阴影只会把字描一圈边。
 */
fun FontRenderer.drawHeightCenteredString(string: String, x: Float, y: Float, color: Int) {
    UiFont.draw(this, string, x, y - UiFont.lineHeight(this) / 2f, color, shadow = false)
}

/**
 * 一档文字的行高，随 PackFont 开关变化。给 [drawHeightCenteredString]、
 * `BaseValueButton.hOffset` 和 `ModuleButton` 的行内偏移用。
 */
fun getHeight(font: FontRenderer) = UiFont.lineHeight(font)

fun dim(color: Color): Color {
    return color.darker()
}

/**
 * 给一个打包成 0xRRGGBB 的颜色补上透明度。
 *
 * 值控件与行底板都按 ARGB 传给 `drawRect` / `drawRoundedRect`（它们用 `glColor4f`
 * 带上 alpha），所以叠色时不能只改 `Color` 的 alpha 而不重新打包。
 */
fun withAlpha(rgb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

/** 两色线性混合，[t] 会被夹到 0..1。 */
fun mix(from: Color, to: Color, t: Float): Color {
    if (t <= 0.001f) return from
    if (t >= 0.999f) return to
    return Color(
        (from.red + (to.red - from.red) * t).toInt(),
        (from.green + (to.green - from.green) * t).toInt(),
        (from.blue + (to.blue - from.blue) * t).toInt()
    )
}

/**
 * 把 [text] 截到 [maxWidth] 以内，放不下就在末尾留一个省略号（同样是 ASCII，材质包字体有）。
 *
 * 列版式下每列只有 100 出头像素，像 `KillAuraRecode`、`InventorySynchronization`
 * 这种长名字不截就会画到隔壁列上。
 */
fun FontRenderer.trimToWidth(text: String, maxWidth: Float): String {
    if (UiFont.width(this, text) <= maxWidth) return text
    var trimmed = text
    while (trimmed.isNotEmpty() && UiFont.width(this, "$trimmed...") > maxWidth) {
        trimmed = trimmed.substring(0, trimmed.length - 1)
    }
    return if (trimmed.isEmpty()) "" else "$trimmed..."
}
