package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.value.ListValue
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import net.aspw.client.visual.font.UiFont
import java.awt.Color

class ListValueButton(x: Float, y: Float, width: Float, height: Float, var setting: ListValue, var color: Color) :
    BaseValueButton(x, y, width, height, setting) {
    private val listEntryBoxPairs = mutableListOf<Pair<Rectangle, String>>()

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)

        val format = setting.get()
        FONT.drawHeightCenteredString(setting.name + ": §3" + format, x + hOffset, y + 1 + height / 2, TEXT)

        var count = 0
        listEntryBoxPairs.clear()

        if (setting.openList) {
            for (valueOfList in setting.values) {
                val rect = Rectangle(x, y + (count + 1) * height, width, height)
                listEntryBoxPairs.add(rect to valueOfList)

                val listEntryText = (if (valueOfList == setting.get()) "" else "") + valueOfList
                drawRect(rect, BACKGROUND_VALUE)
                FONT.drawHeightCenteredString(
                    listEntryText,
                    rect.x + width - UiFont.width(FONT, listEntryText) - hOffset,
                    rect.y + 1 + height / 2,
                    if (setting.get() == valueOfList) Color(28, 28, 34).rgb else Color(138, 140, 152).rgb
                )
                count++
            }
        }

        background.height += count * height

        return background
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show) return
        if (click) {
            when (button) {
                MouseButtons.LEFT.ordinal -> {
                    if (baseRect.contains(mouseX, mouseY))
                        setting.nextValue()
                    else {
                        for (pair in listEntryBoxPairs) {
                            if (pair.first.contains(mouseX, mouseY)) setting.set(pair.second)
                        }
                    }
                }

                MouseButtons.RIGHT.ordinal -> {
                    if (baseRect.contains(mouseX, mouseY)) setting.openList = !setting.openList
                }
            }
        }
    }
}
