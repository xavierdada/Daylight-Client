package net.aspw.client.features.module.impl.combat

import net.aspw.client.event.EventTarget
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.InventoryUtils
import net.aspw.client.utils.timer.MSTimer
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.init.Items
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import java.util.*

@ModuleInfo(name = "AutoGapple", spacedName = "Auto Gapple", category = ModuleCategory.COMBAT)
class AutoGapple : Module() {
    
    val modeValue = ListValue("Mode", arrayOf("Auto", "Once", "Head"), "Auto")
    private val healthValue = FloatValue("Health", 10F, 1F, 20F)
    private val delayValue = IntegerValue("Delay", 150, 0, 1000, "ms")
    private val noAbsorption = BoolValue("NoAbsorption", true)
    private val fastEat = BoolValue("FastEat", false)
    
    private val timer = MSTimer()

    private var isEating = false
    private var eatTick = 0
    
    private var lastEatTime = 0L

    override fun onEnable() {
        lastEatTime = System.currentTimeMillis()
        isEating = false
        eatTick = 0
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent?) {
        if (isEating && !fastEat.get()) {
            eatTick++
            if (eatTick >= 35) {
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                isEating = false
                eatTick = 0
                
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastEatTime
                chat("Gapple was eaten in ${elapsed}ms")
                lastEatTime = currentTime

                timer.reset()
            }
            return 
        }

        when (modeValue.get().lowercase(Locale.getDefault())) {
            "once" -> {
                if (!isEating) {
                    doEat(true)
                    state = false
                }
            }

            "auto" -> {
                if (!timer.hasTimePassed(delayValue.get().toLong()))
                    return
                if (mc.thePlayer.health <= healthValue.get()) {
                    doEat(false)
                }
            }

            "head" -> {
                if (!timer.hasTimePassed(delayValue.get().toLong()))
                    return
                if (mc.thePlayer.health <= healthValue.get()) {
                    val headInHotbar = InventoryUtils.findItem(36, 45, Items.skull)
                    if (headInHotbar != -1) {
                        val realSlot = headInHotbar - 36
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(realSlot))
                        val headStack = mc.thePlayer.inventory.getStackInSlot(realSlot)
                        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(headStack))
                        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                        timer.reset()
                    }
                }
            }
        }
    }

    private fun doEat(warn: Boolean) {
        if (noAbsorption.get() && !warn) {
            val abAmount = mc.thePlayer.absorptionAmount
            if (abAmount > 0)
                return
        }

        val gappleInHotbar = InventoryUtils.findItem(36, 45, Items.golden_apple)
        if (gappleInHotbar != -1) {
            val realSlot = gappleInHotbar - 36
            
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(realSlot))
            val gappleStack = mc.thePlayer.inventory.getStackInSlot(realSlot)
            mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(gappleStack))

            if (fastEat.get()) {
                repeat(35) {
                    mc.netHandler.addToSendQueue(C03PacketPlayer(mc.thePlayer.onGround))
                }
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastEatTime
                chat("Gapple was eaten in ${elapsed}ms")
                lastEatTime = currentTime

                timer.reset()
            } else {
                isEating = true
                eatTick = 0
            }
        } else if (warn)
            chat("No gapple were found in hotbar")
    }

    override fun onDisable() {
        if (isEating) {
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
            isEating = false
        }
    }

    override val tag: String
        get() = modeValue.get()
}
