package net.aspw.client.utils.render

import net.minecraft.client.renderer.GlStateManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 液态玻璃（Liquid Glass）。
 *
 * 全客户端 UI 共用的一套"玻璃"画法，五层叠出来：
 *
 * 1. **玻璃体**：半透明圆角底（色与透明度由调用方给）；
 * 2. **折射渐层**：上半段一层冷白，下三分之一压一点冷蓝 —— 玻璃的"厚度"和"折射"就是这两层；
 *    亮度还会随时间的正弦缓慢呼吸（`appear` 负责入场时的淡入）；
 * 3. **流动高光**：一条宽柔光带 + 里面一条更亮的窄芯，随时间横向扫过（每个面的相位由 [seed]
 *    错开，整屏不会同步闪）；悬停、刚切换状态时更亮；
 * 4. **玻璃边**：内圈上沿一道亮高光、下沿一道暗影、左右两条半高的竖边（光源在上）；
 * 5. **入场**：[appear] 0..1，同时控制底色透明度、渐层与高光的强度。
 *
 * 实现上只用 `RenderUtils` 里那些自带完整 GL 状态收尾的绘制（drawRect / drawGradientRect /
 * drawRoundedRect），**不用 glScissor、不动矩阵**：高光带的高度按圆角内缩过，所以放在 HUD、
 * ClickGUI（带缩放矩阵）还是主菜单里都安全，也不会糊到圆角外面。
 *
 * 强度是按"肉眼能一眼看出来"调的（宽带峰值 26/255、窄芯 40/255、顶边高光 88/255）。
 */
object Glass {

    /** 一次扫光的周期（毫秒）。越短越"活"，太长会显得像静态高光。 */
    private const val SWEEP_PERIOD_MS = 2600L

    /** 宽柔光带切成几条竖条（条数越多越柔，也越贵）。 */
    private const val SWEEP_STRIPS = 14

    /** 窄亮芯的条数。 */
    private const val CORE_STRIPS = 5

    private fun argb(rgb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /** 缓慢呼吸因子：0.75 ~ 1.25，周期约 7 秒。 */
    private fun breath(): Float =
        (1f + 0.25f * sin(System.currentTimeMillis() % 7000L / 7000.0 * Math.PI * 2.0)).toFloat()

    /**
     * 画一块玻璃面板。
     *
     * @param rgb    玻璃底色（0xRRGGBB）
     * @param alpha  玻璃底色透明度 0..255
     * @param hover  0..1 悬停强度，只影响高光与渐层
     * @param appear 0..1 入场进度（淡入 + 高光渐亮）
     * @param seed   这一面的扫光相位，传个稳定值（比如坐标散列）就互不同步
     */
    fun panel(
        x1: Float, y1: Float, x2: Float, y2: Float,
        radius: Float, rgb: Int, alpha: Int,
        hover: Float = 0f, seed: Int = 0, appear: Float = 1f
    ) {
        if (x2 - x1 < 2f || y2 - y1 < 2f || appear <= 0.02f) return

        RenderUtils.drawRoundedRect(x1, y1, x2, y2, radius, argb(rgb, (alpha * appear).toInt()))

        val breath = breath() * appear
        val height = y2 - y1

        // 折射：上半段冷白（随呼吸变化）。
        val tint = ((15f + 24f * hover) * breath).toInt().coerceIn(0, 255)
        RenderUtils.drawGradientRect(
            (x1 + 1f).toInt(), (y1 + 1f).toInt(), (x2 - 1f).toInt(), (y1 + height * 0.58f).toInt(),
            argb(0xFFFFFF, tint), argb(0xFFFFFF, 0)
        )

        // 折射：下三分之一压一点冷蓝，玻璃的"色散"就靠它。
        val cool = ((9f + 7f * hover) * breath).toInt().coerceIn(0, 255)
        RenderUtils.drawGradientRect(
            (x1 + 1f).toInt(), (y1 + height * 0.55f).toInt(), (x2 - 1f).toInt(), (y2 - 1f).toInt(),
            argb(0x8FB4FF, 0), argb(0x8FB4FF, cool)
        )

        sheen(x1, y1, x2, y2, radius, (1f + hover * 0.9f) * appear, seed)
        edges(x1, y1, x2, y2, radius, (1f + hover * 0.6f) * appear)
        // 本项目约定：这些绘制结束后把混合留着（后面的文字/圆角块直接接着画，不会突然变不透明）。
        GlStateManager.enableBlend()
    }

    /**
     * 只叠流动高光。已经有底色或强调色的面（启用行、胶囊、通知卡）用它就够了。
     *
     * @param strength 1 是常态，>1 更亮（悬停、刚切换状态时）
     */
    fun sheen(
        x1: Float, y1: Float, x2: Float, y2: Float,
        radius: Float, strength: Float, seed: Int = 0
    ) {
        val width = x2 - x1
        val height = y2 - y1
        if (width < 18f || height < 8f || strength <= 0.02f) return

        // 高光带高度按圆角内缩：这样它永远落在圆角矩形的直边区间里，不用裁剪也不会糊出圆角。
        val top = y1 + radius
        val bottom = y2 - radius
        if (bottom - top < 2f) return

        // 相位：时间 + 每个面的偏移。加一圈是为了避免负值把高光推到面板外面去。
        val phase = (((System.currentTimeMillis() % SWEEP_PERIOD_MS).toFloat() / SWEEP_PERIOD_MS
                + (seed % 977) * 0.00071f) % 1f + 1f) % 1f

        val band = (width * 0.36f).coerceIn(18f, 120f)
        val center = x1 - band + (width + band * 2f) * phase

        // 宽柔光带 + 里面一条更亮的窄芯。
        sweepBand(x1, x2, top, bottom, center, band, (25f * strength).coerceIn(0f, 255f), SWEEP_STRIPS)
        sweepBand(x1, x2, top, bottom, center, band * 0.34f, (40f * strength).coerceIn(0f, 255f), CORE_STRIPS)

        GlStateManager.enableBlend()
    }

    /** 在 [top]..[bottom] 上画一条以 [center] 为中心、宽 [band] 的钟形高光带。 */
    private fun sweepBand(
        x1: Float, x2: Float, top: Float, bottom: Float,
        center: Float, band: Float, peak: Float, strips: Int
    ) {
        if (peak <= 1f) return
        val step = band / strips
        for (i in 0 until strips) {
            val left = max(center + i * step, x1)
            val right = min(center + (i + 1) * step, x2)
            if (right - left < 0.4f) continue

            // 钟形亮度：中间最亮、两头渐隐，扫过去是柔的而不是一根硬条。
            val t = (i + 0.5f) / strips
            val alpha = (sin(t * Math.PI).toFloat() * peak).toInt()
            if (alpha <= 1) continue

            RenderUtils.drawRect(left, top, right, bottom, argb(0xFFFFFF, alpha))
        }
    }

    /**
     * 只叠玻璃边（内圈上高光 + 下暗影 + 两条半高的竖边）。给不方便换底色的面用。
     */
    fun edges(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, strength: Float) {
        if (x2 - x1 < 4f || y2 - y1 < 4f || strength <= 0.02f) return

        val inset = radius * 0.7f
        val rim = (88f * strength).toInt().coerceIn(0, 255)
        val shade = (38f * strength).toInt().coerceIn(0, 255)
        val midY = (y1 + y2) / 2f

        // 上沿高光（1px 亮线）+ 下沿暗影，玻璃的"厚边"。
        RenderUtils.drawRect(x1 + inset, y1 + 0.6f, x2 - inset, y1 + 1.6f, argb(0xFFFFFF, rim))
        RenderUtils.drawRect(x1 + inset, y2 - 1.6f, x2 - inset, y2 - 0.6f, argb(0x20242E, shade))
        // 左右两条只画上半段（光源在上）。
        RenderUtils.drawRect(x1 + 0.6f, y1 + inset, x1 + 1.6f, midY, argb(0xFFFFFF, rim / 2))
        RenderUtils.drawRect(x2 - 1.6f, y1 + inset, x2 - 0.6f, midY, argb(0xFFFFFF, rim / 3))
        GlStateManager.enableBlend()
    }
}
