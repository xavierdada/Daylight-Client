package net.aspw.client.features.module.impl.visual

import net.aspw.client.Launch
import net.aspw.client.event.EventTarget
import net.aspw.client.event.JumpEvent
import net.aspw.client.event.Render3DEvent
import net.aspw.client.event.StrafeEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.features.module.impl.combat.KillAura
import net.aspw.client.features.module.impl.player.LegitScaffold
import net.aspw.client.features.module.impl.player.Scaffold
import net.aspw.client.utils.RotationUtils
import net.aspw.client.value.BoolValue

@ModuleInfo(
    name = "SilentRotations",
    spacedName = "Silent Rotations",
    category = ModuleCategory.VISUAL,
    forceNoSound = true
)
class SilentRotations : Module() {

    val customStrafe = BoolValue("CustomStrafing", true)

    var rotating = false

    /**
     * 搭路时是否已经"关掉 look 把鼠标还回去"。
     *
     * 只做一次，见 [onRender3D]：`disableLook()` 会把 cameraYaw/cameraPitch 写回玩家朝向，
     * 而那两个值在本模块不显示瞄准时**不再累积鼠标增量**，所以每帧调它 = 每帧把冻结的角度
     * 按回鼠标刚转出来的方向上 —— 表现就是"鼠标完全不能转头"。
     */
    private var scaffoldLookReleased = false

    // TASK(SilentRotations-never-touch-camera):本模块**不再**碰 RotationUtils 的 look 开关，也不再写玩家朝向。
    //
    // 原设计:onEnable -> enableLook()，而 RotationUtils.overrideMouse() 在 perspectiveToggled == true 时
    // 会吞掉原版鼠标处理(鼠标增量只累加到 cameraYaw/cameraPitch,等别人写回玩家朝向)。只要有任何一条分支
    // 忘了写回、或者开关被打开却没人关，"鼠标就完全移不动视角" —— 这就是反反复复出现的那个 bug，
    // 而且每次只修一半就会在"打架"和"完全不能动"两个极端之间来回跳。
    //
    // 现在本模块只做"静默"这件事:瞄准照旧由 RotationUtils/MixinEntityPlayerSP 塞进**出站包**
    // (那条链路与 perspectiveToggled 无关)，玩家视角永远由鼠标独家驱动。
    override fun onEnable() {
        rotating = false
    }

    override fun onDisable() {
        rotating = false
    }

    @EventTarget
    fun onStrafe(event: StrafeEvent) {
        if (RotationUtils.targetRotation != null) {
            rotating = true
            if (!customStrafe.get()) {
                if (!Launch.moduleManager.getModule(LegitScaffold::class.java)?.state!! && !Launch.moduleManager.getModule(
                        Scaffold::class.java
                    )?.state!!
                )
                    event.yaw = RotationUtils.targetRotation?.yaw!!
            }
        }
    }

    @EventTarget
    fun onJump(event: JumpEvent) {
        if (!customStrafe.get() && Launch.moduleManager.getModule(KillAura::class.java)?.state!! && Launch.moduleManager.getModule(
                KillAura::class.java
            )?.currentTarget != null
        )
            mc.thePlayer.isSprinting = false
        if (RotationUtils.targetRotation != null) {
            rotating = true
            if (!customStrafe.get()) {
                if (!Launch.moduleManager.getModule(LegitScaffold::class.java)?.state!! && !Launch.moduleManager.getModule(
                        Scaffold::class.java
                    )?.state!!
                )
                    event.yaw = RotationUtils.targetRotation?.yaw!!
            }
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        // 视角永远归鼠标:这里直接返回 —— 不写 rotationYaw/Pitch，也不动 look 开关
        // (原因见 onEnable 处的 TASK 注释)。下面那段"把瞄准显示成玩家视角"的旧逻辑保留作参考，
        // 但不会再执行(编译器会对此给一条 unreachable 警告，属预期)。
        return
    }

    /** 保留的旧实现（不再调用），留作以后真要做"显示瞄准"时的参考。 */
    @Suppress("unused", "UNREACHABLE_CODE")
    private fun displayAimLegacy(event: Render3DEvent) {
        val scaffoldActiveAnchor = 0
        if (scaffoldActiveAnchor != 0) return
        // TASK(SilentRotations-scaffold-guard):搭路时不显示静默朝向。
        //
        // 本模块的 onStrafe / onJump 早就用 `!Scaffold.state && !LegitScaffold.state` 避免干扰搭路，
        // 但这一处(真正把 RotationUtils.targetRotation 写进 mc.thePlayer.rotationYaw/Pitch 的地方)
        // 漏了同样的判断。而 Scaffold 的朝向是**纯静默**的 —— 上游 LeaderLite 根本没有本模块，
        // 玩家视角永远不动，所以那边看不到任何转头。这里漏掉判断的后果:
        //   Snap 模式在"没有可点支撑"的 tick 会算出 ±180 的朝向(见 Scaffold 的 snapForward 分支),
        //   于是每贴到边缘就变成疯狂转头。
        // 补上判断后，显示层完全交还给鼠标视角，而**包层一个字节都不变**（Scaffold 与上游仍然一致）。
        val scaffoldActive = Launch.moduleManager.getModule(Scaffold::class.java)?.state == true ||
                Launch.moduleManager.getModule(LegitScaffold::class.java)?.state == true

        if (scaffoldActive) {
            // TASK(SilentRotations-disableLook-while-scaffolding):搭路时不显示瞄准,**并且必须把 look 关掉**。
            //
            // overrideMouse() 在 perspectiveToggled == true 时会 **吞掉原版鼠标处理**
            // (`if (!perspectiveToggled) return true;` 之后自己 mouseXYChange() 并只把增量累加到
            // cameraYaw/cameraPitch),指望有人再把它写回玩家朝向。搭路时这一处被跳过(不显示瞄准),
            // 于是"鼠标被吞 + 没人写回" = 动鼠标镜头完全不动 —— 就是玩家说的"我发移动镜头"。
            //
            // disableLook() 同时做两件事:把 cameraYaw/cameraPitch 写回玩家朝向(那正是鼠标累出来的
            // 位置),并复位 perspectiveToggled -> 原版鼠标处理立刻恢复。之后搭路结束、本模块重新显示
            // 瞄准时,下面的 `if (!perspectiveToggled) enableLook()` 会重新抓一次相机,衔接是平滑的。
            if (!scaffoldLookReleased) {
                scaffoldLookReleased = true
                if (RotationUtils.perspectiveToggled) {
                    RotationUtils.disableLook()
                }
                rotating = false
            }
            return
        }
        scaffoldLookReleased = false

        if (mc.thePlayer == null || RotationUtils.targetRotation == null) {
            // TASK(SilentRotations-restore-once):回正**一次**就够，绝不能每帧回正。
            //
            // cameraYaw / cameraPitch 是"启用静默那一刻记下的快门视角"，**不是鼠标当前位置**。
            // 而 targetRotation 是间歇性的(Scaffold 只在需要转向/放置的那些 tick 写它，其余 tick 为 null)，
            // 所以原来这个分支会在"没有瞄准"的每一帧把视角按回那个旧角度 —— 鼠标刚转就被按回去，
            // 表现就是"根本无法指向 / 转头和鼠标打架"。
            // 现在只在确实显示过瞄准(rotating)之后回正一次，之后一帧都不碰视角，鼠标完全说了算。
            if (rotating) {
                rotating = false
                mc.thePlayer.prevRotationYaw = RotationUtils.prevCameraYaw
                mc.thePlayer.prevRotationPitch = RotationUtils.prevCameraPitch
                mc.thePlayer.rotationYaw = RotationUtils.cameraYaw
                mc.thePlayer.rotationPitch = RotationUtils.cameraPitch
            }
            return
        }

        if (!RotationUtils.perspectiveToggled)
            RotationUtils.enableLook()

        rotating = true
        mc.thePlayer.rotationYaw = RotationUtils.targetRotation?.yaw!!
        mc.thePlayer.rotationPitch = RotationUtils.targetRotation?.pitch!!
    }
}