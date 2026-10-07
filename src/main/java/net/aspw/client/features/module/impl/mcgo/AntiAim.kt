package net.aspw.client.features.module.impl.mcgo

import net.aspw.client.event.EventTarget
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.Rotation
import net.aspw.client.utils.RotationUtils
import net.aspw.client.value.FloatValue
import net.aspw.client.value.ListValue
import net.minecraft.entity.player.EntityPlayer
import kotlin.math.atan2
import kotlin.math.PI

@ModuleInfo(
    name = "AntiAim", 
    spacedName = "Anti Aim",
    category = ModuleCategory.MCGO 
)
class AntiAim : Module() {

    private val pitchValue = ListValue("Pitch", arrayOf("None", "Down", "Up"), "Down")
    
    private val yawBaseValue = ListValue("Yaw Base", arrayOf("Local", "At Target"), "Local")
    
    private val yawAddValue = FloatValue("Yaw Add", 180f, -180f, 180f)
    
    private val modifierValue = ListValue("Modifier", arrayOf("None", "Jitter", "Spin"), "None")
    
    private val modifierDegreeValue = FloatValue("Modifier Degree", 30f, 0f, 180f) { modifierValue.get().equals("Jitter", true) }
    private val spinSpeedValue = FloatValue("Spin Speed", 20f, 1f, 90f) { modifierValue.get().equals("Spin", true) }

    private var spinYaw = 0f
    private var jitterSwitch = false

    override fun onEnable() {
        spinYaw = 0f
        jitterSwitch = false
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        var targetPitch = mc.thePlayer.rotationPitch
        when (pitchValue.get().lowercase()) {
            "down" -> targetPitch = 90f
            "up" -> targetPitch = -90f
        }

        var baseYaw = mc.thePlayer.rotationYaw
        if (yawBaseValue.get().equals("At Target", true)) {
            val target = mc.theWorld.playerEntities
                .filter { it != mc.thePlayer && it.isEntityAlive && !it.isInvisible }
                .minByOrNull { mc.thePlayer.getDistanceToEntity(it) }
            
            if (target != null) {
                val dx = target.posX - mc.thePlayer.posX
                val dz = target.posZ - mc.thePlayer.posZ
                baseYaw = (atan2(dz, dx) * 180.0 / PI).toFloat() - 90f
            }
        }

        var finalYaw = baseYaw + yawAddValue.get()

        when (modifierValue.get().lowercase()) {
            "jitter" -> {
                val degree = modifierDegreeValue.get()
                jitterSwitch = !jitterSwitch
                finalYaw += if (jitterSwitch) degree else -degree
            }
            "spin" -> {
                spinYaw += spinSpeedValue.get()
                if (spinYaw > 360f) spinYaw -= 360f
                finalYaw += spinYaw
            }
        }

        RotationUtils.setTargetRotation(Rotation(finalYaw, targetPitch))
    }
}
