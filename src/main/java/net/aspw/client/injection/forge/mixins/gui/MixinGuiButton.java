package net.aspw.client.injection.forge.mixins.gui;

import net.aspw.client.Launch;
import net.aspw.client.features.module.impl.visual.Interface;
import net.aspw.client.utils.render.RenderUtils;
import net.aspw.client.visual.client.GuiMainMenu;
import net.aspw.client.visual.font.semi.AWTFontRenderer;
import net.aspw.client.visual.font.semi.Fonts;
import net.aspw.client.visual.font.smooth.FontLoaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.awt.*;

@Mixin(GuiButton.class)
public abstract class MixinGuiButton extends Gui {

    @Shadow
    @Final
    protected static ResourceLocation buttonTextures;
    @Shadow
    public boolean visible;
    @Shadow
    public int xPosition;
    @Shadow
    public int yPosition;
    @Shadow
    public int width;
    @Shadow
    public int height;
    @Shadow
    public boolean enabled;
    @Shadow
    public String displayString;
    @Shadow
    protected boolean hovered;
    private float alpha;

    @Shadow
    protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

    /**
     * @author As_pw
     * @reason Button Renderer
     */
    @Overwrite
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            final FontRenderer fontRenderer =
                    mc.getLanguageManager().isCurrentLocaleUnicode() ? mc.fontRendererObj : Fonts.minecraftFont;
            hovered = (mouseX >= this.xPosition && mouseY >= this.yPosition &&
                    mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height);

            final Interface anInterface = Launch.moduleManager.getModule(Interface.class);

            if (anInterface == null) return;

            if (mc.currentScreen instanceof GuiMainMenu && enabled && hovered) {
                alpha = 160;
            } else if (enabled && hovered) {
                alpha = 190;
            } else {
                alpha = 120;
            }

            if (mc.currentScreen instanceof GuiMainMenu)
                RenderUtils.originalRoundedRect(this.xPosition, this.yPosition,
                        this.xPosition + this.width, this.yPosition + this.height, 4F,
                        this.enabled ? (this.hovered
                                ? new Color(0.78F, 0.81F, 0.87F).getRGB()
                                : new Color(0.89F, 0.91F, 0.95F).getRGB()) :
                                new Color(184, 186, 199, 100).getRGB());
            else {
                if (this.enabled && this.hovered) {
                    RenderUtils.drawGradientRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + 1, new Color(30, 79, 168, 160).getRGB(), new Color(30, 79, 168, 160).getRGB());
                    RenderUtils.drawGradientRect(this.xPosition, this.yPosition + this.height - 1, this.xPosition + this.width, this.yPosition + this.height, new Color(30, 79, 168, 160).getRGB(), new Color(30, 79, 168, 160).getRGB());
                    RenderUtils.drawGradientRect(this.xPosition, this.yPosition, this.xPosition + 1, this.yPosition + this.height, new Color(30, 79, 168, 160).getRGB(), new Color(30, 79, 168, 160).getRGB());
                    RenderUtils.drawGradientRect(this.xPosition + this.width - 1, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, new Color(30, 79, 168, 160).getRGB(), new Color(30, 79, 168, 160).getRGB());
                    RenderUtils.originalRoundedRect(this.xPosition + 1, this.yPosition + 1, this.xPosition + this.width - 1, this.yPosition + this.height - 1, 4F, new Color(0.93F, 0.94F, 0.97F, this.alpha / 255F).getRGB());
                } else {
                    RenderUtils.originalRoundedRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, 4F, new Color(0.93F, 0.94F, 0.97F, this.alpha / 255F).getRGB());
                }
            }

            int j = 0x2A2C36;
            int b = 0x1C1C22;

            if (!this.enabled) {
                j = 0x8E9098;
                b = 0x8E9098;
            } else if (this.hovered) {
                j = 0x1E4FA8;
                b = 0x1E4FA8;
            }

            if (mc.currentScreen instanceof GuiMainMenu)
                FontLoaders.SF21.drawCenteredString(this.displayString, this.xPosition + this.width / 2F, this.yPosition + (this.height - 8) / 2F, j);
            else {
                mc.getTextureManager().bindTexture(buttonTextures);
                mouseDragged(mc, mouseX, mouseY);
                AWTFontRenderer.Companion.setAssumeNonVolatile(true);
                fontRenderer.drawString(displayString,
                        ((this.xPosition + this.width / 2F) -
                                fontRenderer.getStringWidth(displayString) / 2F),
                        (int) (this.yPosition + (this.height - 4) / 2F - 2), b, false);
                AWTFontRenderer.Companion.setAssumeNonVolatile(false);
                GlStateManager.resetColor();
            }
        }
    }
}