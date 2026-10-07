package net.aspw.client.visual.client.clickgui.smooth

import net.aspw.client.visual.font.semi.Fonts
import net.minecraft.client.gui.FontRenderer
import java.awt.Color

/**
 * 列式 ClickGUI（Smooth 风格）的尺寸与配色。
 *
 * 版式照参考图：每个分类一列、最后一列是配置管理，列内是紧凑的一行行模块，启用行整行填成
 * 强调色。配色保持本项目的浅色主题（浅灰面板 + 深色文字），启用行用橘粉强调色 + 白字，
 * 这一点跟参考图一致。
 *
 * 所有的 *COLOR 变体是给需要混色的地方用的（`Color` 而不是打包好的 Int）。
 */
object SmoothConstants {
    // ------------------------------------------------------------------ 列版式

    /** 一列的宽度。太窄会放不下模块名，所以版式会整体缩放来塞进屏幕（见 SmoothClickGui）。 */
    const val PANEL_WIDTH = 104f

    /** 列头（分类名那条）的高度。 */
    const val PANEL_HEIGHT = 20f

    /** 一行模块的高度。 */
    const val MODULE_HEIGHT = 18f

    /** 一行设置（滑块 / 开关 / 取色 / 绑定键）的高度，值控件按它排版。 */
    const val VALUE_HEIGHT = 10f

    /** 列与列之间的空隙。 */
    const val COLUMN_GAP = 5f

    /** 列顶部距屏幕顶边的距离。 */
    const val COLUMN_TOP = 10f

    /** 列底部距屏幕底边的距离。 */
    const val COLUMN_BOTTOM_MARGIN = 10f

    val FONT: FontRenderer
        get() = Fonts.fontSFUI35

    const val SLIDER_OFFSET = 0.02f

    /** 列的圆角。 */
    const val RADIUS = 4f

    /** 行悬停淡入与展开/收起动画的速度。 */
    const val ANIM_SPEED = 0.3f

    /**
     * 当前绘制所用的总缩放。行的裁剪框要乘上它，缩放后裁剪才会跟着对齐。
     */
    var uiScale = 1f

    /**
     * ClickGUI 这一次打开的整体淡入进度 0..1，由 SmoothClickGui 每帧写入。
     * 各列用它做"错峰入场"（列号越大越晚），见 CategoryPanel.drawPanel。
     */
    var guiOpen = 1f

    // ------------------------------------------------------------------ 浅色面板

    val BACKGROUND_CATEGORY_COLOR = Color(224, 226, 236)
    val BACKGROUND_CATEGORY = BACKGROUND_CATEGORY_COLOR.rgb
    val BACKGROUND_MODULE_COLOR = Color(246, 246, 250)
    val BACKGROUND_MODULE = BACKGROUND_MODULE_COLOR.rgb
    val BACKGROUND_VALUE_COLOR = Color(233, 234, 242)
    val BACKGROUND_VALUE = BACKGROUND_VALUE_COLOR.rgb

    /** 鼠标悬停在行上时叠上去的淡色。 */
    val HOVER = Color(214, 221, 238)

    /** 行内的细分隔线（列头下面那条、配置行之间）。 */
    val SEPARATOR = Color(198, 200, 212).rgb

    /** 列内滚动条的轨道与滑块。 */
    val SCROLL_TRACK = Color(0, 0, 0, 18).rgb
    val SCROLL_THUMB = Color(120, 124, 140, 120).rgb

    /**
     * 启用行默认的强调色。只是兜底：ModuleButton 会优先读 Gui 模块的 "EnabledColor" 设置
     * （默认值就是这个橘粉）。
     */
    val ENABLED = Color(242, 167, 157)

    /** 写在强调色行上的文字（参考图里启用行是白字）。 */
    val TEXT_ON_ENABLED = Color(255, 255, 255).rgb

    // ------------------------------------------------------------------ 浅色文字

    val TEXT = Color(28, 28, 34).rgb
    val TEXT_MUTED = Color(108, 110, 122).rgb
    val TEXT_ACCENT = Color(48, 82, 176).rgb

    /** 配置管理列里那些小按钮的底板与图标色。 */
    val CONFIG_BUTTON = Color(233, 234, 242).rgb
    val CONFIG_BUTTON_HOVER = Color(214, 221, 238).rgb
    val CONFIG_ICON = Color(84, 88, 102).rgb
    val CONFIG_ICON_ON = Color(255, 255, 255).rgb

    // ------------------------------------------------------------------ 值控件（取色器）

    /** 取色器那一整块 HSV 控件的底板。 */
    val COLOR_PICKER_BACKGROUND = Color(244, 245, 250).rgb

    /** 控件与底板之间的细分隔线。 */
    val OUTLINE = Color(196, 198, 208).rgb

    /** 透明度棋盘格的两色。 */
    val CHECKER_LIGHT = Color(255, 255, 255).rgb
    val CHECKER_DARK = Color(202, 204, 214).rgb

    /** 色相条 / 饱和度亮度块 / 透明度条上的标记点。 */
    val MARKER = Color(255, 255, 255).rgb
    val MARKER_OUTLINE = Color(58, 60, 72).rgb
}
