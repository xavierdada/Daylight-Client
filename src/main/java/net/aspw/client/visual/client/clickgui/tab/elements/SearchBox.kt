package net.aspw.client.visual.client.clickgui.tab.elements

import net.aspw.client.visual.font.semi.Fonts
import net.minecraft.client.gui.GuiTextField

class SearchBox(componentId: Int, x: Int, y: Int, width: Int, height: Int) :
    GuiTextField(componentId, Fonts.fontSFUI37, x, y, width, height) {

    init {
        // Light theme: dark input text on the pale search box, grey placeholder.
        setTextColor(0xFF1C1C22.toInt())
        setDisabledTextColour(0xFF8A8C98.toInt())
    }

    override fun getEnableBackgroundDrawing() = false
}