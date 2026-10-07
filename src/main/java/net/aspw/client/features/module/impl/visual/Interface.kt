package net.aspw.client.features.module.impl.visual

import net.aspw.client.Launch
import net.aspw.client.event.*
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.features.module.impl.combat.KillAura
import net.aspw.client.features.module.impl.combat.KillAuraRecode
import net.aspw.client.features.module.impl.combat.TPAura
import net.aspw.client.features.module.impl.player.LegitScaffold
import net.aspw.client.features.module.impl.player.Scaffold
import net.aspw.client.utils.AnimationUtils
import net.aspw.client.utils.MovementUtils
import net.aspw.client.utils.render.Glass
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.ColorValue
import net.aspw.client.value.FloatValue
import net.aspw.client.value.ListValue
import net.aspw.client.value.TextValue
import net.aspw.client.visual.client.clickgui.dropdown.ClickGui
import net.aspw.client.visual.client.clickgui.smooth.SmoothClickGui
import net.aspw.client.visual.client.clickgui.tab.NewUi
import net.aspw.client.visual.font.UiFont
import net.aspw.client.visual.font.semi.Fonts
import net.aspw.client.visual.font.smooth.FontLoaders
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C14PacketTabComplete
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.server.S19PacketEntityStatus
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S3APacketTabComplete
import net.minecraft.network.play.server.S45PacketTitle
import net.minecraft.util.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.abs
import kotlin.math.max

@ModuleInfo(name = "Interface", category = ModuleCategory.VISUAL, array = false)
class Interface : Module() {
    private val watermarkValue = BoolValue("WaterMark", true)
    private val clientNameValue = TextValue("ClientName", "Daylight") { watermarkValue.get() }
    
    private val watermarkRadiusValue = FloatValue("Watermark-Radius", 3F, 0F, 10F) { watermarkValue.get() }
    private val watermarkThicknessValue = FloatValue("Watermark-Thickness", 1.5F, 0.5F, 5.0F) { watermarkValue.get() }
    private val watermarkXValue = FloatValue("WatermarkX", 0F, -200F, 200F) { watermarkValue.get() }
    private val watermarkYValue = FloatValue("WatermarkY", 0F, -200F, 200F) { watermarkValue.get() }
    private val watermarkScaleValue = FloatValue("WatermarkScale", 1F, 0.5F, 2F) { watermarkValue.get() }
    
    private val sessionInfoValue = BoolValue("SessionInfo", true)
    private val sessionInfoRadiusValue = FloatValue("Session-Radius", 3F, 0F, 10F) { sessionInfoValue.get() }
    private val sessionInfoThicknessValue = FloatValue("Session-Thickness", 1.5F, 0.5F, 5.0F) { sessionInfoValue.get() }
    private val sessionInfoXValue = FloatValue("SessionInfoX", 0F, -200F, 200F) { sessionInfoValue.get() }
    private val sessionInfoYValue = FloatValue("SessionInfoY", 0F, -200F, 200F) { sessionInfoValue.get() }
    private val sessionInfoScaleValue = FloatValue("SessionInfoScale", 1F, 0.5F, 2F) { sessionInfoValue.get() }
    
    private val arrayListValue = BoolValue("ArrayList", true)
    private val arrayListLineValue = BoolValue("ArrayListLine", true) { arrayListValue.get() }
    private val arrayListBackGroundValue = BoolValue("ArrayList-BackGround", true) { arrayListValue.get() }
    private val arrayListBgAlphaValue = FloatValue("ArrayList-BgAlpha", 120f, 0f, 255f) { arrayListValue.get() && arrayListBackGroundValue.get() }
    private val arrayListRadiusValue = FloatValue("ArrayList-Radius", 4.5f, 0f, 10f) { arrayListValue.get() }
    private val arrayListColorFlowValue = ListValue("ArrayList-ColorFlow", arrayOf("TopToBottom", "BottomToTop"), "TopToBottom") { arrayListValue.get() }
    private val arrayListSpeedValue = FloatValue("ArrayList-AnimationSpeed", 0.3F, 0F, 0.6F) { arrayListValue.get() }
    private val arrayListXValue = FloatValue("ArrayListX", 0F, -200F, 200F) { arrayListValue.get() }
    private val arrayListYValue = FloatValue("ArrayListY", 0F, -200F, 200F) { arrayListValue.get() }
    private val arrayListScaleValue = FloatValue("ArrayListScale", 1F, 0.5F, 2F) { arrayListValue.get() }
    
    private val targetHudValue = BoolValue("TargetHud", true)
    private val targetHudModeValue = ListValue("TargetHud-Mode", arrayOf("Zoom", "Fade", "BounceZoom"), "BounceZoom") { targetHudValue.get() }
    private val targetHudRadiusValue = FloatValue("TargetHud-Radius", 8F, 0F, 20F) { targetHudValue.get() }
    private val targetHudXPosValue = FloatValue("TargetHud-XPos", 0F, -300F, 300F) { targetHudValue.get() }
    private val targetHudYPosValue = FloatValue("TargetHud-YPos", 0F, -300F, 300F) { targetHudValue.get() }
    /** User scale of the TargetHUD, applied around the top-left corner of the box. */
    private val targetHudScaleValue = FloatValue("TargetHudScale", 1F, 0.5F, 2F) { targetHudValue.get() }
    
    private val cpsDisplayValue = BoolValue("CPS-Display", true)
    private val cpsOnlyModuleValue = BoolValue("CPS-OnlyModule", false) { cpsDisplayValue.get() }
    private val cpsOnlyClickValue = BoolValue("CPS-OnlyClick", false) { cpsDisplayValue.get() }
    private val cpsBgAlphaValue = FloatValue("CPS-BgAlpha", 120f, 0f, 255f) { cpsDisplayValue.get() }
    private val cpsRadiusValue = FloatValue("CPS-Radius", 4f, 0f, 10f) { cpsDisplayValue.get() }
    private val cpsXValue = FloatValue("CPSX", 0F, -200F, 200F) { cpsDisplayValue.get() }
    private val cpsYValue = FloatValue("CPSY", 0F, -200F, 200F) { cpsDisplayValue.get() }
    private val cpsScaleValue = FloatValue("CPSScale", 1F, 0.5F, 2F) { cpsDisplayValue.get() }

    /**
     * 旧的自带字体开关。HUD 文字现在统一走 [UiFont]：它按 Gui 模块的 `PackFont` 决定用
     * 材质包（原版）字体还是自带 TTF，而 `PackFont` 关闭时一律用自带 TTF（保持原观感），
     * 所以这个开关实际上已经不起作用了。保留下来只是为了不破坏已保存的配置。
     */
    private val cFontValue = BoolValue("C-Font", true)
    val csgoCrosshairValue = BoolValue("CSGO-Crosshair", true)
    val scaffoldHighlight = BoolValue("ScaffoldHighlight", true)
    private val motionVisualsValue = BoolValue("MotionVisuals", true)
    val itemVisualSpoofsValue = BoolValue("ItemVisualSpoof", true)
    val noAchievements = BoolValue("No-Achievements", true)
    val animHotbarValue = BoolValue("Hotbar-Animation", false)
    private val animHotbarSpeedValue = FloatValue("Hotbar-AnimationSpeed", 0.03F, 0.01F, 0.2F) { animHotbarValue.get() }
    val blackHotbarValue = BoolValue("Black-Hotbar", false)
    private val noInvClose = BoolValue("NoInvClose", true)
    private val noTitle = BoolValue("NoTitle", false)
    private val antiTabComplete = BoolValue("AntiTabComplete", false)
    val customFov = BoolValue("CustomFov", false)
    val customFovModifier = FloatValue("Fov", 1.45F, 1F, 1.5F) { customFov.get() }
    val chatRectValue = BoolValue("ChatRect", true)
    val chatAnimationValue = BoolValue("Chat-Animation", true)
    val chatAnimationSpeedValue = FloatValue("Chat-AnimationSpeed", 0.06F, 0.01F, 0.5F) { chatAnimationValue.get() }
    private val toggleMessageValue = BoolValue("Toggle-Notification", false)
    private val toggleSoundValue = ListValue("Toggle-Sound", arrayOf("None", "Default", "Custom"), "None")
    val flagSoundValue = BoolValue("Pop-Sound", true)
    val swingSoundValue = BoolValue("Swing-Sound", false)

    /**
     * 单一 HUD 强调色：**跟随 ClickGUI 的颜色**（Gui 模块的 EnabledColor）。
     *
     * 以前这里写死 `Color(255, 236, 140)`（黄色），和 GUI 的配色对不上；现在 GUI 里改一次
     * EnabledColor，watermark 强调色、session info 边框等会一起变（用 getter 每次读，所以是实时的）。
     * 用全限定名是为了避免和本文件的 `net.minecraft.client.gui.Gui`（原版绘制类）撞名。
     */
    private val hudAccent: Color
        get() = Color(
            Launch.moduleManager.getModule(net.aspw.client.features.module.impl.visual.Gui::class.java)
                ?.enabledColorValue?.get() ?: Color(242, 167, 157).rgb
        )

    /**
     * ArrayList 的统一纯色。它原本是渐变起始色，名字保持不变，这样已保存的配置仍然能读回。
     * 默认值改成和 GUI 一致的颜色；如果你之前存过 value.json，那里面的旧值会覆盖它。
     */
    private val arrayStartColorValue = ColorValue("ArrayStartColor", Color(242, 167, 157).rgb)

    private var hotBarX = 0F
    private var easingHealth = 0F
    private var lastTarget: EntityLivingBase? = null
    private val targetHudSpring = Spring()

    /**
     * Entrance progress of the four animated HUD elements, 0 (hidden, 6 px lower) .. 1 (shown).
     * Each one is eased towards "module on" once per frame and drives the alpha of its element.
     */
    private var watermarkAppear = 0f
    private var sessionInfoAppear = 0f
    private var targetHudAppear = 0f
    private var cpsAppear = 0f

    /** Near-black of the automatic contrast switch; pure black looks harsh on a HUD. */
    private val contrastDarkRgb = Color(20, 20, 24).rgb

    /** Backdrop luminance at or above which the HUD switches to [contrastDarkRgb]. */
    private val contrastLuminanceThreshold = 0.6f

    /** Frames between two read-backs. glReadPixels stalls the GPU pipeline, so it is not per frame. */
    private val contrastSampleInterval = 10

    /** Longest side of one read-back patch, in physical pixels. */
    private val maxLuminanceSampleSize = 32

    /** Long-lived read-back buffer, so no ByteBuffer is allocated per sample. */
    private val luminanceBuffer = BufferUtils.createByteBuffer(maxLuminanceSampleSize * maxLuminanceSampleSize * 4)

    /**
     * Average luminance behind the HUD, 0..1, or -1 while nothing has been sampled yet or the
     * read-back failed. Refreshed every [contrastSampleInterval] frames from the ArrayList box
     * and reused by the watermark and the session panel.
     */
    private var backdropLuminance = -1f

    /** Frames left until the next read-back; 0 means "sample on this frame". */
    private var contrastSampleTimer = 0

    private var sessionStartTime = System.currentTimeMillis()
    private var sessionKills = 0
    private val damageCounts = HashMap<String, Int>()
    private var yourDarling = "None"

    private val manualLeftList = mutableListOf<Long>()
    private val manualRightList = mutableListOf<Long>()
    private val packetSwingList = mutableListOf<Long>()
    private val packetAttackList = mutableListOf<Long>()
    private val packetBlockList = mutableListOf<Long>()
    private val packetInteractList = mutableListOf<Long>()
    private var wasLeftDown = false
    private var wasRightDown = false

    override fun onEnable() {
        sessionStartTime = System.currentTimeMillis()
        sessionKills = 0
        damageCounts.clear()
        yourDarling = "None"
        targetHudSpring.reset()
        lastTarget = null
        
        manualLeftList.clear()
        manualRightList.clear()
        packetSwingList.clear()
        packetAttackList.clear()
        packetBlockList.clear()
        packetInteractList.clear()
        wasLeftDown = false
        wasRightDown = false

        // The contrast cache belongs to one session of this module: drop it so the first frame
        // samples again instead of starting from a stale backdrop.
        backdropLuminance = -1f
        contrastSampleTimer = 0

        super.onEnable()
    }

    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        val sr = ScaledResolution(mc)

        // Entrance animation for the four HUD panels. They are eased towards "visible" every
        // frame, even while hidden, so switching a panel off fades it out instead of popping it.
        watermarkAppear = AnimationUtils.animate(if (watermarkValue.get()) 1f else 0f, watermarkAppear, 0.2f)
        sessionInfoAppear = AnimationUtils.animate(if (sessionInfoValue.get() && mc.thePlayer != null) 1f else 0f, sessionInfoAppear, 0.2f)
        cpsAppear = AnimationUtils.animate(if (cpsDisplayValue.get()) 1f else 0f, cpsAppear, 0.2f)
        // The TargetHud is only on screen while its spring is open, so it inherits that state
        // (one frame behind) instead of the raw module switch alone.
        targetHudAppear = AnimationUtils.animate(
            if (targetHudValue.get() && targetHudSpring.value > 0.01f) 1f else 0f,
            targetHudAppear,
            0.2f
        )

        val leftDown = org.lwjgl.input.Mouse.isButtonDown(0)
        if (leftDown && !wasLeftDown) manualLeftList.add(System.currentTimeMillis())
        wasLeftDown = leftDown

        val rightDown = org.lwjgl.input.Mouse.isButtonDown(1)
        if (rightDown && !wasRightDown) manualRightList.add(System.currentTimeMillis())
        wasRightDown = rightDown

        if (cpsDisplayValue.get()) {
            val time = System.currentTimeMillis()
            manualLeftList.removeAll { time - it > 1000L }
            manualRightList.removeAll { time - it > 1000L }
            packetSwingList.removeAll { time - it > 1000L }
            packetAttackList.removeAll { time - it > 1000L }
            packetBlockList.removeAll { time - it > 1000L }
            packetInteractList.removeAll { time - it > 1000L }

            val mLeft = manualLeftList.size
            val mRight = manualRightList.size

            val pLeft = maxOf(packetSwingList.size, packetAttackList.size)
            val pRight = maxOf(packetBlockList.size, packetInteractList.size)

            val moduleLeft = maxOf(0, pLeft - mLeft)
            val moduleRight = maxOf(0, pRight - mRight)

            val finalLeft = if (cpsOnlyModuleValue.get() && !cpsOnlyClickValue.get()) moduleLeft
                            else if (cpsOnlyClickValue.get() && !cpsOnlyModuleValue.get()) mLeft
                            else mLeft + moduleLeft

            val finalRight = if (cpsOnlyModuleValue.get() && !cpsOnlyClickValue.get()) moduleRight
                             else if (cpsOnlyClickValue.get() && !cpsOnlyModuleValue.get()) mRight
                             else mRight + moduleRight

            val cpsText = "MLB: $finalLeft | MRB: $finalRight"
            val textWidth = UiFont.width(FontLoaders.SF20, cpsText)
            val textHeight = 9f
            val paddingX = 6f
            val paddingY = 4f

            val rectWidth = textWidth + paddingX * 2f
            val rectHeight = textHeight + paddingY * 2f
            val cpsScale = cpsScaleValue.get()
            val xPos = (sr.scaledWidth / 2f) - (rectWidth / 2f) + cpsXValue.get()
            // The TargetHUD is drawn after this chip and its background would paint over the
            // CPS text, so drop the chip below that box instead of stacking the two. That box is
            // scaled around its top-left corner, so its bottom edge grows with the scale value.
            val cpsDefaultY = sr.scaledHeight - 75f
            val targetHudBottomY = (sr.scaledHeight / 2f) + 20f + targetHudYPosValue.get() + 55f * targetHudScaleValue.get()
            // The chip slides up from 6 px below its final spot while it fades in. The user
            // offset belongs to the resting position, so it is clamped on screen like the base,
            // and the clamp uses the scaled height so a magnified chip cannot hang off the edge.
            val yPos = (
                maxOf(
                    cpsDefaultY,
                    if (targetHudValue.get()) targetHudBottomY + 4f else cpsDefaultY
                ) + cpsYValue.get()
            ).coerceAtMost(sr.scaledHeight - rectHeight * cpsScale - 2f) + (1f - cpsAppear) * 6f

            if (cpsAppear >= 0.01f) {
                // GlStateManager tints the text (both font renderers rewrite a zero alpha byte
                // to fully opaque, so the wrapper and the ARGB alpha below cover each of them),
                // while the panel keeps its own bg alpha, scaled by the entrance progress.
                // The user scale is the outermost transform and anchors the chip's top-left
                // corner, so the entrance offset above stays a plain translation.
                GlStateManager.pushMatrix()
                try {
                    GlStateManager.translate(xPos, yPos, 0f)
                    GlStateManager.scale(cpsScale, cpsScale, 1f)
                    GlStateManager.translate(-xPos, -yPos, 0f)

                    GlStateManager.color(1f, 1f, 1f, cpsAppear)
                    val bgColor = withAlpha(Color(248, 248, 250).rgb, (cpsBgAlphaValue.get() * cpsAppear).toInt())
                    Glass.panel(
                        xPos, yPos, xPos + rectWidth, yPos + rectHeight, cpsRadiusValue.get(),
                        bgColor and 0xFFFFFF, (bgColor ushr 24) and 0xFF, 0f, 23
                    )

                    val textColor = withAlpha(Color(28, 28, 34).rgb, fadeAlpha(cpsAppear))
                    UiFont.draw(FontLoaders.SF20, cpsText, xPos + paddingX, yPos + paddingY, textColor)
                } finally {
                    GlStateManager.popMatrix()
                }
                GlStateManager.resetColor()
                GlStateManager.color(1f, 1f, 1f, 1f)
            }
        }

        var watermarkBottomY = 2f 

        if (watermarkValue.get() && watermarkAppear >= 0.01f) {
            val showName = clientNameValue.get()
            // Automatic contrast: on a bright picture the accent outline would wash out, so the
            // border (and the text, which is already dark) drops to the near-black as well. A
            // failed sample never counts as bright, which keeps the normal colours.
            val watermarkAccentRgb = if (isBackdropBright) contrastDarkRgb else hudAccent.rgb
            val borderAccentColor = withAlpha(watermarkAccentRgb, fadeAlpha(watermarkAppear))
            val fixedTextColor = withAlpha(if (isBackdropBright) contrastDarkRgb else Color(28, 28, 34).rgb, fadeAlpha(watermarkAppear))
            val fixedBgColor = withAlpha(Color(248, 248, 250).rgb, (200 * watermarkAppear).toInt())
            val textWidth = UiFont.width(FontLoaders.SF20, showName)
            val textHeight = 9f
            val paddingX = 5f
            val paddingY = 4f
            val scale = watermarkScaleValue.get()
            val x = 2f + watermarkXValue.get()
            val y = 2f + watermarkYValue.get()
            val rectWidth = textWidth + paddingX * 2
            val rectHeight = textHeight + paddingY * 2
            // The session panel hangs off this value, so it follows the user offset and the
            // scaled height of the box, but not its entrance animation.
            watermarkBottomY = y + rectHeight * scale
            // Only the drawing slides up; watermarkBottomY stays put so the session panel below
            // does not ride along with the watermark animation.
            val drawY = y + (1f - watermarkAppear) * 6f
            // User scale, anchored on the top-left corner of the panel.
            GlStateManager.pushMatrix()
            try {
                GlStateManager.translate(x, drawY, 0f)
                GlStateManager.scale(scale, scale, 1f)
                GlStateManager.translate(-x, -drawY, 0f)
                GlStateManager.color(1f, 1f, 1f, watermarkAppear)
                Glass.panel(
                    x, drawY, x + rectWidth, drawY + rectHeight, watermarkRadiusValue.get(),
                    fixedBgColor and 0xFFFFFF, (fixedBgColor ushr 24) and 0xFF, 0f, 31
                )
                drawSmoothOutlineRect(x, drawY, x + rectWidth, drawY + rectHeight, watermarkRadiusValue.get(), borderAccentColor, watermarkThicknessValue.get())
                UiFont.draw(FontLoaders.SF20, showName, x + paddingX, drawY + paddingY, fixedTextColor)
            } finally {
                GlStateManager.popMatrix()
            }
            GlStateManager.resetColor()
            GlStateManager.color(1f, 1f, 1f, 1f)
        }

        if (sessionInfoValue.get() && mc.thePlayer != null && sessionInfoAppear >= 0.01f) {
            // Same automatic contrast as the watermark: text and outline share one decision.
            val fixedTextColor = withAlpha(if (isBackdropBright) contrastDarkRgb else Color(28, 28, 34).rgb, fadeAlpha(sessionInfoAppear))
            val fixedBgColor = withAlpha(Color(248, 248, 250).rgb, (200 * sessionInfoAppear).toInt())
            val scale = sessionInfoScaleValue.get()
            val boxWidth = 145f
            val boxHeight = 50f
            val boxX = 2f + sessionInfoXValue.get()
            // The whole panel (background, border, avatar and text) slides up 6 px while fading;
            // the user offset is added to that resting position.
            val boxY = watermarkBottomY + 5f + (1f - sessionInfoAppear) * 6f + sessionInfoYValue.get()
            // User scale, anchored on the top-left corner of the panel.
            GlStateManager.pushMatrix()
            try {
                GlStateManager.translate(boxX, boxY, 0f)
                GlStateManager.scale(scale, scale, 1f)
                GlStateManager.translate(-boxX, -boxY, 0f)
                GlStateManager.color(1f, 1f, 1f, sessionInfoAppear)
                Glass.panel(
                    boxX, boxY, boxX + boxWidth, boxY + boxHeight, sessionInfoRadiusValue.get(),
                    fixedBgColor and 0xFFFFFF, (fixedBgColor ushr 24) and 0xFF, 0f, 37
                )
                val boxBorderColor = withAlpha(if (isBackdropBright) contrastDarkRgb else hudAccent.rgb, fadeAlpha(sessionInfoAppear))
                drawSmoothOutlineRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, sessionInfoRadiusValue.get(), boxBorderColor, sessionInfoThicknessValue.get())
                val titleText = "${mc.thePlayer.name} Info"
                val titleWidth = UiFont.width(FontLoaders.SF16, titleText)
                val titleX = boxX + (boxWidth - titleWidth) / 2f
                val titleY = boxY + 4f
                UiFont.draw(FontLoaders.SF16, titleText, titleX, titleY, fixedTextColor)
                val avatarSize = boxHeight - 8f
                val avatarX = boxX + 5f
                val avatarY = boxY + 4f
                if (mc.netHandler?.getPlayerInfo(mc.thePlayer.uniqueID) != null) {
                    val skinLocation = mc.netHandler.getPlayerInfo(mc.thePlayer.uniqueID).locationSkin
                    if (skinLocation != null) {
                        drawCustomHead(skinLocation, avatarX, avatarY, avatarSize, avatarSize, sessionInfoAppear)
                    }
                }
                val dataLeftX = avatarX + avatarSize + 7f
                val durationMs = System.currentTimeMillis() - sessionStartTime
                val totalSec = durationMs / 1000
                val h = totalSec / 3600
                val m = (totalSec % 3600) / 60
                val s = totalSec % 60
                val playedStr = if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
                val line2Y = boxY + 16f
                UiFont.draw(FontLoaders.SF16, "Played: $playedStr", dataLeftX, line2Y, fixedTextColor)
                val line3Y = boxY + 27f
                UiFont.draw(FontLoaders.SF16, "Kills: $sessionKills", dataLeftX, line3Y, fixedTextColor)
                val line4Y = boxY + 38f
                UiFont.draw(FontLoaders.SF16, "YourDarling: $yourDarling", dataLeftX, line4Y, fixedTextColor)
            } finally {
                GlStateManager.popMatrix()
            }
            GlStateManager.resetColor()
            GlStateManager.color(1f, 1f, 1f, 1f)
        }

        if (arrayListValue.get()) {
            val delta = RenderUtils.deltaTime
            val spacing = 12f

            // Every row shares this one flat colour. It comes from the colour picker as ARGB
            // and the rows fade with their own alpha, so only the RGB part is kept here; the
            // picker alpha would break the slide fade.
            val arrayRgb = arrayStartColorValue.get() and 0xFFFFFF

            // Rows are right-aligned and split in two colours, so name and value (the module
            // tag, e.g. "120ms") have to be measured separately.
            fun widthOfText(text: String): Float = UiFont.width(FontLoaders.SF20, text)

            fun rowWidthOf(module: Module): Float = widthOfText(getModName(module)) + widthOfText(getModTag(module))

            /** Reveal width of a row: 0 keeps it hidden, rowWidth + padding shows it fully. */
            fun targetSlideOf(module: Module): Float = rowWidthOf(module) + 8f

            val sortedAll = Launch.moduleManager.modules.filter { it.array }.sortedByDescending { rowWidthOf(it) }
            
            var currentTargetY = 2f + arrayListYValue.get()
            for (module in sortedAll) {
                val targetSlide = targetSlideOf(module)
                
                if (module.state) {
                    module.slide += arrayListSpeedValue.get() * delta * 4f 
                } else {
                    module.slide -= arrayListSpeedValue.get() * delta * 4f
                }
                module.slide = module.slide.coerceIn(0f, targetSlide)
                
                if (module.slide > 0f) {
                    if (module.state && module.slide <= arrayListSpeedValue.get() * delta * 8f) {
                        module.arrayY = currentTargetY 
                    }
                    // AnimationUtils.animate clamps its speed to 1 and deltaTime is in
                    // milliseconds, so the old "0.1f * delta" always clamped: rows snapped
                    // instead of easing. 0.18 per 16 ms frame keeps the push smooth and
                    // independent of the frame rate.
                    val rowEase = (0.18f * delta / 16f).coerceIn(0f, 1f)
                    if (abs(module.arrayY - currentTargetY) > 0.1f) {
                        module.arrayY = AnimationUtils.animate(currentTargetY, module.arrayY, rowEase)
                    } else {
                        module.arrayY = currentTargetY
                    }
                    currentTargetY += spacing
                }
            }
            
            val visibleModules = sortedAll.filter { it.slide > 0f }
            
            if (visibleModules.isNotEmpty()) {
                val barWidth = 2.5f
                val margin = 4f
                val isLineOn = arrayListLineValue.get()
                val valueRgb = Color(138, 140, 152).rgb
                // Every row ends on the same x: the screen edge minus a small margin, and
                // minus the accent bar when that bar is enabled.
                val lineRight = sr.scaledWidth.toFloat() - margin + arrayListXValue.get()
                val lineLeft = lineRight - barWidth
                val textRight = lineRight - (if (isLineOn) barWidth else 0f) - 2f
                val globalR = arrayListRadiusValue.get()
                val rowCount = visibleModules.size

                // User scale. The list is right-aligned, so it has no single left corner: the
                // shared right edge is used as the anchor. glScissor works in physical pixels and
                // ignores this matrix, and anchoring on lineRight leaves the scaled right edge
                // exactly on lineRight, so the clip rectangle below stays correct at every scale
                // and no row can be cut short by the scale.
                val scale = arrayListScaleValue.get()
                val anchorY = 2f + arrayListYValue.get()

                // Automatic contrast. The list covers the area the HUD text really sits on, so
                // its bounding box is the one region that gets sampled; the watermark and the
                // session panel reuse the cached result instead of reading back again. The read
                // stalls the GPU pipeline, hence the frame counter. The box is expressed in GUI
                // coordinates of the *scaled* list: the right edge is the anchor and does not
                // move, while the left edge and the height grow with the scale value.
                if (contrastSampleTimer > 0) {
                    contrastSampleTimer--
                } else {
                    contrastSampleTimer = contrastSampleInterval
                    val widestRow = visibleModules.maxOf { rowWidthOf(it) }
                    val boxRight = lineRight
                    val boxLeft = lineRight - scale * (widestRow + (if (isLineOn) barWidth else 0f) + 2f)
                    val boxTop = anchorY
                    val boxBottom = anchorY + scale * rowCount * spacing
                    backdropLuminance = sampleLuminance(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop)
                }
                // One decision per frame for every row, the bar and the manual shadow.
                val brightBackdrop = isBackdropBright

                GlStateManager.pushMatrix()
                try {
                    GlStateManager.translate(lineRight, anchorY, 0f)
                    GlStateManager.scale(scale, scale, 1f)
                    GlStateManager.translate(-lineRight, -anchorY, 0f)

                    // Clip background, bar and text to the right edge of the list, so a sliding
                    // row runs off that edge instead of crossing it.
                    val factor = sr.scaleFactor
                    glEnable(GL_SCISSOR_TEST)
                    glScissor(0, 0, (lineRight * factor).toInt().coerceAtLeast(0), mc.displayHeight.coerceAtLeast(0))

                    if (arrayListBackGroundValue.get()) {
                        val bgAlphaInt = arrayListBgAlphaValue.get().toInt().coerceIn(0, 255)
                        val bgColor = Color(248, 248, 250, bgAlphaInt).rgb

                        visibleModules.forEachIndexed { i, module ->
                            val prev = visibleModules.getOrNull(i - 1)
                            val next = visibleModules.getOrNull(i + 1)

                            val currLeft = textRight - rowWidthOf(module) + (targetSlideOf(module) - module.slide) - 4f
                            val prevLeft = prev?.let { textRight - rowWidthOf(it) + (targetSlideOf(it) - it.slide) - 4f } ?: Float.MAX_VALUE
                            val nextLeft = next?.let { textRight - rowWidthOf(it) + (targetSlideOf(it) - it.slide) - 4f } ?: Float.MAX_VALUE

                            val rTL = if (currLeft < prevLeft - 0.5f) globalR else 0f
                            val rBL = if (currLeft < nextLeft - 0.5f) globalR else 0f

                            val rTR = if (!isLineOn && i == 0) globalR else 0f
                            val rBR = if (!isLineOn && i == rowCount - 1) globalR else 0f

                            val currTop = module.arrayY
                            val currBot = module.arrayY + spacing

                            RenderUtils.customRounded(currLeft, currTop, textRight + (targetSlideOf(module) - module.slide) + 4f, currBot, rTL, rTR, rBR, rBL, bgColor)
                        }
                    }

                    if (isLineOn) {
                        GlStateManager.pushMatrix()
                        GlStateManager.disableTexture2D()
                        GlStateManager.enableBlend()
                        GlStateManager.disableAlpha()
                        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
                        GlStateManager.shadeModel(7425)

                        val tessellator = net.minecraft.client.renderer.Tessellator.getInstance()
                        val worldrenderer = tessellator.worldRenderer
                        worldrenderer.begin(7, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR)

                        visibleModules.forEachIndexed { _, module ->
                            // The bar carries the same flat colour as its row and fades out
                            // together with the row's text.
                            val rowRgb = if (brightBackdrop) contrastDarkRgb else arrayRgb
                            val rowAlpha = (module.slide / targetSlideOf(module)).coerceIn(0f, 1f)

                            val f1 = (rowRgb shr 16 and 0xFF) / 255f
                            val f2 = (rowRgb shr 8 and 0xFF) / 255f
                            val f3 = (rowRgb and 0xFF) / 255f

                            val f5 = f1
                            val f6 = f2
                            val f7 = f3

                            val currentY = module.arrayY.toDouble()
                            val nextY = (module.arrayY + spacing).toDouble()
                            val right = lineRight.toDouble()
                            val left = lineLeft.toDouble()

                            worldrenderer.pos(right, currentY, 0.0).color(f1, f2, f3, rowAlpha).endVertex()
                            worldrenderer.pos(left, currentY, 0.0).color(f1, f2, f3, rowAlpha).endVertex()
                            worldrenderer.pos(left, nextY, 0.0).color(f5, f6, f7, rowAlpha).endVertex()
                            worldrenderer.pos(right, nextY, 0.0).color(f5, f6, f7, rowAlpha).endVertex()
                        }

                        tessellator.draw()
                        GlStateManager.shadeModel(7424)
                        GlStateManager.disableBlend()
                        GlStateManager.enableAlpha()
                        GlStateManager.enableTexture2D()
                        GlStateManager.popMatrix()
                    }

                    visibleModules.forEachIndexed { _, module ->
                        val nameText = getModName(module)
                        val valueText = getModTag(module)
                        val nameWidth = widthOfText(nameText)
                        val rowWidth = rowWidthOf(module)
                        // 0 = row rests against the shared right edge, rowWidth + padding = the
                        // whole row has slid off it.
                        val offset = targetSlideOf(module) - module.slide

                        val alphaProgress = (module.slide / targetSlideOf(module)).coerceIn(0f, 1f)
                        // fadeAlpha instead of a bare 0..255 cast: an all-zero alpha byte would be
                        // rewritten to fully opaque by the font renderers and flash the row solid.
                        val textAlpha = fadeAlpha(alphaProgress)
                        // Every row uses the same flat colour, so the list no longer runs a
                        // gradient. On a bright picture that colour is dropped for the
                        // near-black, because light text has no contrast left there.
                        val rowRgb = if (brightBackdrop) contrastDarkRgb else arrayRgb
                        // The value half normally has its own grey; it has to follow the switch,
                        // otherwise it stays unreadable on the bright picture.
                        val rowValueRgb = if (brightBackdrop) contrastDarkRgb else valueRgb

                        val xPos = textRight - rowWidth + offset
                        val yPos = module.arrayY + 3.0f

                        // Manual drop shadow: every segment is drawn once in a dark colour offset by
                        // 0.5 / 0.5 before its real colour. drawStringWithShadow is not usable here,
                        // because it rewrites the colour alpha to 255 and a fading row would leave an
                        // opaque black ghost behind. Dark rows need the opposite shadow colour, or
                        // the offset copy would smear into the glyphs.
                        val shadowRgb = if (brightBackdrop) withAlpha(0xF0F0F0, textAlpha)
                                        else withAlpha(0x101010, textAlpha)

                        UiFont.draw(FontLoaders.SF20, nameText, xPos + 0.5f, yPos + 0.5f, shadowRgb)
                        UiFont.draw(FontLoaders.SF20, nameText, xPos, yPos, withAlpha(rowRgb, textAlpha))
                        if (valueText.isNotEmpty()) {
                            UiFont.draw(FontLoaders.SF20, valueText, xPos + nameWidth + 0.5f, yPos + 0.5f, shadowRgb)
                            UiFont.draw(FontLoaders.SF20, valueText, xPos + nameWidth, yPos, withAlpha(rowValueRgb, textAlpha))
                        }
                    }
                    glDisable(GL_SCISSOR_TEST)
                } finally {
                    GlStateManager.popMatrix()
                }
            }
            GlStateManager.resetColor()
        } else {
            // Without the list there is no box to sample, so the cached value is dropped instead
            // of being reused: the remaining panels fall back to their normal colours rather
            // than acting on a stale sample of a picture that may have changed since.
            backdropLuminance = -1f
            contrastSampleTimer = 0
        }

        if (targetHudValue.get()) {
            val killAura = Launch.moduleManager.getModule(KillAura::class.java)
            val tpAura = Launch.moduleManager.getModule(TPAura::class.java)
            val killAuraRecode = Launch.moduleManager.getModule(KillAuraRecode::class.java)
            
            val currentTarget = if (killAura?.state == true && killAura.currentTarget != null) killAura.currentTarget
                else if (tpAura?.state == true && tpAura.lastTarget != null) tpAura.lastTarget
                else if (killAuraRecode?.state == true && killAuraRecode.lastTarget != null) killAuraRecode.lastTarget
                else if (mc.currentScreen is GuiChat) mc.thePlayer 
                else null

            if (currentTarget != null) {
                lastTarget = currentTarget
                targetHudSpring.update(1f, 0.15f, 0.35f) 
            } else {
                targetHudSpring.update(0f, 0.2f, 0.4f)   
            }

            val progress = targetHudSpring.value
            if (progress < 0.01f || targetHudAppear < 0.01f || lastTarget == null) return

            val entity = lastTarget!!
            val width = 175f
            val height = 55f
            val xPos = (sr.scaledWidth / 2f) - (width / 2f) + targetHudXPosValue.get()
            // Slides up from 6 px below while the entrance progress runs.
            val yPos = (sr.scaledHeight / 2f) + 20f + targetHudYPosValue.get() + (1f - targetHudAppear) * 6f

            val mode = targetHudModeValue.get()
            val globalAlpha = (if (mode == "Fade") progress.coerceIn(0f, 1f) else 1f) * targetHudAppear
            val hudScale = targetHudScaleValue.get()
            
            GlStateManager.pushMatrix()
            // The user scale is applied first so it ends up outside the mode transform below and
            // scales the whole box, anchored on its top-left corner. It rides on this existing
            // matrix push, so the pop at the end of the block still matches one-for-one.
            GlStateManager.translate(xPos, yPos, 0f)
            GlStateManager.scale(hudScale, hudScale, 1f)
            GlStateManager.translate(-xPos, -yPos, 0f)
            when (mode) {
                "Zoom" -> {
                    val cx = xPos + width / 2f
                    val cy = yPos + height / 2f
                    GlStateManager.translate(cx, cy, 0f)
                    GlStateManager.scale(progress.coerceIn(0f, 1f), progress.coerceIn(0f, 1f), 1f)
                    GlStateManager.translate(-cx, -cy, 0f)
                }
                "BounceZoom" -> {
                    val cx = xPos + width / 2f
                    val cy = yPos
                    GlStateManager.translate(cx, cy, 0f)
                    GlStateManager.scale(progress.coerceIn(0f, 1f), progress, 1f)
                    GlStateManager.translate(-cx, -cy, 0f)
                }
            }

            if (easingHealth < 0 || easingHealth > entity.maxHealth || abs(easingHealth - entity.health) > 10f) {
                easingHealth = entity.health
            }
            easingHealth += (entity.health - easingHealth) * 0.15f

            // globalAlpha carries the entrance progress, so the box fades in with everything
            // else; textAlpha is the same value in the 4..255 range the font renderers need.
            val textAlpha = fadeAlpha(globalAlpha)
            GlStateManager.color(1f, 1f, 1f, globalAlpha)

            val bgColor = withAlpha(Color(248, 248, 250).rgb, (215 * globalAlpha).toInt())
            Glass.panel(
                xPos, yPos, xPos + width, yPos + height, targetHudRadiusValue.get(),
                bgColor and 0xFFFFFF, (bgColor ushr 24) and 0xFF, 0f, 53
            )

            val barW = (width - 4f) * (easingHealth / entity.maxHealth).coerceIn(0f, 1f)
            RenderUtils.drawRect(xPos + 2f, yPos + 2f, xPos + 2f + barW, yPos + 4f, withAlpha(Color(255, 50, 50).rgb, (255 * globalAlpha).toInt()))

            val avatarSize = 35f
            val avatarX = xPos + 6f
            val avatarY = yPos + 10f
            val playerInfo = mc.netHandler?.getPlayerInfo(entity.uniqueID)
            if (playerInfo != null) {
                drawRoundedAvatar(playerInfo.locationSkin, avatarX, avatarY, avatarSize, targetHudRadiusValue.get() * 0.8f, globalAlpha)
            } else {
                RenderUtils.drawRoundedRect(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, targetHudRadiusValue.get() * 0.8f, withAlpha(Color(224, 224, 230).rgb, (215 * globalAlpha).toInt()))
                UiFont.draw(FontLoaders.SF20, "?", avatarX + 13f, avatarY + 12f, withAlpha(Color(40, 40, 48).rgb, textAlpha))
            }

            val decimalFormat = DecimalFormat("##0.0", DecimalFormatSymbols(Locale.ENGLISH))
            val hpText = " §4${decimalFormat.format(entity.health)}"
            val nameText = entity.name
            val textX = avatarX + avatarSize + 8f
            val textY = yPos + 10f
            
            UiFont.draw(FontLoaders.SF21, nameText, textX, textY, withAlpha(Color(28, 28, 34).rgb, textAlpha))
            UiFont.draw(
                FontLoaders.SF21,
                hpText,
                textX + UiFont.width(FontLoaders.SF21, nameText),
                textY,
                withAlpha(Color(28, 28, 34).rgb, textAlpha)
            )

            val items = mutableListOf<net.minecraft.item.ItemStack>()
            if (entity.heldItem != null) items.add(entity.heldItem)
            for (i in 4 downTo 1) {
                val equipment = entity.getEquipmentInSlot(i)
                if (equipment != null) items.add(equipment)
            }

            var itemX = textX
            val itemY = yPos + 26f

            for ((index, item) in items.withIndex()) {
                GlStateManager.pushMatrix()
                GlStateManager.color(1f, 1f, 1f, globalAlpha)
                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.renderItemAndEffectIntoGUI(item, itemX.toInt(), itemY.toInt())
                RenderHelper.disableStandardItemLighting()
                GlStateManager.popMatrix()

                val barX = itemX + 16f
                if (item.isItemStackDamageable) {
                    val damage = item.itemDamage.toFloat()
                    val maxDamage = item.maxDamage.toFloat()
                    val durRatio = ((maxDamage - damage) / maxDamage).coerceIn(0f, 1f)
                    val barH = 14f
                    val currentBarH = barH * durRatio
                    
                    RenderUtils.drawRect(barX, itemY + 1f, barX + 1.5f, itemY + 1f + barH, withAlpha(Color(206, 206, 214).rgb, (215 * globalAlpha).toInt()))
                    val durColor = Color.HSBtoRGB(durRatio / 3f, 1f, 1f)
                    val r = (durColor shr 16) and 0xFF
                    val g = (durColor shr 8) and 0xFF
                    val b = durColor and 0xFF
                    val a = (255 * globalAlpha).toInt().coerceIn(0, 255)
                    RenderUtils.drawRect(barX, itemY + 1f + barH - currentBarH, barX + 1.5f, itemY + 1f + barH, withAlpha((r shl 16) or (g shl 8) or b, a))
                }

                itemX += 22f

                if (index < items.size - 1) {
                    val sepColor = withAlpha(Color(120, 122, 132).rgb, textAlpha)
                    UiFont.draw(FontLoaders.SF16, "|", itemX, itemY + 4f, sepColor)
                    itemX += 6f
                }
            }

            GlStateManager.popMatrix()
            GlStateManager.resetColor()
            GlStateManager.color(1f, 1f, 1f, 1f)
        }

        if (csgoCrosshairValue.get()) {
            val range = if (MovementUtils.isMoving()) 5 else 0
            RenderUtils.drawGradientRect(ScaledResolution(mc).scaledWidth / 2 + 1, ScaledResolution(mc).scaledHeight / 2 + 1, ScaledResolution(mc).scaledWidth / 2, ScaledResolution(mc).scaledHeight / 2, Color(1f, 1f, 1f, 0.85f).rgb, Color(1f, 1f, 1f, 0.85f).rgb)
            RenderUtils.drawGradientRect(ScaledResolution(mc).scaledWidth / 2, ScaledResolution(mc).scaledHeight / 2 - 8 - range, ScaledResolution(mc).scaledWidth / 2 + 1, ScaledResolution(mc).scaledHeight / 2 - 3 - range, Color(1f, 1f, 1f, 0.85f).rgb, Color(1f, 1f, 1f, 0.85f).rgb)
            RenderUtils.drawGradientRect(ScaledResolution(mc).scaledWidth / 2, ScaledResolution(mc).scaledHeight / 2 + 4 + range, ScaledResolution(mc).scaledWidth / 2 + 1, ScaledResolution(mc).scaledHeight / 2 + 9 + range, Color(1f, 1f, 1f, 0.85f).rgb, Color(1f, 1f, 1f, 0.85f).rgb)
            RenderUtils.drawGradientRect(ScaledResolution(mc).scaledWidth / 2 + 9 + range, ScaledResolution(mc).scaledHeight / 2 + 1, ScaledResolution(mc).scaledWidth / 2 + 4 + range, ScaledResolution(mc).scaledWidth / 2, Color(1f, 1f, 1f, 0.85f).rgb, Color(1f, 1f, 1f, 0.85f).rgb)
            RenderUtils.drawGradientRect(ScaledResolution(mc).scaledWidth / 2 - 3 - range, ScaledResolution(mc).scaledHeight / 2 + 1, ScaledResolution(mc).scaledWidth / 2 - 8 - range, ScaledResolution(mc).scaledHeight / 2, Color(1f, 1f, 1f, 0.85f).rgb, Color(1f, 1f, 1f, 0.85f).rgb)
        }
    }

    private fun drawSmoothOutlineRect(x: Float, y: Float, x2: Float, y2: Float, radius: Float, color: Int, lineWidth: Float) {
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        glEnable(GL_LINE_SMOOTH)
        // TASK(LineWidth-positive):glLineWidth(<= 0) 会报 GL_INVALID_VALUE(1281);设置项本身最小 0.5,
        // 但配置读入不夹,所以画之前兜一层。
        glLineWidth(lineWidth.coerceAtLeast(0.5f))
        val alpha = (color shr 24 and 0xFF) / 255.0f
        val red = (color shr 16 and 0xFF) / 255.0f
        val green = (color shr 8 and 0xFF) / 255.0f
        val blue = (color and 0xFF) / 255.0f
        GlStateManager.color(red, green, blue, alpha)
        glBegin(GL_LINE_LOOP)
        val r = radius.coerceAtMost(abs(x2 - x) / 2f).coerceAtMost(abs(y2 - y) / 2f)
        if (r <= 0F) {
            glVertex2f(x, y); glVertex2f(x2, y); glVertex2f(x2, y2); glVertex2f(x, y2)
        } else {
            val degree = Math.PI / 180.0
            val cx1 = x + r; val cy1 = y + r; val cx2 = x2 - r; val cy2 = y2 - r
            for (i in 0..90) glVertex2d((cx2 + Math.sin(i * degree) * r), (cy2 + Math.cos(i * degree) * r))
            for (i in 90..180) glVertex2d((cx2 + Math.sin(i * degree) * r), (cy1 + Math.cos(i * degree) * r))
            for (i in 180..270) glVertex2d((cx1 + Math.sin(i * degree) * r), (cy1 + Math.cos(i * degree) * r))
            for (i in 270..360) glVertex2d((cx1 + Math.sin(i * degree) * r), (cy2 + Math.cos(i * degree) * r))
        }
        glEnd()
        glDisable(GL_LINE_SMOOTH)
        GlStateManager.enableTexture2D()
        // Leave blending on: text is drawn straight after these outlines.
        GlStateManager.enableBlend()
        GlStateManager.resetColor()
        GlStateManager.popMatrix()
    }

    private fun drawRoundedAvatar(skin: ResourceLocation, x: Float, y: Float, size: Float, radius: Float, alpha: Float) {
        val u1 = 8f / 64f; val v1 = 8f / 64f; val fw = 8f / 64f; val fh = 8f / 64f
        val u2 = 40f / 64f; val v2 = 8f / 64f
        
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.disableDepth()
        GlStateManager.color(1f, 1f, 1f, alpha)
        mc.textureManager.bindTexture(skin)
        
        fun drawFaceLayer(u: Float, v: Float) {
            glBegin(GL_POLYGON)
            val r = radius.coerceAtMost(size / 2f)
            val degree = Math.PI / 180.0
            fun addVertex(vx: Double, vy: Double) {
                glTexCoord2d((u + (vx - x) / size * fw).toDouble(), (v + (vy - y) / size * fh).toDouble())
                glVertex2d(vx, vy)
            }
            val cx1 = x + r; val cy1 = y + r; val cx2 = x + size - r; val cy2 = y + size - r
            for (i in 0..90 step 5) addVertex(cx2 + Math.sin(i * degree) * r, cy2 + Math.cos(i * degree) * r)
            for (i in 90..180 step 5) addVertex(cx2 + Math.sin(i * degree) * r, cy1 + Math.cos(i * degree) * r)
            for (i in 180..270 step 5) addVertex(cx1 + Math.sin(i * degree) * r, cy1 + Math.cos(i * degree) * r)
            for (i in 270..360 step 5) addVertex(cx1 + Math.sin(i * degree) * r, cy2 + Math.cos(i * degree) * r)
            glEnd()
        }
        
        drawFaceLayer(u1, v1)
        drawFaceLayer(u2, v2)
        
        GlStateManager.enableDepth()
        // Leave blending on: the TargetHud text is drawn straight after this avatar.
        GlStateManager.enableBlend()
        GlStateManager.resetColor()
        GlStateManager.popMatrix()
    }

    private fun drawCustomHead(skin: ResourceLocation, x: Float, y: Float, width: Float, height: Float, alpha: Float = 1f) {
        // Everything goes through GlStateManager: mixing raw glEnable/glDisable with it leaves
        // its cached state out of sync, so later enableBlend() calls turn into no-ops and every
        // following string renders its font atlas without transparency.
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.depthMask(false)
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        // The session panel hands its entrance progress in, so the head fades with the panel.
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha.coerceIn(0f, 1f))
        mc.textureManager.bindTexture(skin)
        Gui.drawScaledCustomSizeModalRect(x.toInt(), y.toInt(), 8F, 8F, 8, 8, width.toInt(), height.toInt(), 64F, 64F)
        Gui.drawScaledCustomSizeModalRect(x.toInt(), y.toInt(), 40F, 8F, 8, 8, width.toInt(), height.toInt(), 64F, 64F)
        GlStateManager.depthMask(true)
        GlStateManager.enableDepth()
        GlStateManager.enableBlend()
        GlStateManager.resetColor()
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (motionVisualsValue.get() && (Launch.moduleManager.getModule(Scaffold::class.java)?.state!! || Launch.moduleManager.getModule(LegitScaffold::class.java)?.state!!)) {
            mc.thePlayer.isSwingInProgress = false
            mc.thePlayer.distanceWalkedModified = 0f
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        
        val time = System.currentTimeMillis()
        if (packet is C0APacketAnimation) {
            packetSwingList.add(time)
        } else if (packet is C02PacketUseEntity) {
            if (packet.action == C02PacketUseEntity.Action.ATTACK) {
                packetAttackList.add(time)
            } else if (packet.action == C02PacketUseEntity.Action.INTERACT || packet.action == C02PacketUseEntity.Action.INTERACT_AT) {
                packetInteractList.add(time)
            }
        } else if (packet is C08PacketPlayerBlockPlacement) {
            packetBlockList.add(time)
        }

        if (noTitle.get() && packet is S45PacketTitle) event.cancelEvent()
        if (antiTabComplete.get() && (packet is C14PacketTabComplete || packet is S3APacketTabComplete)) event.cancelEvent()

        if (packet is S02PacketChat) {
            val message = packet.chatComponent.unformattedText
            if (mc.thePlayer != null) {
                if ((message.contains("by ${mc.thePlayer.name}") || message.contains("slain by ${mc.thePlayer.name}") || message.contains("killed by ${mc.thePlayer.name}")) && !message.startsWith("[")) {
                    sessionKills++
                }
            }
        } else if (packet is S19PacketEntityStatus) {
            if (packet.opCode.toInt() == 2 && mc.theWorld != null) {
                val entity = packet.getEntity(mc.theWorld)
                if (entity == mc.thePlayer) {
                    val attacker = mc.theWorld.loadedEntityList.filter { it != mc.thePlayer && mc.thePlayer.getDistanceToEntity(it) < 7f }.minBy { mc.thePlayer.getDistanceToEntity(it) }
                    if (attacker != null && attacker.name != null) {
                        val name = attacker.name
                        val currentCount = (damageCounts[name] ?: 0) + 1
                        damageCounts[name] = currentCount
                        val maxEntry = damageCounts.entries.maxBy { it.value }
                        if (maxEntry != null) yourDarling = "${maxEntry.key} (${maxEntry.value})"
                    }
                }
            }
        }

        if (mc.theWorld == null || mc.thePlayer == null) return
        if (noInvClose.get() && packet is S2EPacketCloseWindow && (mc.currentScreen is GuiInventory || mc.currentScreen is NewUi || mc.currentScreen is ClickGui || mc.currentScreen is SmoothClickGui || mc.currentScreen is GuiChat)) {
            event.cancelEvent()
        }
    }

    @EventTarget(ignoreCondition = true)
    fun onTick(event: TickEvent) {
        if (Launch.moduleManager.shouldNotify != toggleMessageValue.get()) Launch.moduleManager.shouldNotify = toggleMessageValue.get()
        if (Launch.moduleManager.toggleSoundMode != toggleSoundValue.values.indexOf(toggleSoundValue.get())) Launch.moduleManager.toggleSoundMode = toggleSoundValue.values.indexOf(toggleSoundValue.get())
        if (Launch.moduleManager.toggleVolume != 83f) Launch.moduleManager.toggleVolume = 83f
    }

    /** Name half of an ArrayList row. */
    private fun getModName(mod: Module): String = mod.spacedName

    /**
     * Value half of an ArrayList row ("120ms", "Watchdog", ...). This used to be prefixed with
     * the §8 colour code; the row now draws it in its own grey, so the bare tag is returned
     * with the separating space so name and value still measure and align as one line.
     */
    private fun getModTag(m: Module): String = m.tag?.let { " $it" } ?: ""

    /**
     * Packs an 0..255 alpha onto an 0xRRGGBB colour. java.awt.Color.rgb holds no alpha, so the
     * `Color(r, g, b, a).rgb` form used elsewhere renders fully opaque; the sliding rows need
     * the alpha bits to fade.
     */
    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /**
     * Alpha byte for a fade progress. Both font renderers rewrite an all-zero alpha byte to
     * fully opaque (`if ((color and 0xFC000000) == 0) color = color or 0xFF000000`), so a fading
     * string is clamped to 4: at that point it is invisible anyway, but it can no longer flash
     * back to opaque for the one frame the progress sits just above zero.
     */
    private fun fadeAlpha(progress: Float): Int = (progress.coerceIn(0f, 1f) * 255f).toInt().coerceIn(4, 255)

    /**
     * Whether the cached backdrop sample says the picture behind the HUD is bright, in which
     * case the HUD switches to [contrastDarkRgb]. A failed read-back stays at -1 and therefore
     * never counts as bright, so the normal colours are kept when the sample is unavailable.
     */
    private val isBackdropBright: Boolean
        get() = backdropLuminance >= contrastLuminanceThreshold

    /**
     * Average relative luminance (0..1) of the GUI rectangle [x], [y], [width], [height], read
     * back from the current frame buffer. Returns -1 when the sample cannot be taken, which
     * tells the caller to keep its normal colours.
     *
     * glReadPixels wants physical pixels counted from the bottom-left, while the HUD works in
     * scaled GUI coordinates starting at the top-left, so both axes are converted here: the
     * rectangle is scaled by [ScaledResolution.getScaleFactor] and its vertical edge is flipped
     * around the window height. The read-back is limited to [maxLuminanceSampleSize] pixels per
     * side, centred inside the requested rectangle and clamped onto the screen, because a
     * rectangle reaching outside the frame buffer makes GL raise an error and a wide one would
     * stall the pipeline.
     */
    private fun sampleLuminance(x: Float, y: Float, width: Float, height: Float): Float {
        val displayWidth = mc.displayWidth
        val displayHeight = mc.displayHeight
        if (displayWidth <= 0 || displayHeight <= 0 || width <= 0f || height <= 0f) return -1f

        val factor = ScaledResolution(mc).scaleFactor
        val rectLeft = x * factor
        val rectWidth = width * factor
        val rectHeight = height * factor
        // gui y grows downwards from the top edge, the frame buffer counts upwards from the
        // bottom edge: rectBottom is the frame buffer y of the rectangle's lower side.
        val rectBottom = displayHeight - (y + height) * factor
        // A rectangle that misses the frame buffer completely cannot be sampled; without this
        // check the clamp below would quietly read an unrelated patch at the screen edge.
        if (rectLeft + rectWidth <= 0f || rectLeft >= displayWidth) return -1f
        if (rectBottom + rectHeight <= 0f || rectBottom >= displayHeight) return -1f

        return try {
            val readWidth = rectWidth.toInt().coerceIn(1, maxLuminanceSampleSize)
            val readHeight = rectHeight.toInt().coerceIn(1, maxLuminanceSampleSize)
            val readX = (rectLeft + (rectWidth - readWidth) / 2f).toInt()
                .coerceIn(0, (displayWidth - readWidth).coerceAtLeast(0))
            val readY = (rectBottom + (rectHeight - readHeight) / 2f).toInt()
                .coerceIn(0, (displayHeight - readHeight).coerceAtLeast(0))

            luminanceBuffer.clear()
            glReadPixels(readX, readY, readWidth, readHeight, GL_RGBA, GL_UNSIGNED_BYTE, luminanceBuffer)

            var sum = 0.0
            val pixels = readWidth * readHeight
            for (i in 0 until pixels) {
                val red = (luminanceBuffer.get(i * 4).toInt() and 0xFF) / 255f
                val green = (luminanceBuffer.get(i * 4 + 1).toInt() and 0xFF) / 255f
                val blue = (luminanceBuffer.get(i * 4 + 2).toInt() and 0xFF) / 255f
                sum += (0.2126f * red + 0.7152f * green + 0.0722f * blue).toDouble()
            }
            (sum / pixels).toFloat()
        } catch (throwable: Throwable) {
            -1f
        }
    }

    fun getAnimPos(pos: Float): Float {
        hotBarX = if (state && animHotbarValue.get()) AnimationUtils.animate(pos, hotBarX, animHotbarSpeedValue.get() * RenderUtils.deltaTime.toFloat()) else pos
        return hotBarX
    }

    private inner class Spring(var value: Float = 0f) {
        var velocity = 0f
        fun update(target: Float, tension: Float, friction: Float) {
            val displacement = target - value      
            val springForce = displacement * tension 
            val dampingForce = velocity * friction   
            velocity += springForce - dampingForce
            value += velocity
        }
        fun reset() {
            value = 0f
            velocity = 0f
        }
    }
}
