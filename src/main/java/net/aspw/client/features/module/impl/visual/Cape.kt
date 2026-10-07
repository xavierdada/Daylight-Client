package net.aspw.client.features.module.impl.visual

import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.APIConnecter
import net.aspw.client.utils.MinecraftInstance
import net.aspw.client.value.BoolValue
import net.aspw.client.value.ListValue
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.*

@ModuleInfo(
    name = "Cape",
    category = ModuleCategory.VISUAL,
    forceNoSound = true,
    onlyEnable = true,
    array = false
)
class Cape : Module() {

    val customCape = BoolValue("CustomCape", true)
    val styleValue = ListValue(
        "Mode",
        arrayOf(
            "None",
            "Daylight"
        ),
        "Daylight"
    ) { customCape.get() }

    private val capeCache = hashMapOf<String, CapeStyle>()

    /**
     * Solid Daylight cape, painted client-side.
     *
     * The API does not publish a "daylight" cape yet, so the module draws a plain beige one
     * rather than falling back to the placeholder texture (which showed up as a white slab).
     * Cape textures are 64x32, and the cape model only samples the top-left quad, so filling
     * the whole image is enough. Change the two colours below to re-tint the cape.
     */
    private val beigeCape: ResourceLocation by lazy {
        val image = BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        // A flat fill read as a featureless slab, so the cape is shaded instead: a light top
        // fading into a distinctly darker hem, plus a darker outline around the cape quad
        // (the top-left 22x17 area of the skin layout) so the silhouette stays readable.
        val top = Color(0xF7, 0xEE, 0xDC)
        val hem = Color(0xBF, 0xAE, 0x8B)
        val capeHeight = 17
        for (y in 0 until image.height) {
            val t = (y.toFloat() / (capeHeight - 1)).coerceIn(0f, 1f)
            graphics.color = Color(
                (top.red + (hem.red - top.red) * t).toInt(),
                (top.green + (hem.green - top.green) * t).toInt(),
                (top.blue + (hem.blue - top.blue) * t).toInt()
            )
            graphics.drawLine(0, y, image.width - 1, y)
        }
        graphics.color = Color(0xA3, 0x91, 0x6E)
        graphics.drawRect(0, 0, 21, 16)
        graphics.dispose()
        MinecraftInstance.mc.textureManager.getDynamicTextureLocation(
            "daylight-cape",
            DynamicTexture(image)
        )
    }

    fun getCapeLocation(value: String): ResourceLocation? {
        val key = value.uppercase(Locale.getDefault())
        if (capeCache[key] == null)
            capeCache[key] = CapeStyle.valueOf(key)
        return when (capeCache[key]!!) {
            CapeStyle.NONE ->
                if (APIConnecter.hasImage("none", "cape")) APIConnecter.callImage("none", "cape") else null

            CapeStyle.DAYLIGHT -> beigeCape
        }
    }

    enum class CapeStyle {
        NONE,
        DAYLIGHT
    }

    override val tag: String
        get() = styleValue.get()
}
