package net.aspw.client.visual.font

import net.aspw.client.Launch
import net.aspw.client.features.module.impl.visual.Gui
import net.aspw.client.visual.font.semi.Fonts
import net.aspw.client.visual.font.semi.GameFontRenderer
import net.aspw.client.visual.font.smooth.CFontRenderer
import net.aspw.client.visual.font.smooth.FontLoaders
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager

/**
 * 界面文字的统一入口。
 *
 * 客户端界面原本是自带 TTF 画的（[FontLoaders.SF15]…[Fonts.font35] 等，都是 AWT 渲染），
 * 而材质包换的是原版字形图 `assets/minecraft/font/default.png`（以及 `font/glyph_*.png`），
 * 只有原版的 [FontRenderer] 会读它。所以 TTF 渲染器**永远**不可能受材质包影响。
 *
 * 这里做的事就是：Gui 模块的 `PackFont` 开关打开时，把界面文字改走 [Fonts.minecraftFont]
 * （原版 FontRenderer），并用 `GlStateManager.scale` 把原版 9px 的字形缩放到自带字体那一档
 * 的显示高度。
 *
 * ## 为什么有四个入口
 * 客户端的两族自带字体**没有共同的基类**：
 * - [net.aspw.client.visual.font.semi.Fonts.font35] 等是 [GameFontRenderer]，继承原版
 *   [FontRenderer]，重载是 `drawString(String, Float, Float, Int[, Boolean]): Int`；
 * - [FontLoaders.SF15]…`SF21` 是 [CFontRenderer]，继承
 *   [net.aspw.client.visual.font.smooth.CFont]，**和原版 [FontRenderer] 毫无关系**，
 *   重载是 `drawString(String, Double, Double, Int[, Boolean]): Float`。
 *
 * 所以这里做成两组类型化重载（`FontRenderer` 一组、`CFontRenderer` 一组），而不是一个
 * `Any` 参数的万能签名——传错字体类型会直接在编译期报错。真正的实现只有一套，两组重载都
 * 转发到它。
 *
 * 用法（`ref` 就是调用点原本要用的那个自带字体）：
 * ```
 * UiFont.draw(FontLoaders.SF20, text, x, y, color)      // HUD（CFontRenderer）
 * UiFont.draw(Fonts.fontSFUI35, text, x, y, color)      // Smooth ClickGUI（GameFontRenderer）
 * val w = UiFont.width(FontLoaders.SF20, text)          // 测宽，右对齐 / 居中必须用同一个
 * val h = UiFont.lineHeight(font)                       // 行内垂直居中 / 左内边距
 * ```
 * 如果"有些地方走 [UiFont]、有些地方直接用 FontLoaders"，开关打开后就会字体混杂，
 * 所以这三个界面里的文字绘制与测宽都应该只走这里。
 *
 * ## 固有代价
 * 界面是按自带 TTF 的**字宽**排版的。换成原版字体后字宽必然不同，右对齐、居中、按钮上的
 * 文字都会有位移。这是换字体的固有代价，不为了对齐去改版式；默认缩放（见 [scaleOf]）特意
 * 选得保守（0.88–1.10），让文字在不改动版式的前提下大致仍能塞进原来的行高。
 */
object UiFont {

    /** `PackFont` 开关是否打开。模块还没注册（启动早期）时当作 false。 */
    val usePack: Boolean
        get() = Launch.moduleManager.getModule(Gui::class.java)?.packFontValue?.get() == true

    /** 原版字体的行高（9px）。开材质包字体后文字的实际显示高度是 `9 * scale`。 */
    const val VANILLA_HEIGHT = 9

    // ---- 缩放表 ---------------------------------------------------------------------------

    /**
     * 一档自带字体对应的原版缩放比。
     *
     * 自带字体是 AWT 渲染的：`GameFontRenderer.height` = AWT 行高 / 2，`CFontRenderer` 的
     * 字号就是 AWT point size。按"显示高度 / 9"精确换算会让文字溢出为 TTF 排好的行高
     * （Smooth ClickGUI 的 `VALUE_HEIGHT` 只有 10），所以比例被压进 0.88–1.10 这个保守区间。
     *
     * | 字体                                   | 参考字号 | 比例 | 原版字形显示高度 |
     * |----------------------------------------|----------|------|------------------|
     * | `SF15`                                 | 15pt     | 0.88 | ≈7.9 px          |
     * | `SF16`、`fontSmall`(30)、`fontTahoma30`| 16/30pt  | 0.90 | ≈8.1 px          |
     * | `SF18`、`fontTiny`(24)                 | 18/24pt  | 0.92 | ≈8.3 px          |
     * | `SF20`、`SF21`、`font35`、`fontSFUI35` | 20/21/35 | 1.00 | 9 px             |
     * | `fontSFUI37`、`fontTahoma38`           | 37/38pt  | 1.05 | ≈9.5 px          |
     * | `font40`、`fontSFUI40`、`font72`       | 40/72pt  | 1.10 | ≈9.9 px          |
     *
     * HUD 那一档（SF20/SF21）与原版字体是 1:1，所以 CPS / 水印 / SessionInfo / ArrayList /
     * TargetHud 的文字框尺寸基本不变。主菜单空间充裕，它的标题由调用点显式传
     * [MAIN_MENU_TITLE_SCALE] 再放大一档。
     *
     * 下面这个重载只用引用相等认 [GameFontRenderer]（[GameFontRenderer.size] 是 AWT point
     * size，分档比按引用认单只字体更稳）；[FontLoaders] 那几只 [CFontRenderer] 由另一个
     * 重载处理。`Fonts.xxx` 为 null（`loadFonts()` 还没跑）时 `===` 依然安全。
     */
    fun scaleOf(ref: FontRenderer): Float {
        if (ref === Fonts.fontSmall) return 0.90f
        if (ref === Fonts.fontTiny) return 0.92f
        if (ref === Fonts.font35 || ref === Fonts.fontSFUI35) return 1.0f
        if (ref === Fonts.fontSFUI37 || ref === Fonts.fontTahoma38) return 1.05f
        if (ref === Fonts.font40 || ref === Fonts.fontSFUI40 || ref === Fonts.font72 ||
            ref === Fonts.fontLarge || ref === Fonts.fontTahoma || ref === Fonts.fontBangers ||
            ref === Fonts.fontPixel || ref === Fonts.mojangles || ref === Fonts.mojanglesBold ||
            ref === Fonts.niSans
        ) return 1.10f

        if (ref !is GameFontRenderer) return 1.0f
        return when {
            ref.size <= 24 -> 0.90f
            ref.size <= 30 -> 0.92f
            ref.size <= 37 -> 1.0f
            ref.size <= 52 -> 1.05f
            else -> 1.10f
        }
    }

    /**
     * [CFontRenderer] 一族（`FontLoaders.SF15`…`SF21`）的缩放。
     *
     * `CFont.font` 就是那只 AWT 字体，字号可直接读，所以直接按字号分档；引用相等只在
     * 字号档需要微调时才用，而且 `FontLoaders.SFxx` 为 null 时 `===` 依然安全。
     */
    fun scaleOf(ref: CFontRenderer): Float {
        val size = ref.font?.size ?: 20
        return when {
            ref === FontLoaders.SF20 || ref === FontLoaders.SF21 -> 1.0f
            size <= 15 -> 0.88f
            size <= 16 -> 0.90f
            size <= 18 -> 0.92f
            size <= 21 -> 1.0f
            else -> 1.05f
        }
    }

    /** 主菜单标题的缩放：菜单空间充裕，允许比 HUD 那一档再大一档。 */
    const val MAIN_MENU_TITLE_SCALE = 1.35f

    // ---- 行高 -----------------------------------------------------------------------------

    /**
     * 某一档文字开 / 关材质包字体时的实际显示高度（像素）。
     *
     * - 关：`ref` 自己的高度（[GameFontRenderer.height]，或 [FontRenderer.FONT_HEIGHT]——后者
     *   被 [GameFontRenderer] 在构造时改写过，所以数值和 TTF 一致）。
     * - 开：原版 9px 乘以缩放比例。
     *
     * [net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString] 和
     * `BaseValueButton.hOffset` 用它算偏移，否则换字体后文字会贴着行的上下边缘。
     */
    fun lineHeight(ref: FontRenderer): Int =
        if (usePack) (VANILLA_HEIGHT * scaleOf(ref)).toInt()
        else if (ref is GameFontRenderer) ref.height
        else ref.FONT_HEIGHT

    /** [CFontRenderer] 一族的行高。 */
    fun lineHeight(ref: CFontRenderer): Int =
        if (usePack) (VANILLA_HEIGHT * scaleOf(ref)).toInt()
        else ref.height

    // ---- 绘制 -----------------------------------------------------------------------------

    /**
     * 画一段文字，返回它占用的宽度（像素），方便紧接着画下一段（例如 `Name` + `§4Value`）。
     *
     * - 关：[ref] 自己画，走它的 `drawString` 重载（这一族的 [CFontRenderer] 都是无阴影版本）。
     * - 开：[Fonts.minecraftFont] 画，外面套一层 `pushMatrix / translate / scale / translate`，
     *   放大到 `ref` 那一档。`try/finally` 保证即使绘制抛异常也会 `popMatrix`。
     *
     * @param ref    调用点原本要用的自带字体，同时决定缩放比例。
     * @param shadow 是否在 (x+0.5, y+0.5) 先画一份暗色副本。自带字体的阴影是偏色副本，而原版
     *   字体自带的 `shadow = true` 是"把颜色乘 0.25"，观感差得多，所以两边都用手动副本，
     *   这样开 / 关开关的视觉重量一致。
     */
    @JvmOverloads
    fun draw(
        ref: CFontRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean = false,
        scale: Float = scaleOf(ref)
    ): Float = drawRef(CFontRef.of(ref), text, x, y, color, shadow, scale)

    /** [GameFontRenderer] / 原版 [FontRenderer] 那一组重载。 */
    @JvmOverloads
    fun draw(
        ref: FontRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean = false,
        scale: Float = scaleOf(ref)
    ): Float = drawRef(CFontRef.of(ref), text, x, y, color, shadow, scale)

    /** 带阴影画一段文字，等价于 `draw(..., shadow = true)`。 */
    fun drawWithShadow(
        ref: CFontRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int
    ): Float = drawRef(CFontRef.of(ref), text, x, y, color, true, scaleOf(ref))

    /** [drawWithShadow] 的另一组重载。 */
    fun drawWithShadow(
        ref: FontRenderer,
        text: String,
        x: Float,
        y: Float,
        color: Int
    ): Float = drawRef(CFontRef.of(ref), text, x, y, color, true, scaleOf(ref))

    // ---- 测宽（必须与 draw 用同一个 scale，否则右对齐 / 居中会错位） ---------------------

    /** 文字的显示宽度。[FontRenderer.getStringWidth] 与原版一样会跳过 `§x` 颜色码。 */
    @JvmOverloads
    fun width(ref: CFontRenderer, text: String, scale: Float = scaleOf(ref)): Float =
        if (usePack) Fonts.minecraftFont.getStringWidth(text).toFloat() * scale
        else ref.getStringWidth(text).toFloat()

    /** [width] 的另一组重载。 */
    @JvmOverloads
    fun width(ref: FontRenderer, text: String, scale: Float = scaleOf(ref)): Float =
        if (usePack) Fonts.minecraftFont.getStringWidth(text).toFloat() * scale
        else ref.getStringWidth(text).toFloat()

    /** [width] 的 Int 版本，给只接受 Int 的调用点（例如 GuiButton 的 x 坐标）。 */
    @JvmOverloads
    fun widthInt(ref: CFontRenderer, text: String, scale: Float = scaleOf(ref)): Int =
        width(ref, text, scale).toInt()

    /** [widthInt] 的另一组重载。 */
    @JvmOverloads
    fun widthInt(ref: FontRenderer, text: String, scale: Float = scaleOf(ref)): Int =
        width(ref, text, scale).toInt()

    // ---- 内部实现 -------------------------------------------------------------------------

    /**
     * 字体种类的运行期标记。存在的唯一理由：两族自带字体没有共同基类，而要实现"一套实现 +
     * 两组类型化重载"，就必须有一个能把两者装进同一个变量的载体。构造只能走 [CFontRef.of]，
     * 所以标记与真实类型永远一致。
     */
    private class CFontRef private constructor(
        val vanilla: FontRenderer?,
        val custom: CFontRenderer?
    ) {
        companion object {
            fun of(ref: FontRenderer) = CFontRef(ref, null)
            fun of(ref: CFontRenderer) = CFontRef(null, ref)
        }
    }

    private fun drawRef(
        ref: CFontRef,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean,
        scale: Float
    ): Float {
        if (!usePack) return drawWithTtf(ref, text, x, y, color, shadow)

        val font = Fonts.minecraftFont
        var drawn = font.getStringWidth(text).toFloat() * scale
        if (shadow)
            drawn = maxOf(drawn, drawWithPack(font, text, x + 0.5f, y + 0.5f, SHADOW_COLOR, scale))
        drawn = maxOf(drawn, drawWithPack(font, text, x, y, color, scale))
        return drawn
    }

    /**
     * 自带 TTF 那条路。两族的重载不同：`GameFontRenderer` 用 Float，[CFontRenderer] 用
     * Double（它的 `(String, Double, Double, Int, Boolean)` 是唯一带阴影参数的重载）。
     */
    private fun drawWithTtf(
        ref: CFontRef,
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean
    ): Float {
        val custom = ref.custom
        if (custom != null) return custom.drawString(text, x.toDouble(), y.toDouble(), color, shadow)

        val vanilla = ref.vanilla
        return if (vanilla is GameFontRenderer) vanilla.drawString(text, x, y, color, shadow).toFloat()
        else vanilla!!.drawString(text, x, y, color, shadow).toFloat()
    }

    /**
     * 原版字体那条路：把矩阵平移 / 缩放到 [scale] 再画。
     *
     * `pushMatrix` 与 `popMatrix` 严格配对，`finally` 里兜底。原版 `drawString` 内部会做
     * `GlStateManager.translate(0, -2, 0)`（在被缩放的坐标系里），所以放大后文字会略微上移，
     * 这是原版字体自带的基线偏移，不做补偿。
     */
    private fun drawWithPack(font: FontRenderer, text: String, x: Float, y: Float, color: Int, scale: Float): Float {
        GlStateManager.pushMatrix()
        try {
            GlStateManager.translate(x, y, 0f)
            GlStateManager.scale(scale, scale, 1f)
            GlStateManager.translate(-x, -y, 0f)
            font.drawString(text, x, y, color, false)
        } finally {
            GlStateManager.popMatrix()
        }
        return font.getStringWidth(text).toFloat() * scale
    }

    /** 与 Interface / Smooth GUI 手动画的阴影同一个颜色（ARGB，alpha 150）。 */
    private const val SHADOW_COLOR = 0x96000000.toInt()
}
