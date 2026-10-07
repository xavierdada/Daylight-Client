package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.value.TextValue
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import java.awt.Color

class TextValueButton(x: Float, y: Float, width: Float, height: Float, var setting: TextValue, var color: Color) :
    BaseValueButton(x, y, width, height, setting) {
    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)
        FONT.drawHeightCenteredString(setting.name + ": " + setting.get(), x + hOffset, y + 1 + height / 2, TEXT)
        return background
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
    }
}
