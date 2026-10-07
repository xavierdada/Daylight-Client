package net.aspw.client.features.module.impl.player

import net.aspw.client.event.EventTarget
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.value.BoolValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAppleGold

@ModuleInfo(name = "PredictDamage", spacedName = "Predict Damage", category = ModuleCategory.PLAYER)
class PredictDamage : Module() {

    val modeValue = ListValue("Mode", arrayOf("HurtTime", "Tick"), "HurtTime")
    val tickDelayValue = IntegerValue("TickDelay", 10, 1, 20) 
    
    val ignoreGappleValue = BoolValue("IgnoreGapple", true)
    val calcGappleTimeValue = BoolValue("CalcGappleTime", true)
    val debugValue = BoolValue("Debug", true)

    var predictedNextHitTime: Long = 500L 

    var lastHitTime = 0L
    private val hitIntervals = mutableListOf<Long>() 

    private val enemyEatingStates = HashMap<EntityPlayer, Boolean>()
    private var enemyJustAteGapple = false 
    var lastGappleTime = 0L
    
    private val gappleHitIntervals = mutableListOf<Long>() 
    private val gappleIntervals = mutableListOf<Long>()    

    private var lastHurtTick = 0

    override fun onDisable() {
        hitIntervals.clear()
        gappleHitIntervals.clear()
        gappleIntervals.clear()
        enemyEatingStates.clear()
        enemyJustAteGapple = false
        lastHitTime = 0L
        lastGappleTime = 0L
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) return

        val currentTime = System.currentTimeMillis()

        for (entity in mc.theWorld.playerEntities) {
            if (entity == mc.thePlayer || entity.getDistanceToEntity(mc.thePlayer) > 15f) continue

            val isEatingGapple = entity.isEating && entity.heldItem?.item is ItemAppleGold
            val wasEating = enemyEatingStates[entity] ?: false

            if (wasEating && !isEatingGapple) {
                enemyJustAteGapple = true

                if (ignoreGappleValue.get() && calcGappleTimeValue.get()) {
                    if (lastGappleTime != 0L) {
                        val gappleDelta = currentTime - lastGappleTime
                        addToList(gappleIntervals, gappleDelta, 3)
                    }
                    lastGappleTime = currentTime
                    
                    if (modeValue.get().equals("HurtTime", true) && debugValue.get()) {
                        chat("§e[PredictDamage] 敌人刚吃完金苹果! 平均进食间隔: §c${getAverage(gappleIntervals)}ms")
                    }
                }
            }
            enemyEatingStates[entity] = isEatingGapple
        }

        if (mc.thePlayer.hurtTime == 9 && mc.thePlayer.ticksExisted != lastHurtTick) {
            lastHurtTick = mc.thePlayer.ticksExisted

            if (lastHitTime != 0L) {
                val delta = currentTime - lastHitTime

                if (ignoreGappleValue.get() && enemyJustAteGapple) {
                    enemyJustAteGapple = false 

                    if (calcGappleTimeValue.get()) {
                        addToList(gappleHitIntervals, delta, 5)
                        predictedNextHitTime = getAverage(gappleHitIntervals)
                        
                        if (modeValue.get().equals("HurtTime", true) && debugValue.get()) {
                            chat("§6[PredictDamage] 记录金苹果第一刀间隔: §c${delta}ms §7(平均: ${predictedNextHitTime}ms)")
                        }
                    }
                } else {
                    addToList(hitIntervals, delta, 5)
                    predictedNextHitTime = getAverage(hitIntervals)
                    
                    if (modeValue.get().equals("HurtTime", true) && debugValue.get()) {
                        chat("§a[PredictDamage] 常规受击间隔: §c${delta}ms §7(预测下次攻击: ${predictedNextHitTime}ms)")
                    }
                }
            }
            lastHitTime = currentTime
        }

        if (modeValue.get().equals("Tick", true)) {
            predictedNextHitTime = tickDelayValue.get() * 50L
        }
    }

    private fun addToList(list: MutableList<Long>, value: Long, maxCapacity: Int) {
        list.add(value)
        if (list.size > maxCapacity) {
            list.removeAt(0)
        }
    }

    private fun getAverage(list: List<Long>): Long {
        if (list.isEmpty()) return 500L 
        return list.sum() / list.size
    }
}

