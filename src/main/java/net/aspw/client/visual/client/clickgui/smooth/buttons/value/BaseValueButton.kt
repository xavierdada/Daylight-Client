package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.value.Value
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.buttons.Button
import net.aspw.client.visual.client.clickgui.smooth.getHeight

abstract class BaseValueButton(x: Float, y: Float, width: Float, height: Float, val value: Value<*>?) :
    Button(x, y, width, height) {
    val baseRect: Rectangle
        get() = Rectangle(x, y, width, height)
    val hOffset: Float
        get() = (height - getHeight(FONT)) / 2 + 4
    var show = true

    /**
     * Rows without a backing value, such as the key-bind row, are always displayed. Rows that stand
     * for several settings at once, such as the group header, override this with their own rule.
     */
    open fun canDisplay() = value?.canDisplay?.invoke() ?: true

    /** Rows that consume raw key presses, such as the key-bind row, override this. */
    open fun keyTyped(typedChar: Char, keyCode: Int): Boolean = false
}
