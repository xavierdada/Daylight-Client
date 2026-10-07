package net.aspw.client.features.module.impl.mcgo

import net.aspw.client.event.EventTarget
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.Rotation
import net.aspw.client.utils.RotationUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.block.Block
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.util.BlockPos
import net.minecraft.util.ChatComponentText
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI

@ModuleInfo(
    name = "RageBot",
    spacedName = "Rage Bot",
    category = ModuleCategory.MCGO
)
class RageBot : Module() {

    private val targetModeValue = ListValue("Target Mode", arrayOf("Distance", "Health"), "Distance")
    private val hitboxValue = ListValue("Hitbox", arrayOf("Head", "Chest", "All"), "Head")
    private val rangeValue = FloatValue("Range", 256.0f, 10.0f, 500.0f)
    
    private val resolverValue = BoolValue("Anti-AA Resolver", true)
    private val multiPointValue = FloatValue("Multipoint Scale", 0.7f, 0.0f, 1.0f)
    
    private val autowallValue = BoolValue("Autowall Check", true)
    
    private val predictValue = BoolValue("Position Predict", false)
    private val predictTicksValue = IntegerValue("Predict Ticks", 2, 1, 5) { predictValue.get() }
    
    private val silentValue = BoolValue("Silent Rotation", true)
    
    private val debugValue = BoolValue("Debug", false)

    companion object {
        var isShooting = false
    }

    private data class ShotRecord(
        val target: EntityPlayer,
        val startHealth: Float,
        val predictX: Double,
        val predictY: Double,
        val predictZ: Double,
        var ticksPassed: Int = 0
    )

    private val pendingShots = mutableListOf<ShotRecord>()
    private var lastMissTime = 0L 

    override fun onDisable() {
        isShooting = false
        pendingShots.clear() 
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val iterator = pendingShots.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            shot.ticksPassed++

            if (shot.target.isDead || shot.target.health < shot.startHealth || shot.target.hurtTime > 0) {
                iterator.remove()
                continue
            }

            if (shot.ticksPassed > 15) {
                if (debugValue.get() && System.currentTimeMillis() - lastMissTime > 300L) { 
                    val distSq = (shot.target.posX - shot.predictX) * (shot.target.posX - shot.predictX) +
                                 (shot.target.posY - shot.predictY) * (shot.target.posY - shot.predictY) +
                                 (shot.target.posZ - shot.predictZ) * (shot.target.posZ - shot.predictZ)

                    val reason = if (predictValue.get() && distSq > 1.0) {
                        "WrongPrediction" 
                    } else {
                        "WrongSpreadPrediction"
                    }

                    mc.thePlayer.addChatMessage(ChatComponentText("§8[§cRageBot§8] §7Miss shots due to §c$reason"))
                    lastMissTime = System.currentTimeMillis()
                }
                iterator.remove() 
            }
        }

        val targets = mc.theWorld.playerEntities
            .filter { it != mc.thePlayer && it.isEntityAlive && !it.isInvisible }
            .filter { mc.thePlayer.getDistanceToEntity(it) <= rangeValue.get() }

        if (targets.isEmpty()) {
            isShooting = false 
            return
        }

        val sortedTargets = when (targetModeValue.get().lowercase()) {
            "health" -> targets.sortedBy { it.health }
            else -> targets.sortedBy { mc.thePlayer.getDistanceToEntity(it) }
        }

        var finalTarget: EntityPlayer? = null
        var bestX = 0.0
        var bestY = 0.0
        var bestZ = 0.0

        for (target in sortedTargets) {
            var targetX = target.posX
            var targetY = target.posY
            var targetZ = target.posZ

            if (predictValue.get()) {
                val ticks = predictTicksValue.get().toDouble()
                targetX += (target.posX - target.lastTickPosX) * ticks
                targetY += (target.posY - target.lastTickPosY) * ticks
                targetZ += (target.posZ - target.lastTickPosZ) * ticks
            }

            val yOffset = when (hitboxValue.get().lowercase()) {
                "head" -> target.getEyeHeight().toDouble() - 0.15
                "chest" -> target.getEyeHeight().toDouble() * 0.65
                else -> if (target.motionY > 0) target.getEyeHeight().toDouble() * 0.65 else target.getEyeHeight().toDouble() - 0.15
            }
            targetY += yOffset

            if (resolverValue.get()) {
                targetX += (target.posX - target.lastTickPosX) * multiPointValue.get().toDouble()
                targetZ += (target.posZ - target.lastTickPosZ) * multiPointValue.get().toDouble()
            }

            if (autowallValue.get()) {
                if (!isLineOfSightClearOrPenetrable(targetX, targetY, targetZ)) {
                    continue 
                }
            }

            finalTarget = target
            bestX = targetX
            bestY = targetY
            bestZ = targetZ
            break
        }

        if (finalTarget == null) {
            isShooting = false
            return
        }

        isShooting = true

        val diffX = bestX - mc.thePlayer.posX
        val diffY = bestY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble())
        val diffZ = bestZ - mc.thePlayer.posZ
        val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)
        
        val yaw = (atan2(diffZ, diffX) * 180.0 / PI).toFloat() - 90f
        val pitch = -(atan2(diffY, diffXZ) * 180.0 / PI).toFloat()

        val oldYaw = mc.thePlayer.rotationYaw
        val oldPitch = mc.thePlayer.rotationPitch

        mc.thePlayer.rotationYaw = yaw
        mc.thePlayer.rotationPitch = pitch
        mc.netHandler.addToSendQueue(C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround))

        mc.rightClickMouse() 
        if (pendingShots.size < 10) { 
            pendingShots.add(ShotRecord(finalTarget, finalTarget.health, bestX, bestY, bestZ))
        }

        if (silentValue.get()) {
            mc.thePlayer.rotationYaw = oldYaw
            mc.thePlayer.rotationPitch = oldPitch
        }
        
        RotationUtils.setTargetRotation(Rotation(yaw, pitch))
    }

    private fun isLineOfSightClearOrPenetrable(tx: Double, ty: Double, tz: Double): Boolean {
        val startX = mc.thePlayer.posX
        val startY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight().toDouble()
        val startZ = mc.thePlayer.posZ

        val distance = sqrt((tx - startX) * (tx - startX) + (ty - startY) * (ty - startY) + (tz - startZ) * (tz - startZ))
        if (distance <= 0.1) return true

        val step = 0.15
        val steps = (distance / step).toInt()

        val dirX = (tx - startX) / distance
        val dirY = (ty - startY) / distance
        val dirZ = (tz - startZ) / distance

        for (i in 0..steps) {
            val currentX = startX + dirX * (i * step)
            val currentY = startY + dirY * (i * step)
            val currentZ = startZ + dirZ * (i * step)

            val blockPos = BlockPos(currentX, currentY, currentZ)
            val block = mc.theWorld.getBlockState(blockPos).block

            if (block != null && block !== net.minecraft.init.Blocks.air) {
                if (!isPenetratable(block)) {
                    return false
                }
            }
        }
        return true
    }

    private fun isPenetratable(block: Block): Boolean {
        val name = Block.blockRegistry.getNameForObject(block).toString().lowercase()
        return name.contains("glass") || 
               name.contains("pane") || 
               name.contains("fence") || 
               name.contains("door") || 
               name.contains("trapdoor") || 
               name.contains("leaves") || 
               name.contains("slab") || 
               name.contains("stair") || 
               name.contains("carpet") || 
               name.contains("web") || 
               name.contains("water") || 
               name.contains("lava")
    }
}
