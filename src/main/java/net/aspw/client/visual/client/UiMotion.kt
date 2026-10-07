package net.aspw.client.visual.client

import net.aspw.client.Launch
import net.aspw.client.features.module.impl.visual.Gui
import net.aspw.client.utils.UiAnimation
import java.awt.Color

/** Shared settings for the new interface transitions; no new rendering dependency required. */
object UiMotion {
    @JvmStatic
    fun duration(baseMillis: Long): Long {
        val gui = Launch.moduleManager.getModule(Gui::class.java) ?: return baseMillis
        if (!gui.uiAnimationsValue.get()) return 0L
        return (baseMillis / gui.uiAnimationSpeedValue.get().coerceAtLeast(0.25f)).toLong()
    }

    @JvmStatic
    @JvmOverloads
    fun animate(animation: UiAnimation, target: Float, baseMillis: Long = 180L): Float =
        animation.animate(target, duration(baseMillis))

    @JvmStatic
    fun blend(from: Int, to: Int, progress: Float): Int {
        val a = Color(from, true)
        val b = Color(to, true)
        val t = progress.coerceIn(0f, 1f)
        return Color(
            (a.red + (b.red - a.red) * t).toInt(),
            (a.green + (b.green - a.green) * t).toInt(),
            (a.blue + (b.blue - a.blue) * t).toInt(),
            (a.alpha + (b.alpha - a.alpha) * t).toInt()
        ).rgb
    }
}
