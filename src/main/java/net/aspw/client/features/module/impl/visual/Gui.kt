package net.aspw.client.features.module.impl.visual

import net.aspw.client.Launch
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.render.ColorUtils.LiquidSlowly
import net.aspw.client.utils.render.ColorUtils.fade
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.ColorValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.aspw.client.visual.client.clickgui.dropdown.style.styles.DropDown
import net.aspw.client.visual.client.clickgui.smooth.SmoothClickGui
import net.aspw.client.visual.client.clickgui.tab.NewUi
import org.lwjgl.input.Keyboard
import java.awt.Color
import java.util.*

@ModuleInfo(
    name = "Gui",
    category = ModuleCategory.VISUAL,
    keyBind = Keyboard.KEY_RSHIFT,
    forceNoSound = true,
    onlyEnable = true,
    array = false
)
class Gui : Module() {
    private val styleValue: ListValue = object : ListValue(
        "Style",
        arrayOf("Smooth", "DropDown", "Tab"),
        "Smooth"
    ) {
    }

    @JvmField
    val picturesValue = BoolValue("Pictures", false) { styleValue.get() == "Smooth" }

    /**
     * 用材质包（原版）字体绘制客户端界面，而不是客户端自带的 TTF。
     *
     * 打开后主菜单 / Smooth ClickGUI / HUD 的文字会改走原版 FontRenderer，也就是会被
     * 材质包的 `assets/minecraft/font/default.png`（和 `glyph_*.png`）替换。默认 false，
     * 保持自带 TTF 的原有观感。
     *
     * 注意：这是"换字体"，不是"换字形图"。自带 TTF 的字宽与原版字体不同，所以右对齐 /
     * 居中 / 按钮上的文字会有位移，这是换字体的固有代价，不做版式大改。
     */
    val packFontValue = BoolValue("PackFont", false)

    val uiAnimationsValue = BoolValue("UIAnimations", true)
    val uiAnimationSpeedValue = FloatValue("UIAnimationSpeed", 1f, 0.25f, 3f) { uiAnimationsValue.get() }

    @JvmField
    val animationValue = ListValue("Animation", arrayOf("None", "Zoom"), "Zoom") { styleValue.get() == "DropDown" }

    @JvmField
    val scaleValue =
        FloatValue("Scale", 1.0f, 0.4f, 2f) { styleValue.get() == "DropDown" || styleValue.get() == "Smooth" }

    override fun onEnable() {
        Launch.clickGui.progress = 0.0
        Launch.clickGui.slide = 0.0
        Launch.clickGui.lastMS = System.currentTimeMillis()
        mc.displayGuiScreen(Launch.clickGui)
        when (styleValue.get().lowercase(Locale.getDefault())) {
            "smooth" -> mc.displayGuiScreen(SmoothClickGui())

            "dropdown" -> Launch.clickGui.style =
                DropDown()

            "tab" -> mc.displayGuiScreen(NewUi.getInstance())
        }
    }

    // Named "ColorMode" (not "Color") because the picker below is the "Color" setting now; two values
    // sharing a name would make Module.getValue() resolve only the first of them.
    private val colorModeValue = ListValue(
        "ColorMode",
        arrayOf("Custom", "Sky", "Rainbow", "LiquidSlowly", "Fade"),
        "Sky"
    ) { styleValue.get() == "DropDown" || styleValue.get() == "Tab" }

    /** Custom colour of the DropDown / Tab styles, picked with the visual palette (ARGB, alpha included). */
    private val colorValue = ColorValue(
        "Color",
        Color(255, 255, 255).rgb
    ) { styleValue.get() == "DropDown" || styleValue.get() == "Tab" }

    /**
     * Smooth 风格里模块启用时的高亮色：整行填这个色、模块名写成白字。
     * 默认是参考图里那种橘粉。
     */
    val enabledColorValue = ColorValue("EnabledColor", Color(242, 167, 157).rgb) { styleValue.get() == "Smooth" }

    private val saturationValue =
        FloatValue("Saturation", 0.4f, 0f, 1f) { styleValue.get() == "DropDown" || styleValue.get() == "Tab" }
    private val brightnessValue =
        FloatValue("Brightness", 1f, 0f, 1f) { styleValue.get() == "DropDown" || styleValue.get() == "Tab" }
    private val mixerSecondsValue =
        IntegerValue("Seconds", 6, 1, 10) { styleValue.get() == "DropDown" || styleValue.get() == "Tab" }

    fun generateColor(): Color {
        var c = Color(255, 255, 255, 255)
        when (colorModeValue.get().lowercase(Locale.getDefault())) {
            // The picker stores ARGB, so the chosen alpha is honoured here too.
            "custom" -> c = Color(colorValue.get(), true)
            "rainbow" -> c = Color(
                RenderUtils.getRainbowOpaque(
                    mixerSecondsValue.get(),
                    saturationValue.get(),
                    brightnessValue.get(),
                    0
                )
            )

            "sky" -> c = RenderUtils.skyRainbow(0, saturationValue.get(), brightnessValue.get())
            "liquidslowly" -> c = LiquidSlowly(System.nanoTime(), 0, saturationValue.get(), brightnessValue.get())
            "fade" -> c = fade(Color(colorValue.get(), true), 0, 100)
        }
        return c
    }
}
