package net.aspw.client.features.module.impl.combat

import net.aspw.client.event.EventTarget
import net.aspw.client.event.Render3DEvent
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.render.PathRender
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color
import kotlin.math.*

@ModuleInfo(
    name = "StrafeBot", spacedName = "Strafe Bot",
    category = ModuleCategory.COMBAT 
)
class StrafeBot : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Strafe", "Random", "Behind", "AllRandom", "Teleport"), "Strafe")
    
    private val speedValue = FloatValue("Speed", 1.5f, 0.05f, 30.0f) { !modeValue.get().equals("Teleport", true) }
    
    private val tpTickValue = IntegerValue("Tick", 5, 1, 100) { modeValue.get().equals("Teleport", true) }
    
    private val radiusValue = FloatValue("Radius", 2.0f, 0.1f, 6.0f)

    /**
     * 找目标的最大距离。**0 = 整张图**（默认）：不再限制距离，在所有已加载实体里取最近的那个活着的玩家。
     * 以前这里在代码里写死了 8 格（不是设置项），所以出了 8 格就找不到人、模块直接不动。
     */
    private val rangeValue = FloatValue("Range", 0f, 0f, 512f, "m")
    
    private val yOffsetToggle = BoolValue("Y-Offset", false)
    private val yOffsetValue = FloatValue("Y-Offset-Value", 1.0f, -5.0f, 5.0f) { yOffsetToggle.get() }

    // ---- Path rendering -------------------------------------------------
    // StrafeBot itself predicts nothing (see onUpdate below): in "Strafe" /
    // "Behind" it aims at a point on a circle around the target, and in
    // "Random" / "AllRandom" it aims at a random offset that is re-rolled once
    // reached, so there is no trajectory to draw. What is drawable is the
    // trajectory the bot actually flew, which is what these options render.
    private val pathValue = BoolValue("Path", false)
    private val pathMarkersValue = BoolValue("Path-Markers", true) { pathValue.get() }
    private val pathThicknessValue = FloatValue("Path-Thickness", 2.0f, 1.0f, 5.0f) { pathValue.get() }
    private val pathRedValue = IntegerValue("Path-R", 0, 0, 255) { pathValue.get() }
    private val pathGreenValue = IntegerValue("Path-G", 255, 0, 255) { pathValue.get() }
    private val pathBlueValue = IntegerValue("Path-B", 255, 0, 255) { pathValue.get() }
    private val pathAlphaValue = IntegerValue("Path-Alpha", 200, 0, 255) { pathValue.get() }

    private var direction = 1
    
    private var randomOffsetX = 0.0
    private var randomOffsetY = 0.0
    private var randomOffsetZ = 0.0
    private var arSpeedMultiplier = 1.0
    private var hasTarget = false

    private var tpTickCounter = 0
    private var lastTpOffsetX = 0.0
    private var lastTpOffsetY = 0.0
    private var lastTpOffsetZ = 0.0

    override fun onEnable() {
        direction = 1
        hasTarget = false 
        tpTickCounter = 0
        lastTpOffsetX = 0.0
        lastTpOffsetY = 0.0
        lastTpOffsetZ = 0.0
        PathRender.clear()
    }

    override fun onDisable() {
        PathRender.clear()
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        // TASK(StrafeBot-world-range):原来写死 8 格 -> 现在默认整张图(Range = 0 表示不限制),
        // 依旧取最近的那个活着的玩家。另外排掉 Blink 的客户端克隆(entityId -1337),
        // 它也是 EntityPlayer 且在 loadedEntityList 里,不排掉就会追着自己的幽灵跑。
        val range = rangeValue.get()
        val target = mc.theWorld.loadedEntityList
            .filterIsInstance<EntityPlayer>()
            .filter {
                it != mc.thePlayer && it.isEntityAlive && it.entityId != -1337 &&
                        (range <= 0f || mc.thePlayer.getDistanceToEntity(it) <= range)
            }
            .minByOrNull { mc.thePlayer.getDistanceToEntity(it) } ?: return

        val maxRadius = radiusValue.get().toDouble()
        val baseY = target.posY + if (yOffsetToggle.get()) yOffsetValue.get().toDouble() else 0.0

        if (modeValue.get().equals("Teleport", true)) {
            mc.thePlayer.motionX = 0.0
            mc.thePlayer.motionY = 0.0
            mc.thePlayer.motionZ = 0.0

            tpTickCounter++
            if (tpTickCounter >= tpTickValue.get()) {
                tpTickCounter = 0
                
                var rx = 0.0
                var ry = 0.0
                var rz = 0.0
                var distToLast = 0.0
                var attempts = 0
                
                do {
                    rx = (Math.random() * 2.0 - 1.0) * maxRadius
                    ry = (Math.random() * 2.0 - 1.0) * maxRadius
                    rz = (Math.random() * 2.0 - 1.0) * maxRadius
                    
                    val dx = rx - lastTpOffsetX
                    val dy = ry - lastTpOffsetY
                    val dz = rz - lastTpOffsetZ
                    distToLast = sqrt(dx * dx + dy * dy + dz * dz)
                    
                    attempts++
                } while ((rx * rx + ry * ry + rz * rz > maxRadius * maxRadius || distToLast < maxRadius * 0.8) && attempts < 50)
                
                lastTpOffsetX = rx
                lastTpOffsetY = ry
                lastTpOffsetZ = rz
                
                mc.thePlayer.setPosition(target.posX + rx, baseY + ry, target.posZ + rz)
                recordTracePoint()
            }
            return
        }

        val baseSpeed = speedValue.get().toDouble()
        var destX = 0.0
        var destY = 0.0
        var destZ = 0.0
        var currentSpeed = baseSpeed

        when (modeValue.get().lowercase()) {
            "strafe" -> {
                if (mc.thePlayer.isCollidedHorizontally) direction = -direction
                val angleToPlayer = atan2(mc.thePlayer.posZ - target.posZ, mc.thePlayer.posX - target.posX)
                val nextAngle = angleToPlayer + (baseSpeed / maxRadius) * direction
                destX = target.posX + cos(nextAngle) * maxRadius
                destY = baseY
                destZ = target.posZ + sin(nextAngle) * maxRadius
            }
            "behind" -> {
                val yawRad = Math.toRadians(target.rotationYaw.toDouble())
                destX = target.posX + sin(yawRad) * maxRadius
                destY = baseY
                destZ = target.posZ - cos(yawRad) * maxRadius
            }
            "random" -> {
                val absDestX = target.posX + randomOffsetX
                val absDestY = baseY
                val absDestZ = target.posZ + randomOffsetZ

                val dX = absDestX - mc.thePlayer.posX
                val dY = absDestY - mc.thePlayer.posY
                val dZ = absDestZ - mc.thePlayer.posZ
                val dist3D = sqrt(dX * dX + dY * dY + dZ * dZ)

                if (!hasTarget || dist3D <= max(currentSpeed, 1.0)) {
                    val angle = Math.random() * Math.PI * 2
                    val r = sqrt(Math.random()) * maxRadius 
                    randomOffsetX = cos(angle) * r
                    randomOffsetZ = sin(angle) * r
                    randomOffsetY = 0.0
                    hasTarget = true
                }

                destX = target.posX + randomOffsetX
                destY = baseY
                destZ = target.posZ + randomOffsetZ
            }
            "allrandom" -> {
                val absDestX = target.posX + randomOffsetX
                val absDestY = baseY + randomOffsetY
                val absDestZ = target.posZ + randomOffsetZ

                val dX = absDestX - mc.thePlayer.posX
                val dY = absDestY - mc.thePlayer.posY
                val dZ = absDestZ - mc.thePlayer.posZ
                val dist3D = sqrt(dX * dX + dY * dY + dZ * dZ)

                val threshold = max(baseSpeed * arSpeedMultiplier, 1.0)

                if (!hasTarget || dist3D <= threshold) {
                    var rx = 0.0
                    var ry = 0.0
                    var rz = 0.0
                    do {
                        rx = (Math.random() * 2.0 - 1.0) * maxRadius
                        ry = (Math.random() * 2.0 - 1.0) * maxRadius
                        rz = (Math.random() * 2.0 - 1.0) * maxRadius
                    } while (rx * rx + ry * ry + rz * rz > maxRadius * maxRadius)

                    randomOffsetX = rx
                    randomOffsetY = ry
                    randomOffsetZ = rz
                    
                    arSpeedMultiplier = 0.5 + Math.random() * 1.0 
                    hasTarget = true
                }

                destX = target.posX + randomOffsetX
                destY = baseY + randomOffsetY
                destZ = target.posZ + randomOffsetZ
                currentSpeed = baseSpeed * arSpeedMultiplier
            }
        }

        val diffX = destX - mc.thePlayer.posX
        val diffY = destY - mc.thePlayer.posY
        val diffZ = destZ - mc.thePlayer.posZ
        
        val dist3D = sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ)

        if (dist3D > currentSpeed) {
            mc.thePlayer.motionX = (diffX / dist3D) * currentSpeed
            mc.thePlayer.motionY = (diffY / dist3D) * currentSpeed
            mc.thePlayer.motionZ = (diffZ / dist3D) * currentSpeed
        } else {
            mc.thePlayer.motionX = diffX
            mc.thePlayer.motionY = diffY
            mc.thePlayer.motionZ = diffZ
        }

        // One sample per tick, taken after the motion for this tick has been
        // decided, so the recorded trail matches where the bot actually went.
        recordTracePoint()
    }

    /**
     * Stores the current player position in the path ring buffer.
     * Only runs while the module (and the Path option) is on, and the buffer
     * itself is capped at [PathRender.CAPACITY] points.
     */
    private fun recordTracePoint() {
        if (!pathValue.get()) return
        val player = mc.thePlayer ?: return
        PathRender.addPoint(player.posX, player.posY + player.getEyeHeight().toDouble() * 0.5, player.posZ)
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (!pathValue.get()) return
        val player = mc.thePlayer ?: return
        if (PathRender.pointCount <= 0) return

        val partialTicks = mc.timer.renderPartialTicks

        // Interpolated player position: the head of the line, so it never
        // jitters between ticks.
        val curX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks
        val curY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks
        val curZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks

        val pathColor = Color(
            pathRedValue.get(),
            pathGreenValue.get(),
            pathBlueValue.get(),
            pathAlphaValue.get()
        ).rgb
        val markerColor = Color(
            pathRedValue.get(),
            pathGreenValue.get(),
            pathBlueValue.get(),
            (pathAlphaValue.get() * 0.65f).toInt().coerceIn(0, 255)
        ).rgb

        val renderManager = mc.renderManager
        PathRender.drawHistory(
            curX, curY, curZ,
            renderManager.renderPosX, renderManager.renderPosY, renderManager.renderPosZ,
            pathPhase(curX, curY, curZ),
            pathColor, markerColor,
            pathThicknessValue.get(),
            pathMarkersValue.get()
        )
    }

    /**
     * Fraction of a tick already travelled from the newest recorded point
     * towards the interpolated player position (0..1). Projecting the motion
     * vector onto that segment is exact enough to make the trail glide instead
     * of stepping once per tick, and it needs no extra state.
     */
    private fun pathPhase(curX: Double, curY: Double, curZ: Double): Double {
        if (PathRender.pointCount <= 0) return 0.0
        val dx = mc.thePlayer.motionX
        val dy = mc.thePlayer.motionY
        val dz = mc.thePlayer.motionZ
        val motion = sqrt(dx * dx + dy * dy + dz * dz)
        if (motion < 1.0E-4) return 0.0
        val newestX = PathRender.getX(0)
        val newestY = PathRender.getY(0)
        val newestZ = PathRender.getZ(0)
        val segX = curX - newestX
        val segY = curY - newestY
        val segZ = curZ - newestZ
        val segLengthSq = segX * segX + segY * segY + segZ * segZ
        if (segLengthSq < 1.0E-8) return 0.0
        val travelled = (dx * segX + dy * segY + dz * segZ) / sqrt(segLengthSq)
        return (travelled / sqrt(segLengthSq)).coerceIn(0.0, 1.0)
    }
}
