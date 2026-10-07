package net.aspw.client.features.module.impl.combat

import net.aspw.client.Launch
import net.aspw.client.event.EventState
import net.aspw.client.event.EventTarget
import net.aspw.client.event.MotionEvent
import net.aspw.client.event.PacketEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.EntityUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.IntegerValue
import net.aspw.client.value.ListValue
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import kotlin.math.sqrt

/**
 * PredictAutoBlock —— 预测式自动举盾。
 *
 * ## 举盾 / 松手分别由什么触发
 *
 * - **举盾**（[startBlocking]）：预测到敌人马上会打到我们。把每个敌人按相对速度外推
 *   [predictTicksValue] 个 tick，外推后进入 [rangeValue] 且更近（或已经贴到 3.2 格）就举盾；
 * - **松手**（[release]）：只在**看到自己真正发出攻击包**的那一刻松（[onPacket] 里收到出站
 *   `C02PacketUseEntity(ATTACK)`），松 [reBlockDelayValue] 个 tick 后再举起来。
 *
 * 为什么不用"冷却满了 + 有人在近战距离"这种启发式来判断要出手：这两个条件在战斗中几乎恒为真，
 * 结果会是"一直松着手"，等于没有 AutoBlock。只有出站攻击包才是"我这一下真的打出去了"的确切信号。
 *
 * ## 为什么松手包能排在攻击包前面
 *
 * [KillAura] 的 AutoBlock 是"攻击包由它自己发，所以可以先塞 C07 再塞 C02"。本模块要和任意攻击来源
 * （原版左键、别的 aura）共存，插手不了别人排好的顺序，于是利用客户端发包是**嵌套**的这一点：
 * 出站包的事件在写入缓冲之前触发，在这个回调里再发一条 C07 会把那条包的缓冲先写完，
 * 于是顺序变成"松手 -> 攻击"，正是服务端与反作弊期望的顺序。
 * 万一某个环境下顺序没有变成这样，最坏也只是"打完才松手"（等于没起作用），不会坏事。
 *
 * ## 其它
 *
 * - 手里必须有剑（[autoSwitchValue] 打开时热键栏里有剑即可）：1.8 只有剑算格挡；
 * - 战斗中**不**来回切格挡格（那会产生一堆 C09、观感也差），只在没有威胁一段时间后 / 关闭模块时切回；
 * - [ignoreFriendsValue] 走客户端自己的好友名单；[onlyWhileAuraValue] 只在 KillAura 开着时工作；
 * - 客户端视角的举盾动作由 `PacketManager.isVisualBlocking` 决定，那个字段目前只统计
 *   KillAura / TPAura / KillAuraRecode。想让手臂也举起来，在 `MixinModelBiped` 与 `MixinItemRenderer`
 *   两处判断里加上本模块的 [isBlocking] 即可（各一行）。
 */
@ModuleInfo(
    name = "PredictAutoBlock",
    spacedName = "Predict Auto Block",
    category = ModuleCategory.COMBAT,
    forceNoSound = true
)
class PredictAutoBlock : Module() {

    /** Packet = 只对服务器伪装；Legit = 本地也真的进入使用物品状态（会有原版举盾减速）。 */
    private val modeValue = ListValue("Mode", arrayOf("Packet", "Legit"), "Legit")

    /** 多远之内的敌人才值得预测。 */
    private val rangeValue = FloatValue("Range", 4.5f, 3f, 8f)

    /** 往前预测几个 tick；0 = 只看当前位置。 */
    private val predictTicksValue = IntegerValue("Predict-Ticks", 3, 0, 10)

    /** 松手之后过几个 tick 再举起来（打出去的这一下要留出时间）。 */
    private val reBlockDelayValue = IntegerValue("ReBlock-Delay", 2, 1, 20)

    /**
     * 出手时先松手再攻击。
     *
     * **默认关**：1.8 里格挡和攻击本来就能同时进行（block-hit），服务端也不会因此拒绝攻击，
     * 所以这次松手没有收益，反而是"漏防"的主因 —— 快速 aura 每 tick 都发攻击包，
     * 每次都把松手窗口重置成 [reBlockDelayValue] 个 tick，结果"松 2 tick、举 1 tick"，
     * 大部分时间都在松手。只有确实需要"先松手再出手"的环境(某些反作弊/某些服务端)才打开它。
     */
    private val releaseOnAttackValue = BoolValue("Release-On-Attack", false)

    private val autoSwitchValue = BoolValue("Auto-Switch", true)

    private val switchBackValue = BoolValue("Switch-Back", true) { autoSwitchValue.get() }

    private val onlyWhileAuraValue = BoolValue("Only-While-Aura", false)

    private val ignoreFriendsValue = BoolValue("Ignore-Friends", true)

    /** 失去威胁后继续举盾的余量（tick）。防止预测在边缘抖动导致"一会儿举一会儿松"。 */
    private val holdTicksValue = IntegerValue("Hold-Ticks", 4, 0, 20)

    /** 当前是否处于"已经给服务器发过举盾包"的状态。给渲染/别的模块读。 */
    var isBlocking = false
        private set

    /** > 0 表示正在"松手窗口"里（刚出手），这个窗口内不举盾。 */
    private var releaseTicks = 0

    /** 失去威胁后还要继续举盾的 tick 数（见 [holdTicksValue]）。 */
    private var holdTicks = 0

    /** 没有威胁之后再过多少个 tick 才把热键栏切回去。 */
    private var idleTicks = 0

    /** 切剑之前的热键栏格子，-1 表示没有保存。 */
    private var savedSlot = -1

    // ------------------------------------------------------------------ 事件

    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE) return
        val player = mc.thePlayer ?: return
        if (mc.theWorld == null) return

        if (onlyWhileAuraValue.get() && Launch.moduleManager.getModule(KillAura::class.java)?.state != true) {
            release()
            return
        }

        // 松手窗口：刚出手，先别举盾。
        if (releaseTicks > 0) {
            releaseTicks--
            release()
            return
        }

        // TASK(PredictAutoBlock-hold):威胁 + 保持窗口。
        // 预测成立就重置保持计时；否则每 tick 递减。没有这层的话，预测在"快进快出/擦边"时
        // 会一会儿成立一会儿不成立 -> 举盾/松手反复切换 -> 漏防。
        val threat = findThreat()
        if (threat != null) {
            holdTicks = holdTicksValue.get()
        } else if (holdTicks > 0) {
            holdTicks--
        }

        if (threat != null || holdTicks > 0) {
            idleTicks = 0
            if (!isBlocking) startBlocking()
            return
        }

        // 没威胁：先松手，再等一会儿把热键栏切回去（避免敌人刚出范围就来回切格）。
        release()
        if (savedSlot != -1) {
            idleTicks++
            if (idleTicks > 40) restoreSlot()
        }
    }

    /**
     * 出站攻击包：在它写入缓冲之前补一条松手包，顺序就变成"松手 -> 攻击"。
     * 见类注释里"为什么松手包能排在攻击包前面"。
     */
    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        if (packet !is C02PacketUseEntity) return
        if (packet.action != C02PacketUseEntity.Action.ATTACK) return
        if (!isBlocking || !releaseOnAttackValue.get()) return

        release()
        releaseTicks = reBlockDelayValue.get()
    }

    override fun onDisable() {
        release()
        restoreSlot()
        releaseTicks = 0
        idleTicks = 0
    }

    // ------------------------------------------------------------------ 预测

    /**
     * 找出"最可能马上打到我们"的敌人。
     *
     * 把相对位置按相对速度外推到 [predictTicksValue] 个 tick 之后：外推后已经近到能打
     * （3.2 格 = 近战 3.0 再加一点余量），或者外推后比现在更近 —— 都当作威胁。多个威胁取最近的。
     */
    private fun findThreat(): EntityPlayer? {
        val player = mc.thePlayer ?: return null
        val range = rangeValue.get()
        val ticks = predictTicksValue.get()
        var best: EntityPlayer? = null
        var bestDistance = Double.MAX_VALUE

        for (entity in mc.theWorld.playerEntities) {
            if (entity === player || entity.isDead || entity.health <= 0f) continue
            // Blink 的客户端克隆(entityId -1337)也在 playerEntities 里,别把"自己"当成威胁。
            if (entity.entityId == -1337) continue
            if (ignoreFriendsValue.get() && EntityUtils.isFriend(entity)) continue

            val dx = entity.posX - player.posX
            val dy = entity.posY - player.posY
            val dz = entity.posZ - player.posZ
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > range) continue

            // 相对速度 = 对方这一 tick 的位移 - 我们的位移（都用上一 tick 的坐标差）。
            val relativeX = (entity.posX - entity.lastTickPosX) - (player.posX - player.lastTickPosX)
            val relativeY = (entity.posY - entity.lastTickPosY) - (player.posY - player.lastTickPosY)
            val relativeZ = (entity.posZ - entity.lastTickPosZ) - (player.posZ - player.lastTickPosZ)

            val predictedX = dx + relativeX * ticks
            val predictedY = dy + relativeY * ticks
            val predictedZ = dz + relativeZ * ticks
            val predicted = sqrt(predictedX * predictedX + predictedY * predictedY + predictedZ * predictedZ)

            if (predicted <= 3.2 || predicted < distance) {
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = entity
                }
            }
        }
        return best
    }

    // ------------------------------------------------------------------ 收发

    private fun startBlocking() {
        val player = mc.thePlayer ?: return

        if (autoSwitchValue.get() && player.inventory.getCurrentItem()?.item !is ItemSword) {
            val swordSlot = findSwordSlot()
            if (swordSlot == -1) return
            if (savedSlot == -1) savedSlot = player.inventory.currentItem
            player.inventory.currentItem = swordSlot
            mc.netHandler.addToSendQueue(C09PacketHeldItemChange(swordSlot))
        }

        val held = player.inventory.getCurrentItem() ?: return
        if (held.item !is ItemSword) return

        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(held))
        if (modeValue.get().equals("Legit", true)) {
            // 本地也进入"使用中"：服务端与客户端状态一致，代价是原版的举盾减速（Legit 模式的本意）。
            player.setItemInUse(held, 72000)
            // 对齐原版：开始使用物品时收掉疾跑。原版举盾时移动输入只有 0.2 倍，
            // 而"疾跑 + 正在使用物品"在服务端模拟里是不成立的组合，会喂给 Grim 的 NoSlow/疾跑检查。
            if (player.isSprinting) player.setSprinting(false)
        }
        isBlocking = true
    }

    private fun release() {
        if (!isBlocking) return
        isBlocking = false
        val player = mc.thePlayer ?: return
        mc.netHandler.addToSendQueue(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
            )
        )
        if (player.isUsingItem) player.stopUsingItem()
    }

    private fun restoreSlot() {
        idleTicks = 0
        val player = mc.thePlayer ?: return
        val slot = savedSlot
        savedSlot = -1
        if (!autoSwitchValue.get() || !switchBackValue.get() || slot == -1) return
        if (player.inventory.currentItem == slot) return
        player.inventory.currentItem = slot
        mc.netHandler.addToSendQueue(C09PacketHeldItemChange(slot))
    }

    /** 热键栏里第一把剑；找不到返回 -1。 */
    private fun findSwordSlot(): Int {
        val inventory = mc.thePlayer?.inventory ?: return -1
        for (slot in 0..8) {
            if (inventory.getStackInSlot(slot)?.item is ItemSword) return slot
        }
        return -1
    }
}
