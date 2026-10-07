package net.aspw.client.features.module.impl.movement

import net.aspw.client.Launch
import net.aspw.client.event.EventState
import net.aspw.client.event.EventTarget
import net.aspw.client.event.MotionEvent
import net.aspw.client.event.TickEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.features.module.impl.player.Blink
import net.aspw.client.value.BoolValue
import net.minecraft.client.settings.KeyBinding

/**
 * Stuck —— 把自己"钉"在原地。
 *
 * 移植自 LeaderLite 的 `module/modules/movement/Stuck`，行为对齐：
 *
 * 1. **移动输入清零**：`moveForward / moveStrafe / jump / sneak` 全归零，并 `KeyBinding.unPressAllKeys()`；
 * 2. **motion 清零**：每 tick 把 `motionX/Y/Z` 压成 0（停止位置继续漂）；
 * 3. **位置包按住**（关键）：打开客户端的 [Blink] 模块，把出站位置包扣住不发 —— 这样**服务端的视角里
 *    你根本没动**。只做 1、2 而不做 3 的话，本地预测停了、服务器却还在按你的旧速度外推，
 *    一旦恢复就会被拉回 —— 那正是"橡皮筋"的来源；
 * 4. **关闭时**：把开启前保存的 motion 还回去，并把 Blink 恢复成"进来之前的状态"（不粗暴关掉用户自己开的 Blink）。
 *
 * 上游还有一段"收到 S12 击退 / S27 爆炸时把包扣进 delayManager 十 tick 再放"的逻辑，
 * 本客户端没有对应的 delayManager，而且那部分和 [net.aspw.client.features.module.impl.combat.AntiVelocity]
 * 职责重叠，所以没有一起搬 —— 需要的话用 AntiVelocity 的 legit 模式即可。
 *
 * 主要用途：LeaderLite 的 Scaffold 在 Clutch 时会每 10 tick 里开 9 tick 的 Stuck
 * （见 Scaffold 的 updateClutch）。移植版当时没有这个模块，只好就地清零 motion 来模拟，
 * 现在可以换成真正的那套（Planned：让 Scaffold 直接切本模块）。
 */
@ModuleInfo(
    name = "Stuck",
    spacedName = "Stuck",
    category = ModuleCategory.MOVEMENT
)
class Stuck : Module() {

    /** 用 Blink 把出站位置包按住。**默认开**：这是"服务端视角不动"的关键，关掉就只剩本地冻结、容易橡皮筋。 */
    private val blinkValue = BoolValue("Blink", true)

    /** 关闭时把开启前保存的 motion 还回去（上游行为）。 */
    private val restoreMotionValue = BoolValue("Restore-Motion", true)

    /** 连按键一起松开（上游行为），避免输入在下一 tick 又被读进来。 */
    private val unpressKeysValue = BoolValue("Unpress-Keys", true)

    private var savedMotionX = 0.0
    private var savedMotionY = 0.0
    private var savedMotionZ = 0.0

    /** 进入本模块之前 Blink 是不是开着的，用于退出时恢复。 */
    private var blinkWasOn = false
    private var blinkManaged = false

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        savedMotionX = player.motionX
        savedMotionY = player.motionY
        savedMotionZ = player.motionZ
        if (blinkValue.get()) {
            val blink = Launch.moduleManager.getModule(Blink::class.java)
            blinkWasOn = blink?.state == true
            blinkManaged = true
            if (!blinkWasOn) blink?.state = true
        }
    }

    override fun onDisable() {
        val player = mc.thePlayer
        if (player != null && restoreMotionValue.get()) {
            player.motionX = savedMotionX
            player.motionY = savedMotionY
            player.motionZ = savedMotionZ
        }
        if (blinkManaged) {
            blinkManaged = false
            // 只关掉"是本模块开的"那一份，别动用户自己开的 Blink。
            if (!blinkWasOn) Launch.moduleManager.getModule(Blink::class.java)?.state = false
            blinkWasOn = false
        }
    }

    @EventTarget
    fun onTick(event: TickEvent) {
        if (!state) return
        freeze()
    }

    /** MotionEvent 的 PRE 再压一次：tick 边界上被别的模块写回的 motion 也一起清掉。 */
    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (event.eventState != EventState.PRE || !state) return
        freeze()
    }

    private fun freeze() {
        val player = mc.thePlayer ?: return

        if (blinkValue.get()) {
            val blink = Launch.moduleManager.getModule(Blink::class.java)
            if (blink != null && !blink.state) blink.state = true
        }

        player.motionX = 0.0
        player.motionY = 0.0
        player.motionZ = 0.0

        player.movementInput.moveForward = 0f
        player.movementInput.moveStrafe = 0f
        player.movementInput.jump = false
        player.movementInput.sneak = false

        if (unpressKeysValue.get()) {
            KeyBinding.unPressAllKeys()
        }
    }
}
