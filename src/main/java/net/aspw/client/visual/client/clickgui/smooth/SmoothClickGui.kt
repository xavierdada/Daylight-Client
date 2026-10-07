package net.aspw.client.visual.client.clickgui.smooth

import net.aspw.client.Launch
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.impl.visual.Gui
import net.aspw.client.utils.AnimationUtils
import net.aspw.client.utils.APIConnecter
import net.aspw.client.utils.misc.RandomUtils
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.visual.client.clickgui.smooth.buttons.CategoryPanel
import net.aspw.client.visual.client.clickgui.smooth.buttons.ConfigPanel
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.io.IOException
import kotlin.math.roundToInt

/**
 * 列式 ClickGUI。
 *
 * 版式照参考图：**每个分类一列**并排铺开，最后一列是配置管理；列内是紧凑的一行行模块
 * （启用行整行填强调色），列内容超出就自己滚动、并被裁剪在列内。列不再能拖动，位置由这里每帧
 * 按屏幕宽度算：列宽固定 [SmoothConstants.PANEL_WIDTH]，列数就是分类数 + 1，装不下时整体
 * 等比缩小（不是把列压窄），所以任何分辨率下列的比例都一样。
 *
 * 鼠标坐标要按同一个缩放反算回 GUI 坐标——缩放是"以屏幕中心为锚点"做的，直接除以 scale
 * 只在 scale = 1 时对（旧版就是这么写的），这里用 [toGuiX]/[toGuiY] 精确反算。
 */
class SmoothClickGui : GuiScreen() {
    private var panels = ArrayList<CategoryPanel>()
    private lateinit var configPanel: ConfigPanel

    /** 随机立绘编号（Gui 模块的 Pictures 打开时画在面板后面）。 */
    private var randomPicture: String

    /** 缓入的 0..1，用来淡入背景遮罩与面板。 */
    private var openProgress = 0f

    /**
     * 这一帧的总缩放：用户 Scale × 自适应缩放 × 开场动画。鼠标坐标要用它反算，
     * 面板的裁剪框也用它（写进 [SmoothConstants.uiScale]）。
     */
    private var zoom = 1f

    init {
        for (cat in ModuleCategory.values()) {
            panels.add(CategoryPanel(0f, SmoothConstants.COLUMN_TOP, cat, Color(86, 124, 214)))
        }
        configPanel = ConfigPanel(0f, SmoothConstants.COLUMN_TOP, SmoothConstants.PANEL_WIDTH)
        randomPicture = RandomUtils.nextInt(1, 7).toString()
    }

    private val userScale: Float
        get() = Launch.moduleManager.getModule(Gui::class.java)?.scaleValue?.get() ?: 1f

    /** 屏幕坐标 -> GUI 坐标（缩放的逆变换，锚点是屏幕中心）。 */
    private fun toGuiX(x: Int): Int = (width / 2f + (x - width / 2f) / zoom).roundToInt()

    private fun toGuiY(y: Int): Int = (height / 2f + (y - height / 2f) / zoom).roundToInt()

    override fun drawScreen(mouseXIn: Int, mouseYIn: Int, partialTicks: Float) {
        openProgress = AnimationUtils.animate(1f, openProgress, 0.25f)

        // 版式：列数 = 分类数 + 配置列，装不下就整体缩小。
        val columns = panels.size + 1
        val totalWidth = columns * SmoothConstants.PANEL_WIDTH + (columns - 1) * SmoothConstants.COLUMN_GAP
        val fit = ((width - 12f) / totalWidth).coerceIn(0.35f, 1f)
        zoom = userScale * fit * (0.88f + 0.12f * openProgress)
        SmoothConstants.uiScale = zoom
        // 各列错峰入场用（列号越大越晚淡入）。
        SmoothConstants.guiOpen = openProgress

        val mouseX = toGuiX(mouseXIn)
        val mouseY = toGuiY(mouseYIn)

        // 背景遮罩：随开场淡入。
        val alphaTop = (0xA0 * openProgress).toInt().coerceIn(0, 255)
        val alphaBottom = (0xB0 * openProgress).toInt().coerceIn(0, 255)
        RenderUtils.drawGradientRect(
            0, 0, width, height,
            (alphaTop shl 24) or 0x101010,
            (alphaBottom shl 24) or 0x101010
        )

        GL11.glPushMatrix()
        GL11.glTranslatef(width / 2f, height / 2f, 0f)
        GL11.glScalef(zoom, zoom, 1f)
        GL11.glTranslatef(-width / 2f, -height / 2f, 0f)

        // 立绘（可选，画在所有列后面）。
        if (Launch.moduleManager.getModule(Gui::class.java)?.picturesValue?.get() == true) {
            var pictureX = 0f
            var pictureY = 0f
            var pictureWidth = 0
            var pictureHeight = 0

            when (randomPicture) {
                "1" -> {
                    pictureX = width - 120f
                    pictureY = height - 170f
                    pictureWidth = 84
                    pictureHeight = 120
                }

                "2" -> {
                    pictureX = width - 120f
                    pictureY = height - 180f
                    pictureWidth = 84
                    pictureHeight = 150
                }

                "3" -> {
                    pictureX = width - 140f
                    pictureY = height - 160f
                    pictureWidth = 110
                    pictureHeight = 115
                }

                "4" -> {
                    pictureX = width - 140f
                    pictureY = height - 160f
                    pictureWidth = 121
                    pictureHeight = 110
                }

                "5" -> {
                    pictureX = width - 120f
                    pictureY = height - 180f
                    pictureWidth = 112
                    pictureHeight = 150
                }

                "6" -> {
                    pictureX = width - 120f
                    pictureY = height - 180f
                    pictureWidth = 112
                    pictureHeight = 150
                }
            }

            if (pictureWidth > 0) {
                RenderUtils.drawImage2(
                    APIConnecter.callImage(randomPicture, "clickgui/pictures"),
                    pictureX,
                    pictureY,
                    pictureWidth,
                    pictureHeight
                )
            }
        }

        // 滚轮：滚鼠标底下那一列（整列范围，不只是列头），不整屏滚。
        if (Mouse.hasWheel()) {
            val wheel = Mouse.getDWheel()
            if (wheel != 0) {
                val amount = if (wheel > 0) -25f else 25f
                for (panel in panels) {
                    if (panel.isInColumn(mouseX, mouseY)) panel.scrollBy(amount)
                }
                if (configPanel.isInColumn(mouseX, mouseY)) configPanel.scrollBy(amount)
            }
        }

        val startX = (width - totalWidth) / 2f
        val bottom = height - SmoothConstants.COLUMN_BOTTOM_MARGIN

        var x = startX
        var column = 0
        for (panel in panels) {
            panel.x = x
            panel.y = SmoothConstants.COLUMN_TOP
            panel.width = SmoothConstants.PANEL_WIDTH
            panel.bottom = bottom
            panel.staggerIndex = column
            panel.drawPanel(mouseX, mouseY)
            x += SmoothConstants.PANEL_WIDTH + SmoothConstants.COLUMN_GAP
            column++
        }

        configPanel.x = x
        configPanel.y = SmoothConstants.COLUMN_TOP
        configPanel.width = SmoothConstants.PANEL_WIDTH
        configPanel.bottom = bottom
        configPanel.staggerIndex = column
        configPanel.drawPanel(mouseX, mouseY)

        GL11.glPopMatrix()
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    private fun mouseAction(mouseXIn: Int, mouseYIn: Int, mouseButton: Int, state: Boolean) {
        val mouseX = toGuiX(mouseXIn)
        val mouseY = toGuiY(mouseYIn)

        for (panel in panels) {
            panel.mouseAction(mouseX, mouseY, state, mouseButton)
            if (!panel.open) continue
            for (moduleButton in panel.moduleButtons) {
                moduleButton.mouseAction(mouseX, mouseY, state, mouseButton)
                if (!moduleButton.open) continue
                for (valueButton in moduleButton.valueButtons) {
                    // 只把可见（展开着、且没被滚出列外）的设置行喂给鼠标。
                    if (!valueButton.show) continue
                    if (!moduleButton.inBand(valueButton.y, valueButton.y + valueButton.height)) continue
                    valueButton.mouseAction(mouseX, mouseY, state, mouseButton)
                }
            }
        }

        configPanel.mouseAction(mouseX, mouseY, state, mouseButton)
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseXIn: Int, mouseYIn: Int, mouseButton: Int) {
        mouseAction(mouseXIn, mouseYIn, mouseButton, true)
    }

    override fun mouseReleased(mouseXIn: Int, mouseYIn: Int, mouseButton: Int) {
        mouseAction(mouseXIn, mouseYIn, mouseButton, false)
    }

    /**
     * 先给配置列上面的输入框（新建配置 / 过滤），再给展开中的模块的绑定键行，
     * 最后才交给 `super`（ESC 关界面）。
     */
    @Throws(IOException::class)
    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (configPanel.keyTyped(typedChar, keyCode)) return
        for (panel in panels) {
            if (!panel.open) continue
            for (moduleButton in panel.moduleButtons)
                if (moduleButton.keyTyped(typedChar, keyCode)) return
        }
        super.keyTyped(typedChar, keyCode)
    }
}
