package net.aspw.client.value

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Colour value represents a value with an ARGB colour, alpha included.
 *
 * The raw ARGB int is what is stored, and it is serialised as a plain json number exactly like
 * [IntegerValue] does, so values saved by this class survive a round trip through [ValuesConfig].
 *
 * The wanted accessors are inherited from [Value] with the exact signatures
 * `get(): Int` and `set(argb: Int)`; neither `onChange` nor `onChanged` is overridden here, so
 * `set` only stores the colour. They are intentionally not redeclared, because doing so would
 * clash with the (final) members of `Value<Int>` on the JVM.
 */
open class ColorValue(name: String, value: Int, displayable: () -> Boolean) :
    Value<Int>(name, value, displayable) {

    constructor(name: String, value: Int) : this(name, value, { true })

    /** Alpha channel of the current colour, 0..255. */
    fun getAlpha(): Int = (value ushr 24) and 0xFF

    /** Red channel of the current colour, 0..255. */
    fun getRed(): Int = (value shr 16) and 0xFF

    /** Green channel of the current colour, 0..255. */
    fun getGreen(): Int = (value shr 8) and 0xFF

    /** Blue channel of the current colour, 0..255. */
    fun getBlue(): Int = value and 0xFF

    /** Replaces the alpha channel only, keeping the RGB part untouched. */
    fun setAlpha(alpha: Int) {
        val alphaByte = alpha.coerceIn(0, 255)
        set((value and 0x00FFFFFF) or (alphaByte shl 24))
    }

    /** Stores the colour described by hue/saturation/brightness/alpha, every one of them in 0..1. */
    fun setHsv(hue: Float, saturation: Float, brightness: Float, alpha: Float) {
        val rgb = Color.HSBtoRGB(
            hue.coerceIn(0f, 1f),
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f)
        )
        val alphaByte = (alpha.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        set((rgb and 0x00FFFFFF) or (alphaByte shl 24))
    }

    override fun toJson() = JsonPrimitive(value)

    override fun fromJson(element: JsonElement) {
        // Guard the type: a config saved before this value existed can hold a string under the
        // same name (the Gui module used to store its colour *mode* as "Color"), and an
        // unguarded asInt() throws, which aborts loading the whole value.json.
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber)
            value = element.asInt
    }

}
