package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.features.module.Module
import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import org.lwjgl.input.Keyboard

/**
 * Key-bind row, appended to the settings of every module.
 *
 * Left click starts listening and the next key becomes the bind, escape clears it.
 * Right click clears the bind immediately.
 */
class KeyBindButton(x: Float, y: Float, width: Float, height: Float, private val module: Module) :
    BaseValueButton(x, y, width, height, null) {

    private var listening = false

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)

        val bound = module.keyBind
        val label = when {
            listening -> "Bind: §e..."
            bound == 0 -> "Bind: §8None"
            else -> "Bind: §b" + Keyboard.getKeyName(bound)
        }
        FONT.drawHeightCenteredString(label, x + hOffset, y + 1 + height / 2, TEXT)

        return background
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show || !click || !isHovered(mouseX, mouseY)) return
        when (button) {
            MouseButtons.LEFT.ordinal -> listening = !listening
            MouseButtons.RIGHT.ordinal -> {
                listening = false
                module.keyBind = 0
            }
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!listening) return false
        module.keyBind = if (keyCode == Keyboard.KEY_ESCAPE) 0 else keyCode
        listening = false
        return true
    }
}
