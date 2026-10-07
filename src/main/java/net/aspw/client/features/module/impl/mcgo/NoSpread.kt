package net.aspw.client.features.module.impl.mcgo

import net.aspw.client.event.EventTarget
import net.aspw.client.event.PacketEvent
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.Rotation
import net.aspw.client.utils.RotationUtils
import net.aspw.client.value.ListValue
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S08PacketPlayerPosLook

@ModuleInfo(
    name = "NoSpread",
    spacedName = "No Spread",
    category = ModuleCategory.MCGO
)
class NoSpread : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Cancel", "Predict", "Stop"), "Cancel")

    private var lastRecoilYaw = 0f
    private var lastRecoilPitch = 0f
    
    private var wasMovingBefore = false

    override fun onEnable() {
        lastRecoilYaw = 0f
        lastRecoilPitch = 0f
        wasMovingBefore = false
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val isFiring = mc.gameSettings.keyBindUseItem.isKeyDown || RageBot.isShooting

        if (modeValue.get().equals("Stop", true)) {
            if (isFiring) {
                if (mc.thePlayer.motionX != 0.0 || mc.thePlayer.motionZ != 0.0) {
                    mc.thePlayer.motionX = 0.0
                    mc.thePlayer.motionZ = 0.0
                    
                    mc.thePlayer.movementInput.moveForward = 0f
                    mc.thePlayer.movementInput.moveStrafe = 0f
                    wasMovingBefore = true
                }
            } else {
                if (wasMovingBefore) {
                    wasMovingBefore = false
                }
            }
        }

        if (modeValue.get().equals("Predict", true)) {
            if (isFiring) {
                val deltaYaw = mc.thePlayer.rotationYaw - mc.thePlayer.prevRotationYaw
                val deltaPitch = mc.thePlayer.rotationPitch - mc.thePlayer.prevRotationPitch

                if (deltaYaw != 0f || deltaPitch != 0f) {
                    lastRecoilYaw = deltaYaw
                    lastRecoilPitch = deltaPitch
                }

                if (lastRecoilYaw != 0f || lastRecoilPitch != 0f) {
                    val compensatedYaw = mc.thePlayer.rotationYaw - lastRecoilYaw
                    val compensatedPitch = mc.thePlayer.rotationPitch - lastRecoilPitch
                    RotationUtils.setTargetRotation(Rotation(compensatedYaw, compensatedPitch))
                }
            } else {
                lastRecoilYaw = 0f
                lastRecoilPitch = 0f
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (packet is S08PacketPlayerPosLook && modeValue.get().equals("Cancel", true)) {
            if (packet.yaw != 0f || packet.pitch != 0f) {
                mc.netHandler.addToSendQueue(
                    C03PacketPlayer.C06PacketPlayerPosLook(
                        packet.x, packet.y, packet.z,
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 
                        false
                    )
                )
                mc.thePlayer.setPosition(packet.x, packet.y, packet.z)
                mc.thePlayer.motionX = 0.0
                mc.thePlayer.motionY = 0.0
                mc.thePlayer.motionZ = 0.0
                event.cancelEvent()
            }
        }
    }
}
