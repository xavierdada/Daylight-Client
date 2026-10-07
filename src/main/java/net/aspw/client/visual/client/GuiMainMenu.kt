package net.aspw.client.visual.client

import net.aspw.client.Launch
import net.aspw.client.utils.APIConnecter
import net.aspw.client.utils.misc.MiscUtils
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.visual.client.altmanager.GuiAltManager
import net.aspw.client.visual.font.UiFont
import net.aspw.client.visual.font.smooth.FontLoaders
import net.minecraft.client.gui.*
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

class GuiMainMenu : GuiScreen(), GuiYesNoCallback {

    var alpha = 255
    private var lastAnimTick: Long = 0L
    private var previousTime = System.nanoTime()
    private var interval = 1_000_000_000L / 15
    private val moveMouseStrength = 200
    private var alrUpdate = false
    private val buttonWidth = 112
    private val buttonHeight = 20
    private var ticks = 0
    private val particles = mutableListOf<Particle>()
    private var lastUpdateTime = System.currentTimeMillis()

    override fun initGui() {
        if (particles.isNotEmpty())
            particles.clear()
        for (i in 0 until 600) {
            particles.add(Particle((Math.random() * width).toFloat(), (Math.random() * height).toFloat()))
        }
        // Horizontal menu bar: the five main entries sit side by side, shrinking together so a
        // narrow window still fits them all on one row.
        val labels = arrayOf("SINGLE PLAYER", "MULTI PLAYER", "ALT MANAGER", "OPTIONS", "EXIT")
        val gap = 6
        val rowButtonWidth = ((width - 20 - gap * (labels.size - 1)) / labels.size).coerceAtMost(buttonWidth)
        val rowWidth = rowButtonWidth * labels.size + gap * (labels.size - 1)
        val startX = (width - rowWidth) / 2
        val rowY = height / 2 + 10
        for ((index, label) in labels.withIndex()) {
            this.buttonList.add(
                GuiButton(
                    index,
                    startX + index * (rowButtonWidth + gap),
                    rowY,
                    rowButtonWidth,
                    buttonHeight,
                    label
                )
            )
        }
        this.buttonList.add(
            GuiButton(
                5,
                (this.width - 24F - UiFont.widthInt(FontLoaders.SF21, "Connect API")).toInt(),
                8,
                buttonWidth - 42,
                buttonHeight,
                "Connect API"
            )
        )
        this.buttonList.add(
            GuiButton(
                6,
                (this.width - 112F - UiFont.widthInt(FontLoaders.SF21, "Configure")).toInt(),
                8,
                buttonWidth - 42,
                buttonHeight,
                "Configure"
            )
        )
        this.buttonList.add(
            GuiButton(
                7,
                (this.width - 177F - UiFont.widthInt(FontLoaders.SF21, "Donate Now")).toInt(),
                8,
                buttonWidth - 42,
                buttonHeight,
                "Donate Now"
            )
        )
        super.initGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!alrUpdate) {
            lastAnimTick = System.currentTimeMillis()
            alrUpdate = true
        }
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastUpdateTime) / 1000.0f
        lastUpdateTime = currentTime
        GL11.glPushMatrix()
        GlStateManager.disableAlpha()
        drawBackground(0)
        // Backdrop is the loading artwork with the "Launching ..." caption erased, so the dark
        // menu text reads properly instead of sitting on the old dark illustration.
        RenderUtils.drawImage(ResourceLocation("client/mainmenu_bg.png"), 0, 0, width, height)
        if (APIConnecter.hasImage("daylight", "background")) {
            RenderUtils.drawImage2(
                APIConnecter.callImage("daylight", "background"),
                width / 2F - 50F,
                height / 2F - 145F,
                100,
                100
            )
        } else {
            // Shown once loading is done, i.e. on this menu, above the horizontal button row.
            // 标题用 SF21 那一档再放大一档（scale 默认是按字体档位算的 1.0），因为菜单空间
            // 充裕、标题原本的 21pt TTF 明显比副标题大。
            val title = Launch.CLIENT_BEST + " Client"
            UiFont.draw(
                FontLoaders.SF21,
                title,
                width / 2F - UiFont.width(FontLoaders.SF21, title, UiFont.MAIN_MENU_TITLE_SCALE) / 2F,
                height / 2F - 45F,
                0xFF1C1C22.toInt(),
                scale = UiFont.MAIN_MENU_TITLE_SCALE
            )
        }
        GlStateManager.enableAlpha()
        particles.forEach { it.update(deltaTime) }
        particles.forEach { it.render() }
        val clientCaption = Launch.CLIENT_BEST + " Client"
        UiFont.draw(
            FontLoaders.SF20,
            clientCaption,
            width - 4F - UiFont.width(FontLoaders.SF20, clientCaption),
            height - 23F,
            0xFF1C1C22.toInt()
        )
        val buildCaption = "Your currently build is " + Launch.CLIENT_VERSION
        UiFont.draw(
            FontLoaders.SF20,
            buildCaption,
            width - 4F - UiFont.width(FontLoaders.SF20, buildCaption),
            height - 12F,
            0xFF1C1C22.toInt()
        )
        GlStateManager.disableAlpha()
        GlStateManager.enableAlpha()
        GL11.glPopMatrix()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(GuiSelectWorld(this))
            1 -> mc.displayGuiScreen(GuiMultiplayer(this))
            2 -> mc.displayGuiScreen(GuiAltManager(this))
            3 -> mc.displayGuiScreen(GuiOptions(this, mc.gameSettings))
            4 -> mc.shutdown()

            5 -> {
                APIConnecter.checkStatus()
                APIConnecter.checkStaffList()
                APIConnecter.loadPictures()
                APIConnecter.loadDonors()
            }

            6 -> mc.displayGuiScreen(GuiInfo(this))
            7 -> MiscUtils.showURL(APIConnecter.donate)
        }
    }

    private fun moveMouseEffect(mouseX: Int, mouseY: Int, strength: Float) {
        val mX = mouseX - width / 2
        val mY = mouseY - height / 2
        val xDelta = mX.toFloat() / (width / 2).toFloat()
        val yDelta = mY.toFloat() / (height / 2).toFloat()

        GL11.glTranslatef(xDelta * strength, yDelta * strength, 0F)
    }

    private fun loadGif() {
        val currentTime = System.nanoTime()
        val deltaTime = currentTime - previousTime

        if (deltaTime >= interval) {
            ticks++
            if (ticks > 20)
                ticks = 0
            previousTime = currentTime
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {}

    override fun onGuiClosed() {
        if (particles.isNotEmpty())
            particles.clear()
        ticks = 0
    }
}