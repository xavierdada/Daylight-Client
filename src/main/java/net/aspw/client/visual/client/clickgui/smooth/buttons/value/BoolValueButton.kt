package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.value.BoolValue
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT_ACCENT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT_MUTED
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import java.awt.Color

class BoolValueButton(x: Float, y: Float, width: Float, height: Float, var setting: BoolValue, var color: Color) :
    BaseValueButton(x, y, width, height, setting) {
    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)
        if (setting.get())
            FONT.drawHeightCenteredString(setting.name, x + hOffset, y + 1 + height / 2, TEXT_ACCENT)
        else FONT.drawHeightCenteredString(setting.name, x + hOffset, y + 1 + height / 2, TEXT_MUTED)
        return background
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show) return
        if (isHovered(mouseX, mouseY) && click && button == MouseButtons.LEFT.ordinal) {
            setting.set(!setting.get())
        }
    }
}
