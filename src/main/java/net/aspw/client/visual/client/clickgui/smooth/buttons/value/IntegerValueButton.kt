package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.value.IntegerValue
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.SLIDER_OFFSET
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.dim
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import java.awt.Color

class IntegerValueButton(x: Float, y: Float, width: Float, height: Float, var setting: IntegerValue, var color: Color) :
    BaseValueButton(x, y, width, height, setting) {
    private var dragged = false
    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)
        val diff = (setting.maximum - setting.minimum).toDouble()
        val percentWidth = (setting.get() - setting.minimum.toFloat()) / (setting.maximum - setting.minimum)

        // Inset so the slider stays inside the rounded card.
        val foreground = Rectangle(x + 3, y + 1, width - 3 * 2, height - 3)
        foreground.width *= percentWidth

        val foreground2 = Rectangle(foreground)
        foreground2.width = (foreground2.width + SLIDER_OFFSET * (width - 3 * 2)).coerceAtMost(width - 3 * 2)

        if (setting.get() > setting.minimum) drawRect(foreground2, dim(color).rgb)
        drawRect(foreground, color.rgb)

        if (dragged) {
            val innerWidth = width - 3 * 2
            val position = (mouseX - x - 3).coerceAtMost(innerWidth).coerceAtLeast(0f)
            val value = setting.minimum + position / innerWidth * diff
            setting.set(value.toInt())
        }

        val format = setting.get()
        FONT.drawHeightCenteredString(
            setting.name + ": §4" + format + setting.suffix,
            x + hOffset,
            y + 1 + height / 2,
            TEXT
        )

        return background
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show) return
        if (isHovered(mouseX, mouseY) && button == MouseButtons.LEFT.ordinal) {
            dragged = true
        }
        if (!click) dragged = false
    }
}
