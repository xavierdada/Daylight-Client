package net.aspw.client.visual.client.clickgui.smooth.buttons

import net.aspw.client.Launch
import net.aspw.client.utils.AnimationUtils
import net.aspw.client.utils.SettingsUtils
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.Glass
import net.aspw.client.utils.render.RenderUtils.drawCircle
import net.aspw.client.utils.render.RenderUtils.drawLine
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.ANIM_SPEED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_CATEGORY_COLOR
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CONFIG_BUTTON
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CONFIG_BUTTON_HOVER
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CONFIG_ICON
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CONFIG_ICON_ON
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.MODULE_HEIGHT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.PANEL_HEIGHT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SCROLL_THUMB
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SCROLL_TRACK
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SEPARATOR
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT_MUTED
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT_ON_ENABLED
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import net.aspw.client.visual.client.clickgui.smooth.makeGuiScissorBox
import net.aspw.client.visual.client.clickgui.smooth.trimToWidth
import net.aspw.client.visual.client.clickgui.smooth.withAlpha
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.Desktop
import java.io.File
import java.util.Locale

/**
 * 版式里最后一列：配置管理。
 *
 * 一列配置文件名（左键载入、当前选中的那行填强调色），上面是标题 "Configurations" 与两个小按钮
 * （"+" 存为新配置、放大镜 过滤），下面是四个按钮：刷新 / 保存 / 删除 / 打开文件夹。
 *
 * 配置的读写沿用 `/config` 命令那一套：[SettingsUtils.generateScript] 生成脚本、
 * `SettingsUtils.executeScript` 载入、文件放在 [Launch.fileManager.settingsDir]。
 * 参考图里第五个按钮是 "Upload"（上传到它自己的云），本项目没有这个接口，所以这里换成了
 * "打开文件夹"，功能都是真的可用。
 */
class ConfigPanel(x: Float, y: Float, width: Float) : Button(x, y, width, PANEL_HEIGHT) {

    /** 列的底边，由 SmoothClickGui 每帧写入。 */
    var bottom = y + PANEL_HEIGHT

    /** 列表滚动量（未缩放 GUI 单位）。 */
    var scroll = 0f

    private var configs = ArrayList<File>()
    private var selected: File? = null
    private var hoverAnim = 0f
    private var contentHeight = 0f

    /** 第几列（由 SmoothClickGui 每帧写入），错峰入场用。 */
    var staggerIndex = 0

    /** 入场进度 0..1，按列号错峰缓动。 */
    private var appear = 0f

    /** 列表上方那条输入框当前在干什么。 */
    private enum class InputMode { NONE, SAVE_AS, FILTER }

    private var inputMode = InputMode.NONE
    private var inputText = ""
    private var filter = ""

    /** 列底部那排按钮的高度（含上下留白）。 */
    private val buttonsBand = 26f

    init {
        refresh()
    }

    /** 重新读一次配置目录。 */
    fun refresh() {
        val files = Launch.fileManager.settingsDir.listFiles()
        configs = ArrayList((files ?: emptyArray<File>()).filter { it.isFile }
            .sortedBy { it.name.lowercase(Locale.ROOT) })
        if (selected != null && !configs.contains(selected)) selected = null
    }

    private fun listFiles(): List<File> =
        if (filter.isEmpty()) configs else configs.filter { it.name.lowercase(Locale.ROOT).contains(filter.lowercase(Locale.ROOT)) }

    private fun listBand(): Float = (bottom - buttonsBand - (y + height)).coerceAtLeast(0f)

    private fun inputHeight(): Float = if (inputMode == InputMode.NONE) 0f else MODULE_HEIGHT + 3f

    private fun maxScroll(): Float =
        (contentHeight + inputHeight() - listBand()).coerceAtLeast(0f)

    fun scrollBy(amount: Float) {
        scroll = (scroll + amount).coerceIn(0f, maxScroll())
    }

    /**
     * 鼠标是不是在整列范围内（不只是列头那一条）。滚轮用它，否则只有把鼠标放到标题上才滚得动，
     * 列表里的内容就再也够不到了。
     */
    fun isInColumn(mouseX: Int, mouseY: Int): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= bottom

    // ------------------------------------------------------------------ 操作

    private fun load(file: File) {
        selected = file
        try {
            SettingsUtils.executeScript(file.readText())
        } catch (_: Throwable) {
        }
    }

    private fun saveAs(name: String) {
        val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        if (clean.isEmpty()) return
        try {
            File(Launch.fileManager.settingsDir, clean)
                .writeText(SettingsUtils.generateScript(true, true, true))
            refresh()
            selected = configs.firstOrNull { it.name.equals(clean, true) }
        } catch (_: Throwable) {
        }
    }

    private fun saveSelected() {
        val file = selected
        if (file == null) {
            inputMode = InputMode.SAVE_AS
            inputText = ""
            return
        }
        try {
            file.writeText(SettingsUtils.generateScript(true, true, true))
        } catch (_: Throwable) {
        }
    }

    private fun deleteSelected() {
        val file = selected ?: return
        try {
            file.delete()
        } catch (_: Throwable) {
        }
        refresh()
    }

    private fun openFolder() {
        try {
            Desktop.getDesktop().open(Launch.fileManager.settingsDir)
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ 绘制

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        hoverAnim = AnimationUtils.animate(if (isHovered(mouseX, mouseY)) 1f else 0f, hoverAnim, ANIM_SPEED)

        // 错峰入场（同分类列）。
        val appearTarget = ((SmoothConstants.guiOpen - staggerIndex * 0.07f) / 0.45f).coerceIn(0f, 1f)
        appear = AnimationUtils.animate(appearTarget, appear, 0.22f)
        y += (1f - appear) * 10f

        // 液态玻璃（同分类列）。
        Glass.panel(
            x, y, x + width, bottom, RADIUS,
            net.aspw.client.visual.client.clickgui.smooth.mix(
                BACKGROUND_CATEGORY_COLOR,
                net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.HOVER,
                hoverAnim * 0.35f
            ).rgb, (186 * appear).toInt(),
            hoverAnim, x.toInt() * 31 + y.toInt(), appear
        )
        FONT.drawHeightCenteredString("Configurations", x + 6f, y + height / 2f, TEXT)
        drawRect(x + 5f, y + height - 0.5f, x + width - 5f, y + height, SEPARATOR)

        val plusX = x + width - 22f
        val searchX = x + width - 11f
        drawPlus(plusX, y + height / 2f, CONFIG_ICON)
        drawMagnifier(searchX, y + height / 2f, CONFIG_ICON)

        val bandTop = y + height
        val bandBottom = bottom - buttonsBand

        // 输入框（新建配置 / 过滤）占列表最上面一行。
        if (inputMode != InputMode.NONE) {
            val boxX1 = x + 3f
            val boxX2 = x + width - 3f
            val boxY1 = bandTop + 2f
            val boxY2 = boxY1 + MODULE_HEIGHT - 2f
            drawRoundedRect(boxX1, boxY1, boxX2, boxY2, RADIUS, Color(246, 246, 250).rgb)
            val shown = FONT.trimToWidth(inputText, width - 16f)
            FONT.drawHeightCenteredString(shown, boxX1 + 4f, (boxY1 + boxY2) / 2f, TEXT)
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                val caretX = boxX1 + 5f + net.aspw.client.visual.font.UiFont.width(FONT, shown)
                drawRect(caretX, boxY1 + 3f, caretX + 0.6f, boxY2 - 3f, TEXT)
            }
        }

        val files = listFiles()
        val contentTop = bandTop + inputHeight()
        val visible = (bandBottom - contentTop).coerceAtLeast(0f)
        contentHeight = files.size * MODULE_HEIGHT

        scroll = scroll.coerceIn(0f, maxScroll())

        makeGuiScissorBox(x, contentTop, x + width, bandBottom)
        GL11.glEnable(3089)

        var used = 0f
        val startY = contentTop - scroll
        for (file in files) {
            val rowY = startY + used
            used += MODULE_HEIGHT
            if (rowY + MODULE_HEIGHT < contentTop || rowY > bandBottom) continue

            val isSelected = file == selected
            val hovered = mouseX >= x && mouseX <= x + width && mouseY >= maxOf(rowY, contentTop) && mouseY <= minOf(rowY + MODULE_HEIGHT, bandBottom)
            if (isSelected) {
                drawRect(x, rowY, x + width, rowY + MODULE_HEIGHT, Color(242, 167, 157).rgb)
            } else if (hovered) {
                drawRect(x, rowY, x + width, rowY + MODULE_HEIGHT, withAlpha(CONFIG_BUTTON_HOVER, 150))
            }
            FONT.drawHeightCenteredString(
                FONT.trimToWidth(file.name, width - 12f),
                x + 6f,
                rowY + MODULE_HEIGHT / 2f,
                if (isSelected) TEXT_ON_ENABLED else TEXT
            )
        }

        if (files.isEmpty()) {
            FONT.drawHeightCenteredString(
                if (filter.isEmpty()) "No configs" else "No results",
                x + 6f,
                contentTop + MODULE_HEIGHT / 2f,
                TEXT_MUTED
            )
        }

        GL11.glDisable(3089)

        // 滚动条
        val max = maxScroll()
        if (max > 0.5f) {
            val trackX1 = x + width - 4.5f
            val trackX2 = x + width - 2.5f
            drawRect(trackX1, contentTop, trackX2, bandBottom, SCROLL_TRACK)
            val thumbHeight = (visible * visible / (contentHeight + inputHeight())).coerceIn(16f, visible)
            val thumbY = contentTop + (visible - thumbHeight) * (scroll / max)
            drawRect(trackX1, thumbY, trackX2, thumbY + thumbHeight, SCROLL_THUMB)
        }

        // 底部四个按钮：刷新 / 保存 / 删除 / 打开文件夹。
        drawRect(x + 5f, bandBottom + 2f, x + width - 5f, bandBottom + 2.5f, SEPARATOR)
        val buttonTop = bandBottom + 6f
        val buttonBottom = bottom - 6f
        val gap = 3f
        val buttonWidth = (width - 12f - gap * 3f) / 4f
        for (index in 0 until 4) {
            val bx1 = x + 6f + index * (buttonWidth + gap)
            val bx2 = bx1 + buttonWidth
            val hovered = mouseX >= bx1 && mouseX <= bx2 && mouseY >= buttonTop && mouseY <= buttonBottom
            val background = if (hovered) CONFIG_BUTTON_HOVER else CONFIG_BUTTON
            drawRoundedRect(bx1, buttonTop, bx2, buttonBottom, RADIUS, background)
            val cx = (bx1 + bx2) / 2f
            val cy = (buttonTop + buttonBottom) / 2f
            when (index) {
                0 -> drawRefresh(cx, cy, CONFIG_ICON)
                1 -> drawSave(cx, cy, CONFIG_ICON, background)
                2 -> drawTrash(cx, cy, CONFIG_ICON, background)
                else -> drawFolder(cx, cy, CONFIG_ICON, background)
            }
        }

        return Rectangle()
    }

    // ------------------------------------------------------------------ 小图标（全用矩形/线段/圆拼，不依赖图标字体的码位）

    private fun drawPlus(cx: Float, cy: Float, color: Int) {
        drawRect(cx - 3.5f, cy - 0.5f, cx + 3.5f, cy + 0.5f, color)
        drawRect(cx - 0.5f, cy - 3.5f, cx + 0.5f, cy + 3.5f, color)
    }

    private fun drawMagnifier(cx: Float, cy: Float, color: Int) {
        drawCircle(cx - 1f, cy - 1f, 3.4f, 1f, 0, 320, Color(color, true))
        // drawLine 不带颜色参数，用当前色，所以先把颜色设上（GlStateManager.color）。
        net.aspw.client.utils.render.RenderUtils.color(color)
        drawLine(cx + 1.4f, cy + 2.4f, cx + 4f, cy + 5f, 1.2f)
    }

    private fun drawRefresh(cx: Float, cy: Float, color: Int) {
        drawCircle(cx, cy, 3.6f, 1.2f, 40, 330, Color(color, true))
        drawRect(cx + 1.6f, cy - 4.6f, cx + 4.2f, cy - 3.4f, color)
        drawRect(cx + 3.0f, cy - 3.4f, cx + 4.2f, cy - 1.4f, color)
    }

    private fun drawSave(cx: Float, cy: Float, color: Int, background: Int) {
        drawRoundedRect(cx - 4f, cy - 4f, cx + 4f, cy + 4f, 2f, color)
        drawRect(cx - 2f, cy - 4f, cx + 2f, cy - 1f, background)
        drawRect(cx - 2.5f, cy + 1f, cx + 2.5f, cy + 4f, background)
    }

    private fun drawTrash(cx: Float, cy: Float, color: Int, background: Int) {
        drawRect(cx - 4.5f, cy - 3.5f, cx + 4.5f, cy - 2.3f, color)
        drawRect(cx - 1.2f, cy - 5f, cx + 1.2f, cy - 3.5f, color)
        drawRect(cx - 3.4f, cy - 2.3f, cx + 3.4f, cy + 4.2f, color)
        drawRect(cx - 1.6f, cy - 1f, cx - 0.8f, cy + 2.8f, background)
        drawRect(cx + 0.8f, cy - 1f, cx + 1.6f, cy + 2.8f, background)
    }

    private fun drawFolder(cx: Float, cy: Float, color: Int, background: Int) {
        drawRect(cx - 4.5f, cy - 3.5f, cx - 0.5f, cy - 2f, color)
        drawRect(cx - 4.5f, cy - 2.5f, cx + 4.5f, cy + 3.5f, color)
        drawRect(cx - 3.5f, cy - 1.2f, cx + 3.5f, cy + 2.4f, background)
    }

    // ------------------------------------------------------------------ 输入

    /**
     * 键盘输入：正在输入配置名 / 过滤串时把按键吃掉（返回 true），外面就不会再交给模块的绑定键行，
     * 也不会让 ESC 关掉整个界面。
     */
    fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (inputMode == InputMode.NONE) return false
        when (keyCode) {
            Keyboard.KEY_ESCAPE -> {
                inputMode = InputMode.NONE
                inputText = ""
                filter = ""
            }

            Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> {
                if (inputMode == InputMode.SAVE_AS) saveAs(inputText)
                inputMode = InputMode.NONE
                inputText = ""
            }

            Keyboard.KEY_BACK -> {
                if (inputText.isNotEmpty()) inputText = inputText.substring(0, inputText.length - 1)
                if (inputMode == InputMode.FILTER) {
                    filter = inputText
                    scroll = 0f
                }
            }

            else -> {
                if (typedChar.isLetterOrDigit() || typedChar == '.' || typedChar == '-' || typedChar == '_' || typedChar == ' ') {
                    if (inputText.length < 32) inputText += typedChar
                    if (inputMode == InputMode.FILTER) {
                        filter = inputText
                        scroll = 0f
                    }
                }
            }
        }
        return true
    }

    // ------------------------------------------------------------------ 鼠标

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!click) return
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > bottom) return

        // 标题栏：+ 新建、放大镜 过滤
        if (mouseY <= y + height) {
            if (mouseX >= x + width - 28f && mouseX <= x + width - 16f) {
                inputMode = InputMode.SAVE_AS
                inputText = ""
            } else if (mouseX >= x + width - 16f) {
                inputMode = InputMode.FILTER
                inputText = filter
            }
            return
        }

        val bandBottom = bottom - buttonsBand
        if (mouseY >= bandBottom + 6f && mouseY <= bottom - 6f) {
            val gap = 3f
            val buttonWidth = (width - 12f - gap * 3f) / 4f
            for (index in 0 until 4) {
                val bx1 = x + 6f + index * (buttonWidth + gap)
                if (mouseX < bx1 || mouseX > bx1 + buttonWidth) continue
                when (index) {
                    0 -> refresh()
                    1 -> saveSelected()
                    2 -> deleteSelected()
                    else -> openFolder()
                }
                return
            }
            return
        }

        val contentTop = y + height + inputHeight()
        if (mouseY < contentTop || mouseY > bandBottom) return
        val index = ((mouseY - contentTop + scroll) / MODULE_HEIGHT).toInt()
        val files = listFiles()
        if (index < 0 || index >= files.size) return
        load(files[index])
    }
}
