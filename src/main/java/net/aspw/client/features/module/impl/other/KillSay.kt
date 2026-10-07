package net.aspw.client.features.module.impl.combat

import net.aspw.client.event.EventTarget
import net.aspw.client.event.AttackEvent
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.value.ListValue
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.network.play.client.C01PacketChatMessage
import java.util.Random

@ModuleInfo(
    name = "KillSay",
    spacedName = "Kill Say",
    category = ModuleCategory.COMBAT
)
class KillSay : Module() {

    private var targetPlayer: EntityPlayer? = null
    private val random = Random()

    // 🌟 模式选择设置
    val modeValue = ListValue(
        "Mode",
        arrayOf("Bratty", "Yandere", "Pervert", "Normal"),
        "Bratty"
    )

    // ================= 语录库 =================

    // 1. 雌小鬼 (Bratty) - 傲慢、嘲讽、猫娘感
    private val brattyMessages = arrayOf(
        "杂鱼杂鱼~%target%连本喵的汗毛都碰不到，太弱了吧喵！",
        "喵呜？这就倒下了？%target%你的客户端是捡来的吗？嘻嘻♡",
        "弱爆了！%target%就像笨老鼠一样被本喵单手拿捏了呢，真可怜~",
        "哎呀呀，%target%躺在地上的样子真狼狈呢~把你打爆的是Daylight喵！",
        "反作弊也是杂鱼，%target%也是杂鱼，全都乖乖被本喵踩在脚下吧！",
        "嘻嘻，一点挑战性都没有呢！%target%你到底在瞄准哪里呀笨蛋~"
    )

    // 2. 病娇 (Yandere) - 偏执、疯狂的爱意
    private val yandereMessages = arrayOf(
        "终于...%target%终于永远属于我了呢...呵呵呵，再也不会逃跑了哦♡",
        "看到了吗%target%？这就是背叛我的下场。你的血，好温暖...",
        "为什么要躲开我呢%target%？把你杀掉的话，我们就能永远在一起了吧？",
        "刚才%target%是在看别人吗？绝对不行哦。只有死人，才不会花心呢...",
        "坏孩子%target%需要被惩罚。挖出你的心脏，就能感受到我的爱意了吧？",
        "只要把%target%做成标本，你的眼中就只会倒映出我一个人的身影了..."
    )

    // 3. 变态 (Pervert) - 极度兴奋、令人窒息的发言
    private val pervertMessages = arrayOf(
        "呼哈...呼哈...%target%死掉的声音，真是太棒了！请再多发出一点吧！",
        "哦呼！%target%倒下的姿势太诱人了，趁现在还是热的...嘿嘿嘿...",
        "看到了哦，%target%满身是伤的样子，简直让人兴奋得受不了啊！",
        "来吧%target%，让我好好‘疼爱’一下你渐渐冰冷的身体...",
        "哈啊...哈啊...沾满了%target%气味的刀刃，好想舔一口啊...",
        "求求你再挣扎一下吧%target%！你痛苦的表情是我最棒的下酒菜呢！"
    )

    // 4. 常规 (Normal) - 极简嘲讽，适合严肃的 HvH
    private val normalMessages = arrayOf(
        "Ez kill! %target% 被轻松带走。",
        "%target% 你的技术就这？回去再练练吧。",
        "感谢 %target% 送的人头，随手收下。",
        "这就是 Vanilla 玩家吗？%target% 你简直不堪一击。",
        "别挣扎了 %target%，遇到我算你倒霉。",
        "Ping 再低也救不了你，%target% 打得像个 Bot。"
    )

    // ==========================================

    override fun onEnable() {
        targetPlayer = null
    }

    override fun onDisable() {
        targetPlayer = null
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        if (mc?.thePlayer == null) return
        val entity = event.targetEntity
        if (entity is EntityPlayer && entity != mc.thePlayer) {
            targetPlayer = entity
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        // 🛡️ 启动安全防崩锁
        if (mc?.thePlayer == null || mc?.theWorld == null || mc?.netHandler == null) return

        val target = targetPlayer ?: return

        // 死亡判定
        if (target.health <= 0f || target.deathTime > 0 || target.isDead) {
            
            // 根据当前选中的模式匹配语录库
            val currentArray = when (modeValue.get().lowercase()) {
                "bratty" -> brattyMessages
                "yandere" -> yandereMessages
                "pervert" -> pervertMessages
                else -> normalMessages
            }

            val rawMsg = currentArray[random.nextInt(currentArray.size)]
            
            // 🛡️ 正则强效过滤玩家名字里的 § 颜色符号，防止触发反作弊格式校验
            val targetName = target.name?.replace(Regex("§."), "") ?: "Unknown"
            
            // 拼接最终消息，加入 Daylight 客户端后缀标识
            var finalMsg = rawMsg.replace("%target%", targetName) + " [Daylight]"

            // 🛡️ 严格死守 100 字符限制
            // Minecraft 1.8.9 规定发送超过 100 字符的聊天包会被服务器 Kick。安全第一！
            if (finalMsg.length > 100) {
                finalMsg = finalMsg.substring(0, 99)
            }

            // 🚀 终极杀招：直接往最底层网络队列塞 C01 聊天包！
            mc.netHandler.addToSendQueue(C01PacketChatMessage(finalMsg))
            
            // 重置目标，防止重复刷屏
            targetPlayer = null
        }
    }
}
