package net.aspw.client.visual.client.clickgui.smooth.buttons

import net.aspw.client.Launch
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.utils.AnimationUtils
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.Glass
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.ANIM_SPEED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_CATEGORY_COLOR
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.HOVER
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.MODULE_HEIGHT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.PANEL_HEIGHT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.PANEL_WIDTH
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SCROLL_THUMB
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SCROLL_TRACK
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SEPARATOR
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import net.aspw.client.visual.client.clickgui.smooth.makeGuiScissorBox
import net.aspw.client.visual.client.clickgui.smooth.mix
import org.lwjgl.opengl.GL11
import java.awt.Color

/**
 * 一个分类一列。
 *
 * 版式照参考图：整列一块浅灰圆角底板，最上面一条是分类名（没有图标、没有单独的标题栏），
 * 下面是一行行模块，列内的内容自己滚动并被裁剪到内容区。右键列头可以把整列收起来，
 * 收起后只剩那条标题。
 *
 * 位置与底边由 [net.aspw.client.visual.client.clickgui.smooth.SmoothClickGui] 每帧写入
 * （列宽固定、列数固定，不再靠拖动排布）。
 */
class CategoryPanel(x: Float, y: Float, var category: ModuleCategory, var color: Color) :
    Button(x, y, PANEL_WIDTH, PANEL_HEIGHT) {
    /** 整列是否展开（右键列头切换）。 */
    var open = true
    var moduleButtons = ArrayList<ModuleButton>()
    val name = category.displayName

    /** 这一列内容区滚动到哪了（未缩放 GUI 单位，0 = 顶部）。 */
    var scroll = 0f

    /** 列的底边，由 SmoothClickGui 每帧写入。 */
    var bottom = y + PANEL_HEIGHT

    /** 上一次画出来的内容总高，用来夹滚动量。 */
    private var contentHeight = 0f
    private var hoverAnim = 0f

    /** 第几列（由 SmoothClickGui 每帧写入），错峰入场用。 */
    var staggerIndex = 0

    /** 入场进度 0..1，按列号错峰缓动。 */
    private var appear = 0f

    init {
        val startY = y + height
        for ((count, mod) in Launch.moduleManager.modules.filter {
            it.category.displayName.equals(
                this.category.displayName,
                true
            )
        }.withIndex()) {
            moduleButtons.add(ModuleButton(x, startY + MODULE_HEIGHT * count, width, MODULE_HEIGHT, mod, color))
        }
    }

    /** 内容区高度（0 表示整列收起）。 */
    private fun visibleHeight(): Float = (bottom - (y + height)).coerceAtLeast(0f)

    private fun maxScroll(): Float = (contentHeight - visibleHeight()).coerceAtLeast(0f)

    /**
     * 鼠标是不是在**整列**范围内（不只是列头那一条）。
     *
     * 注意不能用 [Button.isHovered]：它的高度是 [PANEL_HEIGHT]，只覆盖列头，于是滚轮只有把鼠标
     * 放到标题上才生效 —— 列表里的内容就再也滚不到了。
     */
    fun isInColumn(mouseX: Int, mouseY: Int): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= bottom

    /** 滚轮滚这一列。收起状态或内容装得下时什么也不做。 */
    fun scrollBy(amount: Float) {
        if (!open) return
        scroll = (scroll + amount).coerceIn(0f, maxScroll())
    }

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val hovered = isHovered(mouseX, mouseY)
        hoverAnim = AnimationUtils.animate(if (hovered) 1f else 0f, hoverAnim, ANIM_SPEED)

        // 错峰入场：整屏淡入进度按列号往后推一点，每列自己缓动，并从下方 10px 滑上来。
        val target = ((SmoothConstants.guiOpen - staggerIndex * 0.07f) / 0.45f).coerceIn(0f, 1f)
        appear = AnimationUtils.animate(target, appear, 0.22f)
        y += (1f - appear) * 10f

        val panelBottom = if (open) bottom else y + height
        // 液态玻璃：玻璃体 + 折射渐层 + 流动高光 + 玻璃边（悬停更亮、入场时淡入）。
        Glass.panel(
            x, y, x + width, panelBottom, RADIUS,
            mix(BACKGROUND_CATEGORY_COLOR, HOVER, hoverAnim * 0.35f).rgb, (186 * appear).toInt(),
            hoverAnim, x.toInt() * 31 + y.toInt(), appear
        )

        // 列头：分类名 + 一条细分隔线。
        FONT.drawHeightCenteredString(name, x + 6f, y + height / 2f, TEXT)
        drawRect(x + 5f, y + height - 0.5f, x + width - 5f, y + height, SEPARATOR)

        if (!open) {
            contentHeight = 0f
            scroll = 0f
            return Rectangle()
        }

        val contentTop = y + height
        val visible = visibleHeight()
        scroll = scroll.coerceIn(0f, maxScroll())

        makeGuiScissorBox(x, contentTop, x + width, bottom)
        GL11.glEnable(3089)

        var used = 0f
        var revealScroll = -1f
        val startY = contentTop - scroll
        for (moduleButton in moduleButtons) {
            moduleButton.x = x
            moduleButton.width = width
            moduleButton.y = startY + used
            moduleButton.clipTop = contentTop
            moduleButton.clipBottom = bottom
            val rowOffset = used
            val rowHeight = moduleButton.drawPanel(mouseX, mouseY).height
            used += rowHeight
            // 刚被展开的模块：把它（连同设置）带进可视区。不然列底部那几个模块展开后设置落在
            // 列底边之外，鼠标根本够不到，看起来就是"改不了设置"。
            if (moduleButton.revealRequested) {
                moduleButton.revealRequested = false
                val bandHeight = (bottom - contentTop).coerceAtLeast(1f)
                val desired = if (rowHeight > bandHeight) {
                    // 设置比整列还高：把行顶对齐到内容区顶部，从第一项开始看/改。
                    rowOffset
                } else {
                    // 装得下：只把底部推进来，同时不让行顶被推出内容区。
                    (rowOffset + rowHeight - bandHeight).coerceAtMost(rowOffset)
                }
                if (desired > revealScroll) revealScroll = desired
            }
        }

        GL11.glDisable(3089)

        contentHeight = used

        if (revealScroll > 0f) {
            // contentHeight 已经在上面更新过，所以这里 maxScroll() 拿的是展开之后的高度。
            scroll = maxOf(scroll, revealScroll).coerceIn(0f, maxScroll())
        }

        // 滚动条：内容比可见区高时才画。
        val max = maxScroll()
        if (max > 0.5f) {
            val trackX1 = x + width - 4.5f
            val trackX2 = x + width - 2.5f
            drawRect(trackX1, contentTop, trackX2, bottom, SCROLL_TRACK)
            val thumbHeight = (visible * visible / used).coerceIn(16f, visible)
            val thumbY = contentTop + (visible - thumbHeight) * (scroll / max)
            drawRect(trackX1, thumbY, trackX2, thumbY + thumbHeight, SCROLL_THUMB)
        }

        return Rectangle()
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!click) return
        if (mouseX < x || mouseX > x + width) return
        // 只有列头响应，否则右键展开某个模块会把整列收起来。
        if (mouseY < y || mouseY > y + height) return
        if (button == 1) open = !open
    }
}
