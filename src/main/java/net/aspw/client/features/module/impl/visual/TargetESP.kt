package net.aspw.client.features.module.impl.visual

import net.aspw.client.event.AttackEvent
import net.aspw.client.event.EventTarget
import net.aspw.client.event.Render3DEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.EntityUtils
import net.aspw.client.value.FloatValue
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import org.lwjgl.opengl.GL11
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@ModuleInfo(name = "TargetESP", spacedName = "Target ESP", category = ModuleCategory.VISUAL)
class TargetESP : Module() {

    private val scanSpeedValue = FloatValue("ScanSpeed", 1.0f, 0.1f, 5.0f)

    var targets = mutableListOf<Entity>()

    override fun onDisable() {
        targets.clear()
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        val target = event.targetEntity ?: return
        if (target !in targets && EntityUtils.isSelected(target, true)) {
            targets.add(target)
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (targets.isEmpty() || mc.thePlayer == null) return

        val ticks = mc.thePlayer.ticksExisted.toFloat() + mc.timer.renderPartialTicks
        
        val rad = (ticks * 0.1f) * scanSpeedValue.get()
        val wave = (sin(rad) + 1.2f) / 2.4f 

        val iterator = targets.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()

            if (target.isDead || mc.thePlayer.getDistanceToEntity(target) > 6f) {
                iterator.remove()
                continue
            }

            val entityHeight = if (target.height > 0f) target.height else 1.8f
            val animatedY = wave * entityHeight

            GL11.glPushMatrix()
            
            val renderX = (target.lastTickPosX + (target.posX - target.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX).toFloat()
            val renderY = (target.lastTickPosY + (target.posY - target.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY).toFloat() + animatedY
            val renderZ = (target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ).toFloat()

            GL11.glTranslatef(renderX, renderY, renderZ)
            
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glRotatef(90f, 1f, 0f, 0f)

            GL11.glLineWidth(4.5f)
            GL11.glColor3f(0f, 0f, 0f)
            GL11.glBegin(GL11.GL_LINE_LOOP)
            for (i in 0..360 step 6) {
                val angleRad = (i * PI / 180.0f).toFloat()
                GL11.glVertex2f(cos(angleRad) * 0.8f, sin(angleRad) * 0.8f)
            }
            GL11.glEnd()

            GL11.glLineWidth(3.0f)
            GL11.glBegin(GL11.GL_LINE_LOOP)
            for (i in 0..360 step 6) {
                val angleRad = (i * PI / 180.0f).toFloat()
                GL11.glColor3f(242f / 255f, 232f / 255f, 213f / 255f)
                GL11.glVertex2f(cos(angleRad) * 0.8f, sin(angleRad) * 0.8f)
            }
            GL11.glEnd()

            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)

            GL11.glPopMatrix()

            GlStateManager.resetColor()
            GL11.glColor4f(1f, 1f, 1f, 1f)
        }
    }
}
