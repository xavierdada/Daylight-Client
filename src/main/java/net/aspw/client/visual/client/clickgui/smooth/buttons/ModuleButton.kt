package net.aspw.client.visual.client.clickgui.smooth.buttons

import net.aspw.client.Launch
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.impl.visual.Gui
import net.aspw.client.utils.AnimationUtils
import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.Glass
import net.aspw.client.utils.render.RenderUtils.drawCircle
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.value.*
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.ANIM_SPEED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.ENABLED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.HOVER
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT_ON_ENABLED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.VALUE_HEIGHT
import net.aspw.client.visual.client.clickgui.smooth.buttons.value.*
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import net.aspw.client.visual.client.clickgui.smooth.makeGuiScissorBox
import net.aspw.client.visual.client.clickgui.smooth.mix
import net.aspw.client.visual.client.clickgui.smooth.trimToWidth
import net.aspw.client.visual.client.clickgui.smooth.withAlpha
import org.lwjgl.opengl.GL11
import java.awt.Color

/**
 * 一列里的一行模块。
 *
 * 版式照参考图：整行一条实心色带，没有圆角卡片、没有每行之间的空隙、也没有 `+/-` 箭头。
 * 启用时整行填成强调色（Gui 的 EnabledColor，默认橘粉）并把模块名写成白字；没启用时透出列的
 * 底色，悬停叠一层很淡的 [HOVER]。
 *
 * 设置仍然在行下面就地展开（右键这一行），展开的行按 [VALUE_HEIGHT] 往下排，整列由
 * [CategoryPanel] 统一裁剪。
 */
class ModuleButton(x: Float, y: Float, width: Float, height: Float, var module: Module, var color: Color) :
    Button(x, y, width, height) {
    var open = false
    var valueButtons = ArrayList<BaseValueButton>()

    /**
     * 这一列内容区的可见带（未缩放 GUI 单位），由 [CategoryPanel] 每帧画之前写进来。
     * 鼠标事件用它把被滚出列外的行挡掉 —— 否则点到列顶那一下会命中一条看不见的模块。
     */
    var clipTop = -100000f
    var clipBottom = 100000f

    /**
     * 刚被展开、还没被带进可视区。列在下一帧把它（连同设置）滚进可见范围，
     * 见 [CategoryPanel.drawPanel] —— 列底部的模块不这样处理的话，展开出来的设置会落在
     * 列底边之外，鼠标够不到。
     */
    var revealRequested = false

    /** 行悬停淡入与设置展开动画。 */
    private var hoverAnim = 0f
    private var animHeight = 0f
    private var lastFullHeight = 0f

    /** 状态刚翻转的那一下的"液态回弹"亮度，逐帧衰减。 */
    private var pulse = 0f
    private var lastState = false

    /** 点击涟漪：0..1 递减；[rippleX] 是点击点在行内的相对位置 0..1。 */
    private var ripple = 0f
    private var rippleX = 0.5f

    private fun enabledAccent(): Color {
        val argb = Launch.moduleManager.getModule(Gui::class.java)?.enabledColorValue?.get() ?: ENABLED.rgb
        return Color((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }

    init {
        val startY = y + height

        // Settings whose name carries a "Group-Name" prefix are folded into one collapsible row.
        // The rows keep the order of their first appearance and so do the members of a group, so a
        // setting that shares its prefix with an earlier one joins that row instead of opening one.
        val rows = ArrayList<Pair<String?, MutableList<Value<*>>>>()
        val groups = HashMap<String, MutableList<Value<*>>>()

        for (v in module.values) {
            val dash = v.name.indexOf('-')
            if (dash <= 0) {
                // No prefix: the setting keeps a row of its own, exactly as before.
                rows.add(Pair<String?, MutableList<Value<*>>>(null, arrayListOf<Value<*>>(v)))
                continue
            }

            val prefix = v.name.substring(0, dash)
            val group = groups[prefix]
            if (group != null) group.add(v)
            else {
                val created = arrayListOf<Value<*>>(v)
                groups[prefix] = created
                rows.add(Pair<String?, MutableList<Value<*>>>(prefix, created))
            }
        }

        for ((index, row) in rows.withIndex()) {
            val rowY = startY + VALUE_HEIGHT * index
            val prefix = row.first

            if (prefix == null) {
                val only = row.second.firstOrNull() ?: continue
                val button = createValueButton(only, rowY) ?: continue
                valueButtons.add(button)
                continue
            }

            val children = ArrayList<GroupValueButton.Child>()
            for (v in row.second) {
                val button = createValueButton(v, rowY) ?: continue
                children.add(GroupValueButton.Child(button) { v.canDisplay.invoke() })
            }
            // A group whose settings all lack a row (font values, for instance) would be an empty
            // header, so it is dropped instead.
            if (children.isEmpty()) continue

            // A lone setting keeps its own row: folding it into a group would only cost the user
            // an extra click to reach a single slider.
            if (children.size == 1) {
                valueButtons.add(children[0].button)
                continue
            }

            valueButtons.add(GroupValueButton(x, rowY, width, VALUE_HEIGHT, prefix, children))
        }

        // Key bind sits after every value so it is reachable for modules without settings too.
        valueButtons.add(
            KeyBindButton(x, startY + VALUE_HEIGHT * rows.size, width, VALUE_HEIGHT, module)
        )
        // Seed the reveal target so the very first expand already has something to ease towards.
        lastFullHeight = valueButtons.size * VALUE_HEIGHT
    }

    /**
     * Builds the row that draws [v] at [rowY], or null for value types the Smooth GUI has no row
     * for. Rows are repositioned on every frame by [drawPanel], so [rowY] is only a first guess.
     */
    private fun createValueButton(v: Value<*>, rowY: Float): BaseValueButton? = when (v) {
        is BoolValue -> BoolValueButton(x, rowY, width, VALUE_HEIGHT, v, color)
        is ListValue -> ListValueButton(x, rowY, width, VALUE_HEIGHT, v, color)
        is IntegerValue -> IntegerValueButton(x, rowY, width, VALUE_HEIGHT, v, color)
        is FloatValue -> FloatValueButton(x, rowY, width, VALUE_HEIGHT, v, color)
        is TextValue -> TextValueButton(x, rowY, width, VALUE_HEIGHT, v, color)
        is ColorValue -> ColorValueButton(x, rowY, width, VALUE_HEIGHT, v)
        else -> null
    }

    /** Feeds a key press to the rows of this module; true when one of them consumed it. */
    fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!open) return false
        for (valueButton in valueButtons)
            if (valueButton.show && valueButton.keyTyped(typedChar, keyCode)) return true
        return false
    }

    /** 一个矩形（含展开的设置行）是否落在这一列的可见带里。 */
    fun inBand(top: Float, bottom: Float): Boolean = bottom > clipTop && top < clipBottom

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val hovered = isHovered(mouseX, mouseY) && inBand(y, y + height)
        hoverAnim = AnimationUtils.animate(if (hovered) 1f else 0f, hoverAnim, ANIM_SPEED)

        // 状态刚翻转的那一下给个"液态回弹"：高光更亮，然后快速衰减。
        if (module.state != lastState) {
            lastState = module.state
            pulse = 1f
        }
        pulse *= 0.86f
        if (pulse < 0.01f) pulse = 0f

        val seed = x.toInt() * 31 + y.toInt() * 7

        if (module.state) {
            // 启用：整行强调色实心，悬停/刚切换时往白里提。
            drawRect(x, y, x + width, y + height, mix(enabledAccent(), Color.WHITE, hoverAnim * 0.18f + pulse * 0.3f).rgb)
            // 液态玻璃：色带上叠流动高光 + 玻璃边。
            Glass.sheen(x, y, x + width, y + height, 0f, 1.4f + hoverAnim * 0.6f + pulse * 1.8f, seed)
            Glass.edges(x, y, x + width, y + height, 0f, 1f + pulse)
        } else if (hoverAnim > 0.01f) {
            drawRect(x, y, x + width, y + height, withAlpha(HOVER.rgb, (95 * hoverAnim).toInt()))
            Glass.sheen(x, y, x + width, y + height, 0f, 1.1f * hoverAnim, seed)
        }

        // 点击涟漪：一圈从点击位置扩散出去的细环。
        if (ripple > 0f) {
            val ringRadius = (1f - ripple) * width * 0.8f + 3f
            val ringAlpha = (150 * ripple).toInt().coerceIn(0, 255)
            drawCircle(
                x + rippleX * width, y + height / 2f, ringRadius, 1.2f, 0, 360,
                if (module.state) Color(255, 255, 255, ringAlpha) else Color(90, 100, 140, ringAlpha)
            )
            ripple -= 0.07f
            if (ripple < 0f) ripple = 0f
        }

        // 名字：启用行白字，未启用行深色；悬停时整行文字往右滑一点点（"液态"跟随感）。
        val nameColor = if (module.state) TEXT_ON_ENABLED else TEXT
        FONT.drawHeightCenteredString(
            FONT.trimToWidth(module.name, width - 12f - hoverAnim * 2f),
            x + 6f + hoverAnim * 2f,
            y + height / 2f,
            nameColor
        )

        // Settings reveal: the rows are clipped to the eased height, so the modules below slide
        // into place instead of jumping when a module is opened or closed.
        var used = 0f
        if (open || animHeight > 0.01f) {
            val clipped = animHeight > 0.01f
            if (clipped) {
                makeGuiScissorBox(x, y + height, x + width, y + height + animHeight)
                GL11.glEnable(3089)
            }

            val startY = y + height
            for (valueButton in valueButtons) {
                if (!valueButton.canDisplay()) {
                    valueButton.show = false
                    continue
                }

                valueButton.show = true

                valueButton.x = x
                valueButton.y = startY + used
                valueButton.width = width
                used += valueButton.drawPanel(mouseX, mouseY).height
            }

            if (clipped) GL11.glDisable(3089)
            if (open) lastFullHeight = used
        }

        animHeight = AnimationUtils.animate(if (open) lastFullHeight else 0f, animHeight, ANIM_SPEED)

        return Rectangle(x, y, width, animHeight + height)
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!click) return
        // 滚出列外的行不响应点击。
        if (!inBand(y, y + height)) return
        if (!isHovered(mouseX, mouseY)) return
        when (button) {
            MouseButtons.LEFT.ordinal -> {
                module.toggle()
                // 点哪儿就从哪儿荡开一圈涟漪。
                ripple = 1f
                rippleX = ((mouseX - x) / width).coerceIn(0f, 1f)
            }
            MouseButtons.RIGHT.ordinal -> {
                open = !open
                if (open) revealRequested = true
            }
        }
    }
}
