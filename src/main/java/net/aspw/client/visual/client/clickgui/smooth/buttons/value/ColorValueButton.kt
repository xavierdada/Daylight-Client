package net.aspw.client.visual.client.clickgui.smooth.buttons.value

import net.aspw.client.utils.MouseButtons
import net.aspw.client.utils.geom.Rectangle
import net.aspw.client.utils.render.RenderUtils.drawRect
import net.aspw.client.utils.render.RenderUtils.drawRoundedRect
import net.aspw.client.value.ColorValue
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.BACKGROUND_VALUE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CHECKER_DARK
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.CHECKER_LIGHT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.COLOR_PICKER_BACKGROUND
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.FONT
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.MARKER
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.MARKER_OUTLINE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.OUTLINE
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.RADIUS
import net.aspw.client.visual.client.clickgui.smooth.SmoothConstants.TEXT
import net.aspw.client.visual.client.clickgui.smooth.drawHeightCenteredString
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Colour row: the setting name plus a rounded swatch of the current colour while collapsed, and an
 * HSV picker (hue bar, saturation/brightness box, alpha bar) that left click expands underneath.
 *
 * It has no [Value] of its own to hand to [BaseValueButton] (the picker is driven through
 * [setting], which is written with plain ARGB ints), so `null` is passed exactly like the key-bind
 * row does.
 */
class ColorValueButton(x: Float, y: Float, width: Float, height: Float, var setting: ColorValue) :
    BaseValueButton(x, y, width, height, null) {

    /** Control the button is currently dragging on. */
    private enum class DragTarget { NONE, HUE, SATURATION_BRIGHTNESS, ALPHA }

    private var expanded = false
    private var dragTarget = DragTarget.NONE

    // Cached HSV state of the current colour. It is only re-read from the value when the value
    // changed from the outside, so dragging through pure black/white cannot lose the hue.
    private var hue = 0f
    private var saturation = 1f
    private var brightness = 1f
    private var alpha = 1f

    /** Last ARGB this row wrote into or read from [setting]; used to detect outside changes. */
    private var lastArgb = Int.MIN_VALUE

    // Bounds of the picker controls as drawn last frame, so mouseAction knows what was clicked.
    private var hueBar = Rectangle()
    private var svBox = Rectangle()
    private var alphaBar = Rectangle()

    private fun syncFromValue() {
        val argb = setting.get()
        if (argb == lastArgb) return
        lastArgb = argb

        alpha = ((argb ushr 24) and 0xFF) / 255f

        val hsb = FloatArray(3)
        Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsb)
        saturation = hsb[1]
        brightness = hsb[2]
        // Grey, black and fully transparent colours carry no hue of their own, so the cached hue is
        // kept for them: picking a hue on a black colour would otherwise appear to do nothing.
        if (hsb[1] > 0.0001f && hsb[2] > 0.0001f) hue = hsb[0]
    }

    private fun pushColor() {
        val alphaByte = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val argb = (Color.HSBtoRGB(hue, saturation, brightness) and 0x00FFFFFF) or (alphaByte shl 24)
        // Remember what this row produced, so syncFromValue does not overwrite the cached HSV with
        // the (lossy) round trip of the same colour on the next frame.
        lastArgb = argb
        setting.set(argb)
    }

    private fun applyDrag(mouseX: Int, mouseY: Int) {
        when (dragTarget) {
            DragTarget.HUE -> hue = ((mouseX - hueBar.x) / hueBar.width).coerceIn(0f, 1f)
            DragTarget.SATURATION_BRIGHTNESS -> {
                saturation = ((mouseX - svBox.x) / svBox.width).coerceIn(0f, 1f)
                brightness = (1f - (mouseY - svBox.y) / svBox.height).coerceIn(0f, 1f)
            }

            DragTarget.ALPHA -> alpha = ((mouseX - alphaBar.x) / alphaBar.width).coerceIn(0f, 1f)
            DragTarget.NONE -> return
        }
        pushColor()
    }

    override fun drawPanel(mouseX: Int, mouseY: Int): Rectangle {
        syncFromValue()

        // The swatch is what the row shows while collapsed, on top of the usual row card.
        val background = Rectangle(x + 1, y, width - 2, height - 1)
        drawRoundedRect(background.x, background.y, background.x2, background.y2, RADIUS, BACKGROUND_VALUE)
        FONT.drawHeightCenteredString(setting.name, x + hOffset, y + 1 + height / 2, TEXT)

        val argb = setting.get()
        val swatchX = x + width - hOffset - SWATCH_WIDTH
        val swatchY = y + (height - SWATCH_HEIGHT) / 2f
        drawRoundedRect(
            swatchX - 1, swatchY - 1, swatchX + SWATCH_WIDTH + 1, swatchY + SWATCH_HEIGHT + 1,
            SWATCH_RADIUS + 1, OUTLINE
        )
        // Neutral squares under the colour, inset by the corner radius so they stay inside the
        // rounded shape: a translucent colour then previews over the checkerboard instead of
        // looking as if the light row card were part of it.
        val swatchInnerX = swatchX + SWATCH_RADIUS
        val swatchInnerY = swatchY + SWATCH_RADIUS
        val swatchInnerWidth = SWATCH_WIDTH - SWATCH_RADIUS * 2
        val swatchInnerHeight = SWATCH_HEIGHT - SWATCH_RADIUS * 2
        drawRect(
            swatchInnerX, swatchInnerY, swatchInnerX + swatchInnerWidth,
            swatchInnerY + swatchInnerHeight, CHECKER_LIGHT
        )
        var cellX = 0f
        var column = 0
        while (cellX < swatchInnerWidth) {
            val right = (cellX + SWATCH_CHECKER_SIZE).coerceAtMost(swatchInnerWidth)
            var cellY = 0f
            var row = 0
            while (cellY < swatchInnerHeight) {
                val bottom = (cellY + SWATCH_CHECKER_SIZE).coerceAtMost(swatchInnerHeight)
                if ((column + row) % 2 == 1)
                    drawRect(
                        swatchInnerX + cellX, swatchInnerY + cellY,
                        swatchInnerX + right, swatchInnerY + bottom,
                        CHECKER_DARK
                    )
                cellY += SWATCH_CHECKER_SIZE
                row++
            }
            cellX += SWATCH_CHECKER_SIZE
            column++
        }

        drawRoundedRect(
            swatchX, swatchY, swatchX + SWATCH_WIDTH, swatchY + SWATCH_HEIGHT,
            SWATCH_RADIUS, argb
        )

        if (!expanded) {
            dragTarget = DragTarget.NONE
            return background
        }

        val panelTop = y + height
        drawRoundedRect(
            x + 1, panelTop, x + width - 1, panelTop + PICKER_HEIGHT,
            RADIUS, COLOR_PICKER_BACKGROUND
        )

        val innerX = x + 1 + PAD
        val innerWidth = width - 2 - PAD * 2

        hueBar = Rectangle(innerX, panelTop + PAD, innerWidth, HUE_HEIGHT)
        drawHueBar()
        drawVerticalMarker(hueBar, hue)

        svBox = Rectangle(innerX, hueBar.y2 + GAP, innerWidth, SV_HEIGHT)
        drawSvBox()
        drawSvMarker()

        alphaBar = Rectangle(innerX, svBox.y2 + GAP, innerWidth, ALPHA_HEIGHT)
        drawAlphaBar()
        drawVerticalMarker(alphaBar, alpha)

        // Keep following the mouse while the button is held down: the GUI only reports clicks, so
        // the actual dragging has to happen here, once per frame, like FloatValueButton does.
        if (dragTarget != DragTarget.NONE) applyDrag(mouseX, mouseY)

        // The returned height pushes every row below this one down by the height of the picker.
        return Rectangle(x + 1, y, width - 2, height + PICKER_HEIGHT)
    }

    private fun drawHueBar() {
        val stepWidth = hueBar.width / HUE_STEPS
        for (i in 0 until HUE_STEPS) {
            val left = hueBar.x + i * stepWidth
            val right = if (i == HUE_STEPS - 1) hueBar.x2 else left + stepWidth
            drawRect(left, hueBar.y, right, hueBar.y2, Color.HSBtoRGB(i / (HUE_STEPS - 1f), 1f, 1f))
        }
    }

    private fun drawSvBox() {
        val columnWidth = svBox.width / SV_COLUMNS
        for (i in 0 until SV_COLUMNS) {
            val left = svBox.x + i * columnWidth
            val right = if (i == SV_COLUMNS - 1) svBox.x2 else left + columnWidth
            drawRect(left, svBox.y, right, svBox.y2, Color.HSBtoRGB(hue, i / (SV_COLUMNS - 1f), 1f))
        }

        // Brightness is the saturated colour scaled towards black, which is exactly what a black
        // overlay of rising alpha composites to.
        val rowHeight = svBox.height / SV_ROWS
        for (j in 1 until SV_ROWS) {
            val top = svBox.y + j * rowHeight
            val bottom = if (j == SV_ROWS - 1) svBox.y2 else top + rowHeight
            val shade = (j * 255f / (SV_ROWS - 1f)).roundToInt().coerceIn(0, 255)
            drawRect(svBox.x, top, svBox.x2, bottom, shade shl 24)
        }
    }

    private fun drawSvMarker() {
        val markerX = svBox.x + saturation.coerceIn(0f, 1f) * svBox.width
        val markerY = svBox.y2 - brightness.coerceIn(0f, 1f) * svBox.height
        // A rounded rect with radius = half its size is a circle.
        drawRoundedRect(
            markerX - 3.5f, markerY - 3.5f, markerX + 3.5f, markerY + 3.5f, 3.5f, MARKER_OUTLINE
        )
        drawRoundedRect(
            markerX - 2.5f, markerY - 2.5f, markerX + 2.5f, markerY + 2.5f, 2.5f, MARKER
        )
    }

    private fun drawVerticalMarker(bar: Rectangle, position: Float) {
        val markerX = bar.x + position.coerceIn(0f, 1f) * bar.width
        drawRect(markerX - 2f, bar.y - 1, markerX + 2f, bar.y2 + 1, MARKER_OUTLINE)
        drawRect(markerX - 1f, bar.y, markerX + 1f, bar.y2, MARKER)
    }

    private fun drawAlphaBar() {
        // Checkerboard first, so the gradient drawn over it shows what alpha really looks like.
        var cx = 0f
        var column = 0
        while (cx < alphaBar.width) {
            val right = (cx + CHECKER_SIZE).coerceAtMost(alphaBar.width)
            var cy = 0f
            var row = 0
            while (cy < alphaBar.height) {
                val bottom = (cy + CHECKER_SIZE).coerceAtMost(alphaBar.height)
                val light = (column + row) % 2 == 0
                drawRect(
                    alphaBar.x + cx, alphaBar.y + cy, alphaBar.x + right, alphaBar.y + bottom,
                    if (light) CHECKER_LIGHT else CHECKER_DARK
                )
                cy += CHECKER_SIZE
                row++
            }
            cx += CHECKER_SIZE
            column++
        }

        val rgb = Color.HSBtoRGB(hue, saturation, brightness) and 0x00FFFFFF
        val stepWidth = alphaBar.width / ALPHA_STEPS
        for (i in 0 until ALPHA_STEPS) {
            val alphaByte = (i * 255f / (ALPHA_STEPS - 1f)).roundToInt().coerceIn(0, 255)
            if (alphaByte <= 0) continue
            val left = alphaBar.x + i * stepWidth
            val right = if (i == ALPHA_STEPS - 1) alphaBar.x2 else left + stepWidth
            drawRect(left, alphaBar.y, right, alphaBar.y2, (alphaByte shl 24) or rgb)
        }
    }

    override fun mouseAction(mouseX: Int, mouseY: Int, click: Boolean, button: Int) {
        if (!show) return

        if (!click) {
            dragTarget = DragTarget.NONE
            return
        }
        if (button != MouseButtons.LEFT.ordinal) return

        // Left click on the row itself toggles the picker; clicks inside the picker pick a colour.
        if (isHovered(mouseX, mouseY)) {
            expanded = !expanded
            dragTarget = DragTarget.NONE
            return
        }
        if (!expanded) return

        when {
            hueBar.contains(mouseX, mouseY) -> dragTarget = DragTarget.HUE
            svBox.contains(mouseX, mouseY) -> dragTarget = DragTarget.SATURATION_BRIGHTNESS
            alphaBar.contains(mouseX, mouseY) -> dragTarget = DragTarget.ALPHA
            // Anywhere else (outside the panel included) must not change the colour.
            else -> return
        }
        // React on the click itself instead of waiting for the next frame.
        applyDrag(mouseX, mouseY)
    }

    private companion object {
        /** Padding between the picker panel edge and its controls. */
        const val PAD = 4f

        /** Gap between two picker controls. */
        const val GAP = 4f

        const val HUE_HEIGHT = 9f
        const val SV_HEIGHT = 50f
        const val ALPHA_HEIGHT = 9f

        /** Height of the expanded picker: padding, all three controls and the gaps between them. */
        const val PICKER_HEIGHT = PAD * 2 + HUE_HEIGHT + GAP + SV_HEIGHT + GAP + ALPHA_HEIGHT

        const val SWATCH_WIDTH = 20f
        const val SWATCH_HEIGHT = 8f
        const val SWATCH_RADIUS = 2f
        const val SWATCH_CHECKER_SIZE = 4f

        const val HUE_STEPS = 60
        const val SV_COLUMNS = 24
        const val SV_ROWS = 16
        const val ALPHA_STEPS = 24
        const val CHECKER_SIZE = 4.5f
    }
}
