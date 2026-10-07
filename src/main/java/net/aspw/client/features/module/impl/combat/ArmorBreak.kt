package net.aspw.client.features.module.impl.combat

import net.aspw.client.Launch
import net.aspw.client.event.EventTarget
import net.aspw.client.event.PacketEvent
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.features.module.impl.player.PredictDamage
import net.aspw.client.features.module.impl.visual.DynamicIsland
import net.aspw.client.utils.timer.MSTimer
import net.aspw.client.value.BoolValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C0APacketAnimation

@ModuleInfo(name = "ArmorBreak", spacedName = "Armor Break", category = ModuleCategory.COMBAT)
class ArmorBreak : Module() {

    companion object {
        @JvmField
        var fakeRenderStack: ItemStack? = null
        @JvmField
        var isFakeRendering = false
    }

    val modeValue = ListValue("Mode", arrayOf("Default", "Smart"), "Default")
    val switchDelayValue = IntegerValue("SwitchDelay", 1, 0, 10) 
    val smartBufferTime = IntegerValue("DefenseBuffer", 50, 0, 200)

    private val invFilterValue = BoolValue("Inv-Filter-Upgrade", true)
    
    val importKillAuraValue = BoolValue("ImportKillAura", true)
    
    private val antiFakeItemValue = BoolValue("AntiFakeItem", true)
    
    private val fakeResourcesValue = BoolValue("FakeResources", false)

    private val useDiamondSword = BoolValue("Diamond-Sword", true)
    private val useIronSword = BoolValue("Iron-Sword", true)
    private val useDiamondAxe = BoolValue("Diamond-Axe", true)
    private val useStoneSword = BoolValue("Stone-Sword", true)
    private val useIronAxe = BoolValue("Iron-Axe", true)
    private val useGoldSword = BoolValue("Gold-Sword", true)
    private val useWoodSword = BoolValue("Wood-Sword", true)
    private val useStoneAxe = BoolValue("Stone-Axe", true)
    private val useGoldAxe = BoolValue("Gold-Axe", true)
    private val useWoodAxe = BoolValue("Wood-Axe", true)

    private val ironProtectDurability = IntegerValue("Iron-Protect-Dura", 20, 0, 250)

    private var weaponIndex = 0
    private var lastOrderString = ""
    private var hasWarned = false 
    
    private val clickTimer = MSTimer()
    private var isWeaponsSwapped = false
    private var swappedSlotInInventory = -1 
    private var currentHandSlot = -1        
    private var expectedSwappedItemId = -1 

    private var hasSwungAfterSwap = false

    private val predictDamageModule: PredictDamage?
        get() = Launch.moduleManager.getModule(PredictDamage::class.java) as? PredictDamage

    override fun onEnable() {
        weaponIndex = 0
        hasWarned = false 
        isWeaponsSwapped = false
        swappedSlotInInventory = -1
        currentHandSlot = -1
        expectedSwappedItemId = -1
        hasSwungAfterSwap = false
        clickTimer.reset()
        
        isFakeRendering = false
        fakeRenderStack = null
        
        val displayList = getSortedArmorBreakWeapons(true)
        lastOrderString = if (displayList.isEmpty()) "None" else displayList.take(5).joinToString(" > ") { getWeaponShortName(it.second) }
    }

    override fun onDisable() {
        restoreWeaponPosition() 
        isFakeRendering = false
        fakeRenderStack = null
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (mc.thePlayer == null) return

        if (modeValue.get().equals("Smart", true)) {
            val pd = predictDamageModule
            if (pd == null || !pd.state) {
                if (!hasWarned) {
                    chat("§c[ArmorBreak] Smart 模式需要开启 PredictDamage 模块！暂时退化为 Default 模式。")
                    hasWarned = true
                }
            } else {
                hasWarned = false 
            }
        } else {
            hasWarned = false
        }

        val displayList = getSortedArmorBreakWeapons(true)
        val top5 = displayList.take(5).joinToString(" > ") { getWeaponShortName(it.second) }
        val newOrder = if (displayList.isEmpty()) "None" else if (displayList.size > 5) "$top5 > ..." else top5
        
        if (newOrder != lastOrderString) {
            lastOrderString = newOrder
            val di = Launch.moduleManager.getModule(DynamicIsland::class.java) as? DynamicIsland
            if (di != null && di.state) {
                di.showCustomNotification("ArmorBreak Notification", "Current Order: $newOrder")
            }
        }

        if (isWeaponsSwapped && swappedSlotInInventory != -1 && currentHandSlot != -1) {
            if (importKillAuraValue.get()) {
                val killAura = Launch.moduleManager.getModule(KillAura::class.java)
                if (killAura != null && killAura.state) {
                    if (!hasSwungAfterSwap) return 
                }
            }

            if (modeValue.get().equals("Default", true)) {
                if (clickTimer.hasTimePassed(switchDelayValue.get() * 50L)) {
                    restoreWeaponPosition()
                }
            } else if (modeValue.get().equals("Smart", true)) {
                val pd = predictDamageModule
                if (pd == null || !pd.state) {
                    if (clickTimer.hasTimePassed(switchDelayValue.get() * 50L)) restoreWeaponPosition()
                    return
                }

                val timeSinceLastHit = System.currentTimeMillis() - pd.lastHitTime
                val predictedInterval = pd.predictedNextHitTime
                val timeRemaining = predictedInterval - timeSinceLastHit
                
                if (timeRemaining <= smartBufferTime.get()) {
                    restoreWeaponPosition()
                }
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        
        if (packet is C0APacketAnimation) {
            if (isWeaponsSwapped) {
                hasSwungAfterSwap = true
            }
        }
        
        if (packet is C02PacketUseEntity && packet.action == C02PacketUseEntity.Action.ATTACK) {
            val player = mc.thePlayer ?: return

            if (isWeaponsSwapped || !clickTimer.hasTimePassed(50L)) return

            val weaponList = getSortedArmorBreakWeapons()
            if (weaponList.isEmpty()) return

            val currentSlot = player.inventory.currentItem
            val currentContainerSlot = currentSlot + 36

            var targetInventorySlot = -1

            if (modeValue.get().equals("Default", true)) {
                if (weaponIndex >= weaponList.size) weaponIndex = 0
                targetInventorySlot = weaponList[weaponIndex].first
                weaponIndex++
            } else if (modeValue.get().equals("Smart", true)) {
                val pd = predictDamageModule
                val bestSlot = weaponList[0].first

                if (pd == null || !pd.state) {
                    targetInventorySlot = bestSlot
                } else {
                    val timeSinceLastHit = System.currentTimeMillis() - pd.lastHitTime
                    val predictedInterval = pd.predictedNextHitTime
                    val timeRemaining = predictedInterval - timeSinceLastHit

                    if (timeRemaining > smartBufferTime.get()) {
                        targetInventorySlot = bestSlot
                    }
                }
            }

            if (targetInventorySlot != -1 && targetInventorySlot != currentContainerSlot) {
                swapWeaponsViaClick(targetInventorySlot, currentContainerSlot)
            }
        }
    }

    private fun swapWeaponsViaClick(srcSlot: Int, destSlot: Int) {
        val player = mc.thePlayer ?: return
        val controller = mc.playerController ?: return
        val windowId = player.inventoryContainer.windowId

        val hotbarButton = destSlot - 36

        try {
            val originalHandStack = player.inventoryContainer.getSlot(destSlot).stack
            expectedSwappedItemId = originalHandStack?.item?.let { net.minecraft.item.Item.getIdFromItem(it) } ?: -1

            if (fakeResourcesValue.get() && originalHandStack != null && originalHandStack.item is ItemSword) {
                fakeRenderStack = originalHandStack
                isFakeRendering = true
            }

            controller.windowClick(windowId, srcSlot, hotbarButton, 2, player)
            
            if (antiFakeItemValue.get()) {
                mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
            }
            
            isWeaponsSwapped = true
            hasSwungAfterSwap = false
            swappedSlotInInventory = srcSlot
            currentHandSlot = destSlot
            clickTimer.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreWeaponPosition() {
        val player = mc.thePlayer ?: return
        val controller = mc.playerController ?: return
        
        if (isWeaponsSwapped && swappedSlotInInventory != -1 && currentHandSlot != -1) {
            val windowId = player.inventoryContainer.windowId
            val hotbarButton = currentHandSlot - 36 
            
            try {
                val currentInventoryStack = player.inventoryContainer.getSlot(swappedSlotInInventory).stack
                val currentInventoryItemId = currentInventoryStack?.item?.let { net.minecraft.item.Item.getIdFromItem(it) } ?: -1
                
                if (expectedSwappedItemId != -1 && currentInventoryItemId != expectedSwappedItemId) {
                     return 
                }

                controller.windowClick(windowId, swappedSlotInInventory, hotbarButton, 2, player)
                
                if (antiFakeItemValue.get()) {
                    mc.netHandler.addToSendQueue(C09PacketHeldItemChange(player.inventory.currentItem))
                }
                
                isWeaponsSwapped = false
                hasSwungAfterSwap = false
                swappedSlotInInventory = -1
                currentHandSlot = -1
                expectedSwappedItemId = -1
                clickTimer.reset()
                
                isFakeRendering = false
                fakeRenderStack = null
                
            } catch (e: Exception) {
                e.printStackTrace()
                isWeaponsSwapped = false
                hasSwungAfterSwap = false
                swappedSlotInInventory = -1
                currentHandSlot = -1
                expectedSwappedItemId = -1
            }
        }
    }

    private fun getWeaponShortName(score: Int): String {
        return when (score) {
            100 -> "DiaSw"
            90  -> "IronSw"
            80  -> "DiaAx"
            70  -> "StoneSw"
            60  -> "IronAx"
            50  -> "WoodSw"
            49  -> "GoldSw"
            40  -> "StoneAx"
            20  -> "WoodAx"
            19  -> "GoldAx"
            else -> "?"
        }
    }

    private fun getSortedArmorBreakWeapons(forDisplay: Boolean = false): List<Pair<Int, Int>> {
        val player = mc.thePlayer ?: return emptyList()
        val rawWeaponList = mutableListOf<Pair<Int, Int>>()

        for (containerSlot in 9..44) {
            if (!forDisplay && isWeaponsSwapped && containerSlot == currentHandSlot) continue 
            
            val stack = player.inventoryContainer.getSlot(containerSlot).stack ?: continue
            val item = stack.item
            var powerScore = 0

            if (item is ItemSword) {
                if (item.toolMaterialName == "IRON") {
                    val remainingDurability = stack.maxDamage - stack.itemDamage
                    if (remainingDurability <= ironProtectDurability.get()) continue 
                }

                when (item.toolMaterialName) {
                    "EMERALD" -> if (useDiamondSword.get()) powerScore = 100 
                    "IRON"    -> if (useIronSword.get())    powerScore = 90  
                    "STONE"   -> if (useStoneSword.get())   powerScore = 70  
                    "WOOD"    -> if (useWoodSword.get())    powerScore = 50  
                    "GOLD"    -> if (useGoldSword.get())    powerScore = 49  
                }
            } else if (item is ItemAxe) {
                when (item.toolMaterialName) {
                    "EMERALD" -> if (useDiamondAxe.get()) powerScore = 80 
                    "IRON"    -> if (useIronAxe.get())    powerScore = 60 
                    "STONE"   -> if (useStoneAxe.get())   powerScore = 40 
                    "WOOD"    -> if (useWoodAxe.get())    powerScore = 20 
                    "GOLD"    -> if (useGoldAxe.get())    powerScore = 19 
                }
            }

            if (powerScore > 0) {
                rawWeaponList.add(Pair(containerSlot, powerScore))
            }
        }

        if (invFilterValue.get()) {
            val hotbarWeapons = rawWeaponList.filter { it.first in 36..44 }
            val maxHotbarScore = hotbarWeapons.maxOfOrNull { it.second } ?: 0
            val minHotbarScore = hotbarWeapons.minOfOrNull { it.second } ?: 0

            val filteredList = mutableListOf<Pair<Int, Int>>()
            
            for (weapon in rawWeaponList) {
                val slot = weapon.first
                val score = weapon.second

                if (slot in 36..44) {
                    filteredList.add(weapon)
                } else {
                    if (score < minHotbarScore) continue
                    if (score > minHotbarScore) filteredList.add(weapon)
                }
            }
            
            filteredList.sortBy { it.second }
            return filteredList
        }

        rawWeaponList.sortBy { it.second }
        return rawWeaponList
    }
}
