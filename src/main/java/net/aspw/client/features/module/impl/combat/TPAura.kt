package net.aspw.client.features.module.impl.combat

import net.aspw.client.event.EventTarget
import net.aspw.client.event.Render3DEvent
import net.aspw.client.event.UpdateEvent
import net.aspw.client.event.WorldEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.EntityUtils
import net.aspw.client.utils.PacketUtils
import net.aspw.client.utils.RotationUtils
import net.aspw.client.utils.pathfinder.MainPathFinder
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.pow

@ModuleInfo(
    name = "TPAura", spacedName = "TP Aura",
    category = ModuleCategory.COMBAT
)
class TPAura : Module() {

    private val apsValue = IntegerValue("CPS", 20, 1, 20)
    
    private val multiPacketValue = IntegerValue("MultiPacket", 1, 1, 150)
    private val packetSpooferValue = BoolValue("PacketSpoofer", true) { multiPacketValue.get() > 1 }
    
    private val maxTargetsValue = IntegerValue("MaxTarget", 1, 1, 8)
    private val rangeValue = IntegerValue("Range", 30, 10, 70, "m")
    private val fovValue = FloatValue("Fov", 180F, 0F, 180F, "°")
    private val swingValue = ListValue("Swing", arrayOf("Normal", "Packet", "None"), "Normal")
    private val newAttackValue = BoolValue("1.9+Attack", false)
    private val rotationValue = BoolValue("Rotations", true)
    
    private val autoBlockValue = BoolValue("AutoBlock", true)
    private val autoBlockModeValue = ListValue("AutoBlockMode", arrayOf("Default", "Vanilla"), "Default") { autoBlockValue.get() }

    private val predictValue = BoolValue("Predict", true)
    private val predictTicksValue = FloatValue("PredictTicks", 1.5f, 0.5f, 10.0f)
    private val pingCompensateValue = BoolValue("PingCompensation", true)
    
    private val desyncFixValue = BoolValue("Anti-DeSync", true)
    private val criticalFixValue = BoolValue("CriticalFix", true)
    private val debugValue = BoolValue("Debug", true)

    private var tickCounter = 0
    var isBlocking = false
    private var blockingStatus = false
    var lastTarget: EntityLivingBase? = null
    
    class AttackRecord(
        val target: EntityLivingBase,
        val predictedPos: Vec3,
        val time: Long,
        val pathSize: Int,
        val targetSpeedAtHit: Double 
    )
    private val attackRecords = mutableListOf<AttackRecord>()

    private val canBlock: Boolean
        get() = mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item is ItemSword

    override fun onDisable() {
        unblockVanilla()
        isBlocking = false
        tickCounter = 0
        lastTarget = null
        attackRecords.clear()
    }

    override fun onEnable() {
        tickCounter = 0
        blockingStatus = false
        attackRecords.clear()
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        state = false
        chat("TPAura was disabled due to world change")
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (lastTarget != null && rotationValue.get())
            RotationUtils.faceLook(lastTarget!!, 80f, 120f)

        val iter = attackRecords.iterator()
        val currentTime = System.currentTimeMillis()
        while (iter.hasNext()) {
            val record = iter.next()
            
            if (record.target.hurtTime > 0 || record.target.isDead) {
                iter.remove()
                continue
            }
            
            if (currentTime - record.time > 500L) {
                if (debugValue.get()) {
                    val currentDist = record.target.getDistance(record.predictedPos.xCoord, record.predictedPos.yCoord, record.predictedPos.zCoord)
                    
                    val reason = if (record.pathSize <= 1) {
                        "Wrong Calculation (寻路算法失败，原地挥刀)"
                    } else if (record.targetSpeedAtHit > 1.5) {
                        "Desync (目标速度过快/Ping导致严重残影)"
                    } else if (currentDist > 4.0) {
                        "Wrong Prediction (目标变向或瞬移导致预测错位)"
                    } else {
                        "Packet Error (攻击包被服务器反作弊吞掉)"
                    }
                    
                    chat("Miss Hits due to §c§l$reason")
                }
                iter.remove()
            }
        }

        tickCounter++
        val cps = apsValue.get().coerceIn(1, 20)
        val ticksPerAttack = 20.0 / cps

        if (tickCounter >= ticksPerAttack) {
            runAttack()
            tickCounter = 0 
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) return
        
        if (predictValue.get() && lastTarget != null) {
            val target = lastTarget!!
            if (target.isDead) return
            
            val partial = mc.timer.renderPartialTicks
            val (finalX, finalY, finalZ) = calculatePredictedPos(target, partial)
            
            val playerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * partial
            val playerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * partial
            val playerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * partial
            
            val renderPosX = mc.getRenderManager().renderPosX
            val renderPosY = mc.getRenderManager().renderPosY
            val renderPosZ = mc.getRenderManager().renderPosZ
            
            GL11.glPushMatrix()
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glLineWidth(2f)
            
            RenderUtils.glColor(Color(255, 255, 255, 120))
            GL11.glBegin(GL11.GL_LINES)
            GL11.glVertex3d(playerX - renderPosX, playerY - renderPosY, playerZ - renderPosZ)
            GL11.glVertex3d(finalX - renderPosX, finalY - renderPosY, finalZ - renderPosZ)
            GL11.glEnd()
            
            val width = target.width / 2.0
            val height = target.height.toDouble()
            val bb = AxisAlignedBB(
                finalX - width, finalY - (target.height / 2.0), finalZ - width,
                finalX + width, finalY + (target.height / 2.0), finalZ + width
            )
            RenderUtils.drawAxisAlignedBB(bb, Color(255, 0, 0, 60))
            
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)
            GL11.glPopMatrix()
            
            GlStateManager.pushMatrix()
            GlStateManager.enableBlend()
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
            GlStateManager.color(1f, 1f, 1f, 0.4f)
            GlStateManager.disableDepth()
            GlStateManager.depthMask(false)
            GlStateManager.disableAlpha()

            val ghostYaw = (Math.toDegrees(Math.atan2(target.posZ - finalZ, target.posX - finalX)) - 90).toFloat()
            
            mc.renderManager.renderEntityWithPosYaw(
                mc.thePlayer, 
                finalX - renderPosX, 
                finalY - (target.height / 2.0) - renderPosY, 
                finalZ - renderPosZ, 
                ghostYaw, 
                partial
            )
            
            GlStateManager.enableAlpha()
            GlStateManager.depthMask(true)
            GlStateManager.enableDepth()
            GlStateManager.disableBlend()
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.popMatrix()
        }
    }

    private fun runAttack() {
        if (mc.thePlayer == null || mc.theWorld == null) return

        val targets = arrayListOf<EntityLivingBase>()
        var entityCount = 0

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity is EntityLivingBase && EntityUtils.isSelected(entity, true) && mc.thePlayer.getDistanceToEntity(entity) <= rangeValue.get()) {
                if (fovValue.get() < 180F && RotationUtils.getRotationDifference(entity) > fovValue.get())
                    continue

                if (entityCount >= maxTargetsValue.get())
                    break

                if (autoBlockValue.get()) {
                    isBlocking = true
                }
                targets.add(entity)
                entityCount++
            }
        }

        if (targets.isEmpty()) {
            lastTarget = null
            isBlocking = false
            unblockVanilla()
            return
        }

        targets.sortBy { it.health }
        val groundState = !criticalFixValue.get()

        targets.forEach { target ->
            val (finalX, finalY, finalZ) = calculatePredictedPos(target, 1.0f)
            
            val motX = target.posX - target.lastTickPosX
            val motY = target.posY - target.lastTickPosY
            val motZ = target.posZ - target.lastTickPosZ
            val currentSpeed = sqrt(motX.pow(2) + motY.pow(2) + motZ.pow(2))

            val path = MainPathFinder.computePath(
                net.aspw.client.utils.pathfinder.Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ), 
                net.aspw.client.utils.pathfinder.Vec3(finalX, finalY, finalZ)
            )

            for (vec in path) {
                PacketUtils.sendPacketNoEvent(C04PacketPlayerPosition(vec.x, vec.y, vec.z, groundState))
            }

            lastTarget = target
            val multiplier = multiPacketValue.get()

            if (packetSpooferValue.get() && multiplier > 1) {
                mc.netHandler.addToSendQueue(C0APacketAnimation())
                for (i in 0 until multiplier) {
                    mc.netHandler.addToSendQueue(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
                }
            } else {
                for (i in 0 until multiplier) {
                    if (newAttackValue.get()) {
                        mc.netHandler.addToSendQueue(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
                    }

                    when (swingValue.get().lowercase(Locale.getDefault())) {
                        "normal" -> mc.thePlayer.swingItem()
                        "packet" -> mc.netHandler.addToSendQueue(C0APacketAnimation())
                    }

                    if (!newAttackValue.get()) {
                        mc.netHandler.addToSendQueue(C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK))
                    }
                }
            }

            if (autoBlockValue.get() && autoBlockModeValue.get().equals("Vanilla", true) && canBlock) {
                mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()))
                blockingStatus = true
            }

            attackRecords.add(AttackRecord(target, Vec3(finalX, finalY, finalZ), System.currentTimeMillis(), path.size, currentSpeed))

            path.reverse()
            for (vec in path) {
                PacketUtils.sendPacketNoEvent(C04PacketPlayerPosition(vec.x, vec.y, vec.z, groundState))
            }
        }
    }

    private fun unblockVanilla() {
        if (blockingStatus) {
            if (canBlock) {
                PacketUtils.sendPacketNoEvent(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    )
                )
            }
            blockingStatus = false
        }
    }

    private fun calculatePredictedPos(target: EntityLivingBase, partialTicks: Float): DoubleArray {
        var targetX = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks
        var targetY = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks
        var targetZ = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks
        
        val baseTargetX = targetX
        val baseTargetY = targetY
        val baseTargetZ = targetZ

        if (predictValue.get()) {
            var motX = (target.posX - target.lastTickPosX).coerceIn(-2.5, 2.5)
            var motY = (target.posY - target.lastTickPosY).coerceIn(-2.5, 2.5)
            var motZ = (target.posZ - target.lastTickPosZ).coerceIn(-2.5, 2.5)
            
            var latencyTicks = 0
            if (pingCompensateValue.get()) {
                val ping = mc.netHandler.getPlayerInfo(mc.thePlayer.uniqueID)?.responseTime ?: 50
                latencyTicks = (ping / 50.0).toInt().coerceIn(0, 5)
            }

            var totalTicks = (predictTicksValue.get() + latencyTicks).toInt()

            if (desyncFixValue.get()) {
                val currentSpeed = sqrt(motX.pow(2) + motY.pow(2) + motZ.pow(2))
                if (currentSpeed > 0.8) { 
                    totalTicks = max(1, (totalTicks / (currentSpeed * 1.5)).toInt())
                }
            }
            
            for (i in 1..totalTicks) {
                targetX += motX
                targetY += motY
                targetZ += motZ

                if (desyncFixValue.get()) {
                    if (!target.onGround) motY -= 0.04
                    motX *= 0.91
                    motZ *= 0.91
                }
            }

            if (desyncFixValue.get()) {
                val maxDeviation = 6.0
                val deviation = sqrt((targetX - baseTargetX).pow(2) + (targetY - baseTargetY).pow(2) + (targetZ - baseTargetZ).pow(2))
                if (deviation > maxDeviation) {
                    val ratio = maxDeviation / deviation
                    targetX = baseTargetX + (targetX - baseTargetX) * ratio
                    targetY = baseTargetY + (targetY - baseTargetY) * ratio
                    targetZ = baseTargetZ + (targetZ - baseTargetZ) * ratio
                }
            }
        }

        val distanceOffset = 0.5 
        val angle = Math.atan2(mc.thePlayer.posZ - targetZ, mc.thePlayer.posX - targetX)
        
        val finalX = targetX + cos(angle) * distanceOffset
        val finalY = targetY + (target.height / 2.0)
        val finalZ = targetZ + sin(angle) * distanceOffset

        return doubleArrayOf(finalX, finalY, finalZ)
    }
}
