package net.aspw.client.visual.client

import net.aspw.client.utils.APIConnecter
import net.aspw.client.utils.MinecraftInstance
import net.aspw.client.utils.misc.RandomUtils
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

class Particle(var x: Float, var y: Float) {
    private var velocityY = (Math.random() * 100).toFloat()
    private val size = 1f
    private val texture = APIConnecter.callImage("particle", "background")
    private var delay = RandomUtils.nextInt(0, 800)

    fun update(deltaTime: Float) {
        if (delay > 0) {
            delay--
            return
        }

        velocityY += 700f * deltaTime
        y += velocityY * deltaTime

        if (y >= MinecraftInstance.mc.displayHeight) {
            reset()
        }
    }

    fun render() {
        if (delay > 0) return

        MinecraftInstance.mc.textureManager.bindTexture(texture)
        // The placeholder texture is near-white; grey it down so the particles still show
        // against the pale main-menu backdrop.
        GL11.glColor4f(0.72f, 0.75f, 0.80f, 1f)
        val tessellator = Tessellator.getInstance()
        val worldrenderer = tessellator.worldRenderer

        worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
        worldrenderer.pos((x - size).toDouble(), (y + size).toDouble(), 0.0).tex(0.0, 1.0).endVertex()
        worldrenderer.pos((x + size).toDouble(), (y + size).toDouble(), 0.0).tex(1.0, 1.0).endVertex()
        worldrenderer.pos((x + size).toDouble(), (y - size).toDouble(), 0.0).tex(1.0, 0.0).endVertex()
        worldrenderer.pos((x - size).toDouble(), (y - size).toDouble(), 0.0).tex(0.0, 0.0).endVertex()
        tessellator.draw()
    }

    private fun reset() {
        x = (Math.random() * MinecraftInstance.mc.displayWidth).toFloat()
        y = (Math.random() * -20).toFloat()
        velocityY = (Math.random() * 100).toFloat()
        delay = RandomUtils.nextInt(0, 800)
    }
}