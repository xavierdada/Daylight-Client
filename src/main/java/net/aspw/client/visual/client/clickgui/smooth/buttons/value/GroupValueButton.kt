package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import net.aspw.client.visual.font.UiFont
import net.aspw.client.visual.font.semi.Fonts

/**
 * Collapsible row that folds every setting sharing a `Group-Name` prefix into one header.
 *
 * Collapsed it is a single row showing the group name and a `+`/`-` mark. Expanded it draws the rows
 * of its group underneath itself exactly as they would be drawn on their own, and reports the header
 * plus all of those rows as its height, so the rows below are pushed down by the full amount.
 *
 * Like [ColorValueButton] and [KeyBindButton] it owns no [net.aspw.client.value.Value] of its own and
 * passes `null` to [BaseValueButton]; the filters of the grouped settings travel with the children.
 */
class GroupValueButton(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    private val groupName: String,
    private val children: List<Child>
) : BaseValueButton(x, y, width, height, null) {

    /**
     * One row inside the group: the button that draws and handles it, plus the display filter of the
     * value it was built from. The filter is carried here because rows such as the colour picker do
     * not hand their own value to [BaseValueButton], so their `canDisplay` cannot be asked directly.
     */
    class Child(val button: BaseValueButton, val visible: () -> Boolean)

    var expanded = false

    /** The group only shows while at least one of its rows passes its own display filter. */
    override fun canDisplay(): Boolean = children.any { it.visible() }

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        // Header card, drawn with the same insets as the rows it stands in for, so it lines up.
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)

        FONT.drawHeightCenteredString(groupName, x + hOffset, y + 1 + height / 2, TEXT)
        UiFont.draw(Fonts.font72, if (expanded) "§6-" else "§8+", x + width - 12, y - 11 + height / 2, TEXT)

        // Height reported to the module row: the header card, plus exactly the heights the grouped
        // rows return, so the layout below never drifts from what is actually drawn.
        var used = background.height
        if (expanded) {
            for (child in children) {
                if (!child.visible()) {
                    child.button.show = false
                    continue
                }

                child.button.show = true
                child.button.x = x
                child.button.y = y + used
                used += child.button.drawPanel(mouseX, mouseY).height
            }
        }

        return Rectangle(x + 1, y, width - 2, used)
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show) return

        // A click on the header row only folds the group; the rows underneath keep their own
        // behaviour (sliders, the colour picker and even the key-bind row included).
        if (click && isHovered(mouseX, mouseY)) {
            if (button == MouseButtons.LEFT.ordinal || button == MouseButtons.RIGHT.ordinal)
                expanded = !expanded
            return
        }
        if (!expanded) return

        for (child in children) if (child.button.show)
            child.button.mouseAction(mouseX, mouseY, click, button)
    }

    /** Key presses reach the rows of an open group, so a key bind inside it can be captured. */
    override fun keyTyped(typedChar: Char, keyCode: Int): Boolean {
        if (!expanded) return false
        for (child in children)
            if (child.button.show && child.button.keyTyped(typedChar, keyCode)) return true
        return false
    }
}
