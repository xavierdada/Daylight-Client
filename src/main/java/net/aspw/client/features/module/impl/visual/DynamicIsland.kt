package net.aspw.client.features.module.impl.visual

import net.aspw.client.Launch
import net.aspw.client.event.EventTarget
import net.aspw.client.event.Render2DEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.features.module.impl.player.Scaffold
import net.aspw.client.utils.render.Glass
import net.aspw.client.utils.render.RenderUtils
import net.aspw.client.value.BoolValue
import net.aspw.client.value.ColorValue
import net.aspw.client.value.FloatValue
import net.aspw.client.visual.font.semi.Fonts
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.max

@ModuleInfo(name = "DynamicIsland", spacedName = "Dynamic Island", category = ModuleCategory.VISUAL)
class DynamicIsland : Module() {

    private val blurValue = BoolValue("BackgroundBlur", true)

    private val borderValue = BoolValue("Border", true)
    private val borderThicknessValue = FloatValue("BorderThickness", 1.5f, 0.5f, 5.0f, { borderValue.get() })

    private val bgAlphaValue = FloatValue("BackgroundAlpha", 180f, 0f, 255f)
    private val cornerRadiusValue = FloatValue("CornerRadius", 8.0f, 0f, 20f)

    /**
     * 强调色：驱动通知左侧的圆角图标卡片以及卡片里的 iOS 开关（轨道/滑块）。
     *
     * This used to be called `CapsuleColor` and drove the capsule background; the capsule is now a
     * fixed white (see [CAPSULE_RGB]) and this setting only tints the notification's button.
     * The rename is deliberate: an old `CapsuleColor` entry in value.json is simply ignored, and the
     * default below (a clearly brighter blue) is what a fresh config starts from.
     */
    private val accentColorValue = ColorValue("AccentColor", Color(242, 167, 157).rgb)

    private val notifyDurationValue = FloatValue("NotifyDuration", 3.0f, 1.0f, 10.0f)

    private val speedValue = FloatValue("AnimSpeed", 0.15f, 0.05f, 0.5f)
    private val bouncinessValue = FloatValue("Bounciness", 0.6f, 0.1f, 0.95f)

    private val showFpsValue = BoolValue("ShowFPS", true)

    private val noDisappearValue = BoolValue("NoDisappear", false)

    private val cutApartValue = BoolValue("CutApart", true, { noDisappearValue.get() })

    // The capsule is a fixed opaque white: only the RGB part lives in [CAPSULE_RGB], and the alpha
    // that makes the capsule fade in and out is added at draw time by [withAlpha]. The blur layer is
    // that same white pushed a step *down* instead of up (see [CAPSULE_BLUR_DROP]), because lifting
    // pure white would clamp straight back to white and the two stacked layers would be identical.
    private val capsuleRgb: Int
        get() = CAPSULE_RGB
    private val capsuleBlurRgb: Int
        get() = darkenRgb(CAPSULE_RGB, CAPSULE_BLUR_DROP)

    /**
     * The notification button: the rounded icon card and the iOS toggle inside it.
     *
     * The card is the picked colour lifted by [TILE_LIFT] so it always reads one step brighter than
     * the pick it came from, and the toggle is that same colour too: its track is the pick pushed
     * towards black (so the switch reads against the brighter card) and its knob is the pick pushed
     * towards white (so the light half of the switch stays light whatever the pick is). A very dark
     * pick therefore yields a dark button, but never an unreadable one: the card keeps a floor (see
     * [ACCENT_FLOOR]) and every glyph inside the button is still drawn in flat white, which is what
     * keeps the button legible on the white capsule too.
     */
    private val accentRgb: Int
        get() = accentColorValue.get() and 0xFFFFFF

    /**
     * The island's primary ink, and the one constant the whole light capsule hangs off: every glyph,
     * label and icon drawn directly on the white background reads this.
     *
     * It used to be white because the capsule was dark. It is now the near-black the project's light
     * theme already uses, which keeps the change centralised: the HUD segment text, the separators'
     * neighbour glyphs, the notification title and message, every shape of [drawIslandIcon] and the
     * `textRgb` accent passed by [showCustomNotification] and by Scaffold's notice all follow this
     * one value, so no drawing call had to be touched individually.
     *
     * Deliberately *not* part of this: [tileGlyphRgb], the flat white glyph on the accent coloured
     * button card, which is unchanged artwork.
     */
    private val textRgb = Color(28, 28, 34).rgb

    /** Secondary ink: the HUD separators, one step lighter than [textRgb] but still dark. */
    private val separatorRgb = Color(120, 124, 138).rgb

    /** The dimmed tail of a notification's second line (" - 6.03 block/s"). */
    private val detailRgb = Color(104, 108, 122).rgb

    /** The remaining-time bar along the bottom of a notification. */
    private val progressRgb = Color(112, 118, 152).rgb

    /**
     * The capsule outline. It used to be a pale blue that read against the deep blue capsule; on
     * white that would be almost invisible, so it is a muted slate now. It is still drawn at the
     * same alphas, which keeps the outline clearly perceptible on the white fill.
     */
    private val borderRgb = Color(84, 92, 116).rgb

    /**
     * The flat white glyph inside the notification's accent coloured button card (the Scaffold cube).
     * It stays white on purpose: the card is the AccentColor artwork that the setting controls, so
     * its contents must not follow [textRgb] onto the dark side along with the capsule background.
     */
    private val tileGlyphRgb = Color(255, 255, 255).rgb

    /** The status word's colours ("Enabled!" / "Disabled!"); both still read on white. */
    private val enabledRgb = Color(95, 174, 83).rgb
    private val disabledRgb = Color(187, 85, 101).rgb

    // Notification toggle pill. Neither half is hard-coded any more: both are derived from the
    // AccentColor inside drawNotifyIconTile, so the switch follows the same setting as the card it
    // sits on. A background pair would be dead weight, so there is deliberately none here.

    private val moduleStates = HashMap<String, Boolean>()
    private var isFirstLoad = true

    /**
     * What sits inside a notification's icon tile.
     *
     * [SWITCH] is the iOS toggle the previous reference artwork asked for and stays the default
     * for every entry point that does not name an icon; [CUBE] is Scaffold's wire-frame block.
     */
    private enum class NotifyIcon { SWITCH, CUBE }

    /**
     * Which third segment the HUD is currently showing. [NONE] means the third segment is switched
     * off entirely (Scaffold off and ShowFPS off), so the HUD is just the logo and the player name.
     */
    private enum class ThirdSegment { NONE, BLOCKS, FPS }

    /**
     * One notification. Everything the draw branches need travels with the record, so a notice being
     * drawn can never be mutated by a newer one: a replacement swaps in a whole new record.
     *
     * The second line is assembled from three pieces, drawn left to right:
     * [message] in [textRgb], [accent] in [accentColor], then [detail] in grey. All three may be
     * empty.
     */
    // TASK(Island-multirow): the whole multi-row notification feature is delimited by comments that
    // start with this marker. Removing every marked block and restoring the pre-task versions of the
    // four marked methods (noted inline) returns this file to its pre-task state.
    private data class IslandNotification(
        val title: String,
        val message: String,
        val accent: String,
        val accentColor: Int,
        val switchOn: Boolean,
        val durationMs: Long,
        val detail: String = "",
        val icon: NotifyIcon = NotifyIcon.SWITCH,
        // TASK(Island-multirow): every field below this line is new. Delete from here down to (and
        // including) the `isAliveAt` function below, and restore the `)` that closed the old record.
        /**
         * Absolute timestamp this row appeared; the per-row fade-in is measured from it.
         *
         * The row times itself, which is what lets several rows coexist: every record carries its
         * own pair of stamps and the draw loop never reads a shared "current notification" clock.
         */
        val startedAtMs: Long = 0L,
        /** Absolute timestamp this row is due to go away; ignored while [persistent] is set. */
        val expiresAtMs: Long = 0L,
        /**
         * [ThirdSegment]-style constant row: it never expires and is never evicted. Scaffold's
         * "blocks left" line is the only such row, and it lives at the bottom of the stack.
         */
        val persistent: Boolean = false,
        /**
         * Optional caption written across the row's progress bar. Only the persistent Scaffold row
         * sets it (the live block count); it is part of the record, so the measured layout that
         * covers it is cached on the row like every other string here.
         */
        val progressLabel: String = "",
        /**
         * Measured layout of *this* record, filled in once by the draw loop the first time the row
         * is actually drawn and invalidated only by a font identity change.
         *
         * This is the per-row replacement for the old single shared layout field: because a record
         * is immutable and only ever drawn on one font at a time, the measurement happens once per
         * record instead of once per frame, and it stays attached to the row no matter how many
         * rows are on screen.
         */
        var cachedLayout: NotifyLayout? = null,
        /** The font [cachedLayout] was measured on; a different font re-measures this row only. */
        var cachedLayoutFont: Any? = null,
        /** Font height [cachedLayout] was measured with, compared exactly like the font itself. */
        var cachedLayoutFontHeight: Float = -1f
    ) {
        /** Whether this row should still be on screen at [now]: the persistent row always is. */
        fun isAliveAt(now: Long): Boolean = persistent || now < expiresAtMs
    }

    // TASK(Island-multirow): MAX_NOTIFY_ROWS / MAX_ACTIVE_NOTIFICATIONS / NOTIFY_STACK_GAP /
    // NOTIFY_FADE_IN_MS / NOTIFY_FADE_OUT_MS / PROGRESS_LABEL_HEIGHT / SCAFFOLD_PROGRESS_REFERENCE
    // are new; the old block was just MAX_NOTIFY_QUEUE = 8 followed by NOTIFY_POP_START. The two
    // unused ones (SCAFFOLD_PROGRESS_REFERENCE, and the fade constants are used) can be dropped.
    private companion object {
        /**
         * How many notification rows may be on screen at once. Extra rows never queue up: the
         * oldest transient row is dropped the moment this is exceeded, so the stack can only ever
         * push out a notice that has already been readable for a while.
         */
        const val MAX_NOTIFY_ROWS = 4

        /** Hard bound on the backing list, so a pathological caller cannot grow it without limit. */
        const val MAX_ACTIVE_NOTIFICATIONS = 8

        /** Vertical gap between two stacked notification rows. */
        const val NOTIFY_STACK_GAP = 3f

        /** Pop-in start value. Kept below 1.3 so the bounce stays restrained. */
        const val NOTIFY_POP_START = 1.28f

        /**
         * Per-row fade in/out lengths. Each row owns its own fade instead of sharing the capsule's
         * single alpha spring, so a row that is expiring dims while a fresh row above it is at full
         * strength. The fade-out is the longer of the two so a row that leaves reads as a fade
         * rather than a pop, and both are shorter than the shortest configurable duration (1s).
         */
        const val NOTIFY_FADE_IN_MS = 180f
        const val NOTIFY_FADE_OUT_MS = 400f

        /** The module that gets its own notification artwork and its live placement rate. */
        const val SCAFFOLD_NAME = "Scaffold"

        /** Gap between the primary head and the grey tail of a notification's second line. */
        const val DETAIL_GAP = 2f

        /** Height of the remaining-time bar; its corner radius is half of this. */
        const val PROGRESS_HEIGHT = 4f

        /**
         * Height of the persistent Scaffold row's bar. It is taller than [PROGRESS_HEIGHT] because
         * the live block count is written across it, and a 4px bar cannot hold a glyph.
         */
        const val PROGRESS_LABEL_HEIGHT = 11f

        /**
         * Blocks the Scaffold bar treats as "full". The bar is a reading aid for the live block
         * count written on it, not a measurement: the count on the bar is always the exact value.
         */
        const val SCAFFOLD_PROGRESS_REFERENCE = 64

        /** How much brighter the notification's icon tile is lifted above the picked accent colour. */
        const val TILE_LIFT = 18

        /**
         * The fixed capsule background: flat white. It stays inside Int range (0xFFFFFF), so no
         * `.toInt()` is needed. Everything drawn on top of it is deliberately dark.
         */
        const val CAPSULE_RGB = 0xFFFFFF

        /**
         * How far the blur layer behind the capsule is pushed *below* [CAPSULE_RGB]. Brightening
         * white is a no-op, so the blurred backdrop is separated from the capsule by darkening it
         * one notch (12/255 per channel) instead.
         */
        const val CAPSULE_BLUR_DROP = 12

        /**
         * Channel floor for the accent colour. The icon card is built from this colour and holds a
         * flat white glyph, so a pick darker than this would leave the button barely separated from
         * the white capsule and the glyph hard to read; a darker pick is lifted up to the floor.
         */
        const val ACCENT_FLOOR = 82

        /** Shifts that turn an RGB int into its three channels, kept next to the bit packing. */
        const val CHANNEL_RED = 16
        const val CHANNEL_GREEN = 8

        /** Notification layout constants, all in the same units as the capsule: logical pixels. */
        const val NOTIFY_TILE_SIZE = 24f
        const val NOTIFY_TILE_GAP = 6f
        const val NOTIFY_PROGRESS_GAP = 9f
        const val NOTIFY_PROGRESS_PADDING = 5f

        /** Vertical gap between the notification's title row and its message row. */
        const val NOTIFY_ROW_GAP = 2f

        /** How much of the accent colour each half of the toggle keeps (see [drawToggleSwitch]). */
        const val SWITCH_TRACK_ON = 0.55f
        const val SWITCH_TRACK_OFF = 0.40f
        const val SWITCH_KNOB_ON = 0.80f
        const val SWITCH_KNOB_OFF = 0.90f

        /** How many degrees each corner arc of [drawRoundedOutline] steps through. */
        const val ARC_STEP_DEG = 5

        /** Number of glyph rays in the LOGO sun and the GEAR cog. */
        const val RAY_COUNT = 8

        /**
         * Unit-circle samples for the ray loops of the LOGO sun (every 45 degrees) and the GEAR cog
         * (the same angles, offset by 22.5). Each loop used to rebuild both values from Math.toRadians
         * + Math.cos/sin on every frame, which allocates boxed Doubles per ray; the angles are fixed,
         * so they are tabulated once at class load instead.
         */
        val RAY_COSINE = DoubleArray(RAY_COUNT) { index ->
            Math.cos(Math.toRadians((index * (360.0 / RAY_COUNT)).toDouble()))
        }
        val RAY_SINE = DoubleArray(RAY_COUNT) { index ->
            Math.sin(Math.toRadians((index * (360.0 / RAY_COUNT)).toDouble()))
        }
        val GEAR_COSINE = DoubleArray(RAY_COUNT) { index ->
            Math.cos(Math.toRadians((index * (360.0 / RAY_COUNT) + 22.5).toDouble()))
        }
        val GEAR_SINE = DoubleArray(RAY_COUNT) { index ->
            Math.sin(Math.toRadians((index * (360.0 / RAY_COUNT) + 22.5).toDouble()))
        }

        /** How many units of the notification's second line the primary head and the grey tail use. */
        const val LINE_MESSAGE = 0
        const val LINE_ACCENT = 1
        const val LINE_DETAIL = 2

        /**
         * cos and sin of every arc sample of a full rounded-rect outline, generated once at class
         * load.
         *
         * [drawRoundedOutline] walks four quarter circles of [ARC_STEP_DEG] degrees; doing that with
         * Math.sin/Math.cos every frame allocated two boxed doubles per vertex (hundreds per frame,
         * and outline glyphs push that into the thousands). The samples are exact constants, so the
         * tables are precomputed instead. 360 is a whole number of steps, so the closing sample
         * (index 72) is exactly the first one again.
         */
        val ARC_COSINE = DoubleArray(360 / ARC_STEP_DEG + 1) { index ->
            Math.cos(Math.toRadians((index * ARC_STEP_DEG).toDouble()))
        }
        val ARC_SINE = DoubleArray(360 / ARC_STEP_DEG + 1) { index ->
            Math.sin(Math.toRadians((index * ARC_STEP_DEG).toDouble()))
        }
    }

    // -----------------------------------------------------------------------------------------
    // Display caches
    //
    // Everything below exists because [Fonts.font35] is a GameFontRenderer: every
    // getStringWidth() allocates a TextEvent, dispatches it through the whole event manager and
    // then walks the string character by character, and every drawString() walks it again (the
    // renderer has no display-list cache, so it issues one textured quad *per character*). The
    // text and the measured widths were being rebuilt from scratch on every single frame, which
    // is where this module's cost was. The caches are only ever recomputed when one of the
    // values they were derived from actually changed, and all of those values are compared
    // exactly (string / int equality plus a reference check on the notification), so a cache hit
    // is always pixel-identical to a rebuild.
    // -----------------------------------------------------------------------------------------

    /** Cached HUD segments; only the third entry's text ever changes while the module is on. */
    private val segmentCache = arrayOf(
        IslandSegment(IslandIcon.LOGO, "Daylight"),
        IslandSegment(IslandIcon.USER, ""),
        IslandSegment(IslandIcon.FPS, "")
    )

    /** Measured width of each cached segment's text, invalidated alongside it. */
    private val segmentTextWidths = FloatArray(3)

    /**
     * The two element view of the caches above, handed to the draw loop while the HUD shows only the
     * logo and the player name; the third slot is simply left out of `baseCount` in that case. Both
     * views are refreshed by [updateTextCaches] and read by the draw loop, so no array is ever
     * allocated on a frame that does not change the text.
     */
    private val twoSegments = arrayOf(segmentCache[0], segmentCache[1])
    private val twoSegmentWidths = floatArrayOf(segmentTextWidths[0], segmentTextWidths[1])

    /** The three element view, refreshed alongside [twoSegments]. */
    private val allSegments = arrayOf(segmentCache[0], segmentCache[1], segmentCache[2])
    private val allSegmentWidths = FloatArray(3)

    /** Total width of the separator, which only changes with the font. */
    private var separatorWidthCache = -1f
    private var separatorWidthFont: Any? = null

    /** The font the segment widths below were measured on; a different one invalidates all of them. */
    private var segmentWidthFont: Any? = null

    /** Last player name baked into the USER segment, so a rename (or a respawn) rebuilds it. */
    private var cachedPlayerName: String? = null

    /** The displayed third segment; [ThirdSegment.NONE] while there is nothing to show there. */
    private var cachedThirdKind = ThirdSegment.NONE
    private var cachedThirdValue = Int.MIN_VALUE

    // TASK(Island-multirow): activeNotifications / visibleNotifications / scaffoldRowBlocks /
    // scaffoldRowPresent / rowOrder replaced `notifyQueue` + `currentNotification` + `notifyEndTime`.
    /**
     * Every notification row currently alive, oldest first, with the persistent Scaffold row (if
     * any) kept last so it is always the bottom line of the stack.
     *
     * This replaces the old single-slot `currentNotification` + `notifyEndTime` pair and the deque
     * behind it: rows no longer take turns on one another, they coexist. Each record carries its own
     * `startedAtMs` / `expiresAtMs`, so expiry is decided per row by [pruneExpiredNotifications] and
     * needs no shared clock at all.
     */
    private val activeNotifications = ArrayList<IslandNotification>()

    /**
     * Snapshot of the rows the last frame actually drew, replaced in place by
     * [fillVisibleNotifications]. It exists purely so the HUD can keep fading the stack out through
     * the capsule's alpha spring after the last row expired, exactly like the single notification
     * used to: the rows are copied out first and the expired ones dropped afterwards.
     */
    private val visibleNotifications = ArrayList<IslandNotification>()

    /** Block count the persistent Scaffold row was last built from, so it is rebuilt only on change. */
    private var scaffoldRowBlocks = Int.MIN_VALUE
    private var scaffoldRowPresent = false

    /** The rows the current frame draws, in draw order; the layout pass sums their cached heights. */
    private val rowOrder = ArrayList<IslandNotification>()

    private var lastRenderTime = System.currentTimeMillis()

    private val widthSpring = Spring(100f)
    private val heightSpring = Spring(22f)
    private val alphaSpring = Spring(0f)

    private val splitWidthSpring = Spring(0f)
    private val splitHeightSpring = Spring(0f)
    private val splitAlphaSpring = Spring(0f)

    /** Drives the pop-in: started above 1.0 for every new notification, settles back to 1.0. */
    private val popSpring = Spring(1f)

    /**
     * Island glyphs. They used to be blitted from the shipped line-art PNGs, which at 12px
     * collapsed into grey mush. Drawing them from primitives keeps them crisp; every glyph is
     * a flat shape in [textRgb] (dark, since the capsule is white) laid out from `r = size / 2`.
     */
    private enum class IslandIcon { LOGO, USER, FPS, BLOCKS, GEAR }

    private data class IslandSegment(
        val icon: IslandIcon,
        val text: String
    )

    /**
     * Everything about the notification on screen that only changes when the notification itself
     * changes: the three measured widths of its second line, plus the two widths derived from them.
     * Positions are deliberately *not* cached, because they depend on the animating capsule.
     */
    // TASK(Island-multirow): NotifyLayout gained `labelWidth` as its last constructor parameter.
    private class NotifyLayout(
        /** Widths of [IslandNotification.message], [IslandNotification.accent] and its detail tail. */
        val lineWidths: FloatArray,
        val titleWidth: Float,
        val messageLineWidth: Float,
        val contentWidth: Float,
        val contentHeight: Float,
        /**
         * Width of [IslandNotification.progressLabel]; zero for every row without a caption. It is
         * measured here with the other strings so the caption is never re-measured at draw time.
         */
        val labelWidth: Float
    )

    // TASK(Island-multirow): onEnable used to clear notifyQueue + currentNotification + notifyEndTime;
    // it now clears the row lists and the Scaffold-row bookkeeping instead.
    override fun onEnable() {
        widthSpring.value = 100f
        heightSpring.value = 22f
        alphaSpring.value = 0f

        splitWidthSpring.value = 0f
        splitHeightSpring.value = 0f
        splitAlphaSpring.value = 0f

        activeNotifications.clear()
        visibleNotifications.clear()
        rowOrder.clear()
        scaffoldRowBlocks = Int.MIN_VALUE
        scaffoldRowPresent = false
        popSpring.value = 1f
        popSpring.velocity = 0f
        lastRenderTime = System.currentTimeMillis()

        // Re-enabling rebuilds every cache from scratch, so the first frame after a reload measures
        // the text once and every frame after that reads it back. Nothing here assumes the world or
        // the player survived the toggle.
        invalidateDisplayCaches()
    }

    /**
     * Drops every display cache, so the next frame measures the HUD text and the notification layout
     * again from scratch. Cheap: a handful of field writes and no GL work.
     */
    private fun invalidateDisplayCaches() {
        segmentCache[1] = IslandSegment(segmentCache[1].icon, "")
        segmentCache[2] = IslandSegment(IslandIcon.FPS, "")
        twoSegments[1] = segmentCache[1]
        allSegments[1] = segmentCache[1]
        allSegments[2] = segmentCache[2]

        cachedPlayerName = null
        cachedThirdKind = ThirdSegment.NONE
        cachedThirdValue = Int.MIN_VALUE

        // Cleared, so the next frame re-measures every width against whatever font is loaded by then.
        segmentWidthFont = null
        separatorWidthFont = null
        separatorWidthCache = -1f

        // The measured layouts now live on the notification records, so dropping the caches means
        // asking every live row to measure itself again (a font swap is the only way to get here
        // with rows on screen). The records themselves are deliberately kept: they are just data.
        // TASK(Island-multirow): this replaced `notifyLayout = null; notifyLayoutSource = null;
        // notifyLayoutFontHeight = -1f`, because the layout now lives on each record.
        dropNotificationLayoutCache()
    }

    // TASK(Island-multirow): new function; delete it to revert.
    /**
     * Clears the per-row measured layout on every live row, and on the snapshot rows the capsule may
     * still be fading out. Nothing else about a record is mutable, so this is the whole invalidation.
     */
    private fun dropNotificationLayoutCache() {
        for (index in activeNotifications.indices) {
            val row = activeNotifications[index]
            row.cachedLayout = null
            row.cachedLayoutFont = null
            row.cachedLayoutFontHeight = -1f
        }
        for (index in visibleNotifications.indices) {
            val row = visibleNotifications[index]
            row.cachedLayout = null
            row.cachedLayoutFont = null
            row.cachedLayoutFontHeight = -1f
        }
    }
    private fun triggerToggleNotification(moduleName: String, state: Boolean) {
        val duration = (notifyDurationValue.get() * 1000).toLong()
        val scaffold = if (moduleName.equals(SCAFFOLD_NAME, ignoreCase = true)) {
            Launch.moduleManager.getModule(Scaffold::class.java)
        } else {
            null
        }

        // Scaffold always gets the wire-frame block instead of the switch, and while it is on its
        // second line carries "<blocks left>" in [textRgb] plus " - <rate> block/s" in grey.
        // The notice no longer takes the screen over: it is pushed as one more row (see
        // [addNotice]) and simply coexists with whatever else is showing.
        if (state && scaffold != null) {
            val rate = "%.2f".format(scaffold.blocksPerSecond)
            addNotice(
                IslandNotification(
                    title = "$moduleName Toggled",
                    message = "${scaffold.blocksAmount} blocks left",
                    accent = "",
                    accentColor = textRgb,
                    switchOn = true,
                    durationMs = duration,
                    detail = " - $rate block/s",
                    icon = NotifyIcon.CUBE
                )
            )
            return
        }

        addNotice(
            IslandNotification(
                title = "$moduleName Toggled",
                message = "$moduleName has been ",
                accent = if (state) "Enabled!" else "Disabled!",
                accentColor = if (state) enabledRgb else disabledRgb,
                switchOn = state,
                durationMs = duration,
                icon = if (scaffold != null) NotifyIcon.CUBE else NotifyIcon.SWITCH
            )
        )
    }

    fun showCustomNotification(title: String, message: String) {
        addNotice(
            IslandNotification(
                title = title,
                message = message,
                // No coloured status word for custom notices: the whole body shares one colour.
                accent = "",
                accentColor = textRgb,
                switchOn = true,
                durationMs = (notifyDurationValue.get() * 1000).toLong()
            )
        )
    }

    /**
     * Shows a notice now, as one more row of the stack.
     *
     * The record is stamped here, once, with its own start and expiry: that pair is what makes the
     * rows independent, and nothing else in the module ever reads or writes a shared notification
     * clock. The row is live on this very frame — there is no queue to wait in and no replacement
     * of whatever is already on screen.
     */
    private fun addNotice(notification: IslandNotification) {
        val now = System.currentTimeMillis()
        addNotificationRow(
            notification.copy(
                startedAtMs = now,
                expiresAtMs = now + notification.durationMs,
                persistent = false
            )
        )
    }

    /** Packs a plain RGB triple with a runtime alpha, so the capsule can fade in and out. */
    private fun withAlpha(rgb: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    /**
     * Index into [ARC_COSINE]/[ARC_SINE] for an outline angle in degrees. The angle is always a
     * non-negative multiple of [ARC_STEP_DEG] and never exceeds 360, so the modulo only wraps the
     * closing sample back onto the first entry.
     */
    private fun arcSample(degrees: Int): Int = (degrees / ARC_STEP_DEG) % ARC_COSINE.size

    /** Lifts every channel of a plain RGB int by [amount], clamped at 255. */
    private fun brightenRgb(rgb: Int, amount: Int): Int {
        val red = (((rgb shr 16) and 0xFF) + amount).coerceAtMost(255)
        val green = (((rgb shr 8) and 0xFF) + amount).coerceAtMost(255)
        val blue = ((rgb and 0xFF) + amount).coerceAtMost(255)
        return (red shl 16) or (green shl 8) or blue
    }

    /**
     * Lowers every channel of a plain RGB int by [amount], clamped at 0. The counterpart of
     * [brightenRgb], used for the blur layer behind the white capsule (see [CAPSULE_BLUR_DROP]),
     * where brightening would clamp straight back to white.
     */
    private fun darkenRgb(rgb: Int, amount: Int): Int {
        val red = (((rgb shr CHANNEL_RED) and 0xFF) - amount).coerceAtLeast(0)
        val green = (((rgb shr CHANNEL_GREEN) and 0xFF) - amount).coerceAtLeast(0)
        val blue = ((rgb and 0xFF) - amount).coerceAtLeast(0)
        return (red shl CHANNEL_RED) or (green shl CHANNEL_GREEN) or blue
    }

    /** Raises the RGB triple until its brightest channel reaches [ACCENT_FLOOR]. */    private fun liftToFloor(rgb: Int): Int {
        val red = (rgb shr CHANNEL_RED) and 0xFF
        val green = (rgb shr CHANNEL_GREEN) and 0xFF
        val blue = rgb and 0xFF
        val highest = max(red, max(green, blue))
        return if (highest >= ACCENT_FLOOR) rgb else brightenRgb(rgb, ACCENT_FLOOR - highest)
    }

    /** Pushes a packed ARGB colour towards black by the given factor. */
    private fun scaleArgb(argb: Int, factor: Float): Int {
        val red = ((argb shr CHANNEL_RED) and 0xFF) * factor
        val green = ((argb shr CHANNEL_GREEN) and 0xFF) * factor
        val blue = (argb and 0xFF) * factor
        val alpha = (argb ushr 24) and 0xFF
        return (alpha shl 24) or
            (red.toInt().coerceIn(0, 255) shl CHANNEL_RED) or
            (green.toInt().coerceIn(0, 255) shl CHANNEL_GREEN) or
            blue.toInt().coerceIn(0, 255)
    }

    /**
     * Pushes a packed ARGB colour towards white by the given fraction. Used for the toggle's knob,
     * which has to stay the lighter half of the switch whatever the accent colour is.
     */
    private fun blendToWhite(argb: Int, amount: Float): Int {
        val white = 1f - amount
        val red = ((argb shr CHANNEL_RED) and 0xFF) * white + 255f * amount
        val green = ((argb shr CHANNEL_GREEN) and 0xFF) * white + 255f * amount
        val blue = (argb and 0xFF) * white + 255f * amount
        val alpha = (argb ushr 24) and 0xFF
        return (alpha shl 24) or
            (red.toInt().coerceIn(0, 255) shl CHANNEL_RED) or
            (green.toInt().coerceIn(0, 255) shl CHANNEL_GREEN) or
            blue.toInt().coerceIn(0, 255)
    }

    /**
     * Re-measures the HUD text, and only when one of the underlying values changed.
     *
     * FPS comes from [Minecraft.getDebugFPS], which MineCraft itself only refreshes once a second,
     * and Scaffold's block count only moves when a block is placed, so comparing the displayed
     * numbers is enough: any frame whose text differs from the cached one rebuilds it, and every
     * other frame reuses the strings and the measured widths instead of walking them again.
     */
    private fun updateTextCaches(
        font: FontRenderer,
        playerName: String,
        showBlocks: Boolean,
        showFps: Boolean,
        blocks: Int,
        fps: Int
    ) {
        val thirdKind = when {
            showBlocks -> ThirdSegment.BLOCKS
            showFps -> ThirdSegment.FPS
            else -> ThirdSegment.NONE
        }

        // A different font means every cached width is wrong, and only the widths: the strings and
        // the name/value caches below are font independent, so they are kept as they are.
        if (font !== segmentWidthFont) {
            segmentWidthFont = font
            segmentTextWidths[0] = measureWidth(font, segmentCache[0].text)
            segmentTextWidths[1] = measureWidth(font, segmentCache[1].text)
            segmentTextWidths[2] = measureWidth(font, segmentCache[2].text)
            twoSegmentWidths[0] = segmentTextWidths[0]
            twoSegmentWidths[1] = segmentTextWidths[1]
            allSegmentWidths[0] = segmentTextWidths[0]
            allSegmentWidths[1] = segmentTextWidths[1]
            allSegmentWidths[2] = segmentTextWidths[2]
        }

        if (playerName != cachedPlayerName) {
            // New player (or a respawn renamed it): rebuild the user slot only.
            val userSegment = segmentCache[1]
            val userTextWidth = measureWidth(font, playerName)
            segmentCache[1] = IslandSegment(userSegment.icon, playerName)
            segmentTextWidths[1] = userTextWidth
            twoSegments[1] = segmentCache[1]
            twoSegmentWidths[1] = userTextWidth
            allSegments[1] = segmentCache[1]
            allSegmentWidths[1] = userTextWidth
            cachedPlayerName = playerName
        }

        if (thirdKind == ThirdSegment.NONE) {
            cachedThirdKind = ThirdSegment.NONE
            cachedThirdValue = Int.MIN_VALUE
            return
        }
        val thirdValue = if (thirdKind == ThirdSegment.BLOCKS) blocks else fps
        if (thirdKind == cachedThirdKind && thirdValue == cachedThirdValue) return

        val text = thirdValue.toString()
        // Rewrite the slot in place and mirror it into the three element view, so the per-frame draw
        // loop allocates nothing and the icon only changes when the segment switches kind.
        val thirdWidth = measureWidth(font, text)
        segmentCache[2] = segmentCache[2].copy(
            icon = if (thirdKind == ThirdSegment.BLOCKS) IslandIcon.BLOCKS else IslandIcon.FPS,
            text = text
        )
        segmentTextWidths[2] = thirdWidth
        allSegments[2] = segmentCache[2]
        allSegmentWidths[2] = thirdWidth
        cachedThirdKind = thirdKind
        cachedThirdValue = thirdValue
    }

    /** Width of [text] in [font]. Only ever called from the cache refreshers, never per frame. */
    private fun measureWidth(font: FontRenderer, text: String): Float = font.getStringWidth(text).toFloat()

    // TASK(Island-multirow): layoutFor REPLACES the old `updateNotificationLayout(font, fontHeight,
    // notification)`. To revert, restore that function's body and its two fields
    // (`notifyLayout`, `notifyLayoutSource`, `notifyLayoutFont`, `notifyLayoutFontHeight`).
    /**
     * Layout of [notification], measured at most once per record and then cached *on the record*.
     *
     * The old version keyed a single shared cache on "the notification currently on screen", which
     * only works while exactly one row exists. Here the cache travels with the row, so N rows cost
     * one measuring pass per row for the whole time they are visible: the dependency set is
     * unchanged (the record's identity is implicit, plus the font reference and the font height),
     * it is just no longer global. Nothing in this refactor measures a string per frame.
     */
    private fun layoutFor(font: FontRenderer, fontHeight: Float, notification: IslandNotification): NotifyLayout {
        val cached = notification.cachedLayout
        if (cached != null && font === notification.cachedLayoutFont &&
            fontHeight == notification.cachedLayoutFontHeight
        ) {
            return cached
        }

        val titleWidth = measureWidth(font, notification.title)
        val messageWidth = measureWidth(font, notification.message)
        val accentWidth = measureWidth(font, notification.accent)
        val detailWidth = if (notification.detail.isNotEmpty()) {
            measureWidth(font, notification.detail)
        } else {
            0f
        }
        val labelWidth = if (notification.progressLabel.isNotEmpty()) {
            measureWidth(font, notification.progressLabel)
        } else {
            0f
        }

        val detailTail = if (notification.detail.isNotEmpty()) DETAIL_GAP + detailWidth else 0f
        val messageLineWidth = messageWidth + accentWidth + detailTail
        val contentWidth = NOTIFY_TILE_SIZE + NOTIFY_TILE_GAP + max(titleWidth, messageLineWidth)
        val textHeight = fontHeight * 2f + NOTIFY_ROW_GAP
        // The bar height is a per-row property now (the Scaffold row's caption needs a taller bar),
        // so it is part of the measured record rather than a single module-wide constant.
        val contentHeight = max(NOTIFY_TILE_SIZE, textHeight) + NOTIFY_PROGRESS_GAP +
            progressBarHeight(notification) + NOTIFY_PROGRESS_PADDING

        val layout = NotifyLayout(
            floatArrayOf(messageWidth, accentWidth, detailWidth),
            titleWidth,
            messageLineWidth,
            contentWidth,
            contentHeight,
            labelWidth
        )
        notification.cachedLayout = layout
        notification.cachedLayoutFont = font
        notification.cachedLayoutFontHeight = fontHeight
        return layout
    }

    // TASK(Island-multirow): everything from here down to drawNotificationRow is new.
    /** Height of [notification]'s progress bar: the caption row gets the taller labelled bar. */
    private fun progressBarHeight(notification: IslandNotification): Float =
        if (notification.progressLabel.isNotEmpty()) PROGRESS_LABEL_HEIGHT else PROGRESS_HEIGHT

    /** A notice row's fade in, out and combined opacity at [now], all from its own two stamps. */
    private fun notificationRowAlpha(notification: IslandNotification, now: Long): Float {
        val inFade = ((now - notification.startedAtMs).toFloat() / NOTIFY_FADE_IN_MS).coerceIn(0f, 1f)
        val outFade = if (notification.persistent) {
            1f
        } else {
            ((notification.expiresAtMs - now).toFloat() / NOTIFY_FADE_OUT_MS).coerceIn(0f, 1f)
        }
        return inFade * outFade
    }

    /**
     * Copies the live rows into [visibleNotifications], pruning the ones that have expired first.
     *
     * TASK(Island-restore-fix): the snapshot is now rebuilt from the *pruned* rows on every frame and
     * the empty case drains it as well. The old body returned early while leaving the previous
     * frame's rows in [visibleNotifications], so once the last notice expired `rowCount` - and with
     * it `isNotifying`, which is read straight off this list in onRender2D - stayed non-zero forever
     * and the capsule kept the notification targets instead of falling back to the three-segment HUD.
     * Draining costs nothing visually: a transient row's own opacity already reaches exactly 0 at
     * `expiresAtMs` (see [notificationRowAlpha]), which is the very instant [pruneExpiredNotifications]
     * drops it, so a snapshot kept past that point could only ever be drawn at zero alpha. The
     * capsule's own alpha spring still takes the stack away, exactly like the single notification
     * used to. The rows are immutable records, so a snapshot cannot go stale.
     */
    private fun fillVisibleNotifications(now: Long) {
        pruneExpiredNotifications(now)
        // TASK(Island-restore-fix): the unconditional rebuild is the fix. Clearing before the empty
        // check is what lets `rowCount` fall back to 0, so `isNotifying` goes false and
        // `targetWidth`/`targetHeight` return to the base HUD targets. To revert, delete this clear
        // and put the old `if (activeNotifications.isEmpty()) return` back between the two clears.
        visibleNotifications.clear()
        if (activeNotifications.isEmpty()) return

        visibleNotifications.addAll(activeNotifications)
    }

    /**
     * Drops every row whose own deadline has passed (the persistent Scaffold row has none).
     *
     * No width bookkeeping is needed here: the draw loop reads each row's cached content width out
     * of the snapshot it was handed, so a row leaving simply stops contributing to it.
     */
    private fun pruneExpiredNotifications(now: Long) {
        var index = 0
        while (index < activeNotifications.size) {
            if (activeNotifications[index].isAliveAt(now)) {
                index++
            } else {
                activeNotifications.removeAt(index)
            }
        }
    }

    /**
     * Appends [notification] to the live rows.
     *
     * The notice is live on this very frame (rows no longer wait behind one another), and the pop
     * spring is re-armed so it bounces in. The persistent Scaffold row is special-cased: it keeps
     * its place at the bottom of the stack instead of being appended above it, so the stack grows
     * downwards for transient notices and the Scaffold line stays where the user last saw it.
     */
    private fun addNotificationRow(notification: IslandNotification) {
        if (notification.persistent) {
            for (index in activeNotifications.indices) {
                if (activeNotifications[index].persistent) {
                    activeNotifications[index] = notification
                    return
                }
            }
            activeNotifications.add(notification)
        } else {
            // Insert before the persistent row if there is one, so Scaffold always stays last.
            var insertAt = activeNotifications.size
            for (index in activeNotifications.indices) {
                if (activeNotifications[index].persistent) {
                    insertAt = index
                    break
                }
            }
            activeNotifications.add(insertAt, notification)
        }

        while (activeNotifications.size > MAX_ACTIVE_NOTIFICATIONS) {
            activeNotifications.removeAt(0)
        }
        // More rows than may be shown: drop the oldest transient one, never the persistent row.
        while (transientRowCount() > MAX_NOTIFY_ROWS) {
            var dropped = false
            for (index in activeNotifications.indices) {
                if (!activeNotifications[index].persistent) {
                    activeNotifications.removeAt(index)
                    dropped = true
                    break
                }
            }
            if (!dropped) break
        }

        popSpring.value = NOTIFY_POP_START
        popSpring.velocity = 0f
    }

    /** How many non-persistent rows are alive, i.e. how much of the stack may be evicted. */
    private fun transientRowCount(): Int {
        var count = 0
        for (index in activeNotifications.indices) {
            if (!activeNotifications[index].persistent) count++
        }
        return count
    }

    /**
     * Keeps the persistent Scaffold row in step with Scaffold's state.
     *
     * The row exists exactly while `scaffold.state` is true, which is what makes it a status line
     * rather than a notice: it is re-built only when the live block count changes (so its measured
     * layout is recomputed only then), and it is removed the moment Scaffold is switched off, which
     * is what returns the island to its three-segment HUD form.
     */
    private fun updateScaffoldRow(scaffold: Scaffold?, now: Long) {
        if (scaffold == null || !scaffold.state) {
            if (scaffoldRowPresent) {
                for (index in activeNotifications.indices) {
                    if (activeNotifications[index].persistent) {
                        activeNotifications.removeAt(index)
                        break
                    }
                }
                scaffoldRowPresent = false
                scaffoldRowBlocks = Int.MIN_VALUE
            }
            return
        }

        val blocks = scaffold.blocksAmount
        if (scaffoldRowPresent && blocks == scaffoldRowBlocks) return

        val rate = "%.2f".format(scaffold.blocksPerSecond)
        addNotificationRow(
            IslandNotification(
                title = "$SCAFFOLD_NAME On",
                // The exact count, so the bar's caption is never the only place it is readable.
                message = "$blocks blocks left",
                accent = "",
                accentColor = textRgb,
                switchOn = true,
                durationMs = 0L,
                detail = " - $rate block/s",
                icon = NotifyIcon.CUBE,
                startedAtMs = now,
                expiresAtMs = Long.MAX_VALUE,
                persistent = true,
                progressLabel = "$blocks blocks"
            )
        )
        scaffoldRowPresent = true
        scaffoldRowBlocks = blocks
    }

    // TASK(Island-multirow): drawNotificationRow is new (the per-row replacement for the inline
    // single-notification draw block that used to sit in each of the two branches of onRender2D).
    /**
     * One row drawn with its own pop, at its own vertical slot.
     *
     * The pop used to be applied once around the single notification; with a stack, each row scales
     * about its own centre so rows do not slide out of alignment while the spring settles. Both
     * [rowAlpha] and the caller's alpha base are already resolved by the time this is called.
     */
    private fun drawNotificationRow(
        notification: IslandNotification,
        contentX: Float,
        contentY: Float,
        fontHeight: Float,
        rowAlpha: Float,
        pop: Float,
        progress: Float
    ) {
        val a = rowAlpha.coerceIn(0f, 1f)
        if (a <= 0.02f) return

        val layout = notification.cachedLayout ?: return
        val tileSize = NOTIFY_TILE_SIZE
        val rowWidth = layout.contentWidth
        val rowHeight = layout.contentHeight
        val textX = contentX + tileSize + NOTIFY_TILE_GAP
        val titleY = contentY + 2f
        val messageY = titleY + fontHeight + NOTIFY_ROW_GAP
        val barHeight = progressBarHeight(notification)
        val barX1 = contentX
        val barY1 = contentY + rowHeight - NOTIFY_PROGRESS_PADDING - barHeight
        val alphaInt = (a * 255).toInt().coerceIn(0, 255)

        val centerX = contentX + rowWidth / 2f
        val centerY = contentY + rowHeight / 2f

        GlStateManager.pushMatrix()
        try {
            GlStateManager.translate(centerX, centerY, 0f)
            GlStateManager.scale(pop, pop, 1f)
            GlStateManager.translate(-centerX, -centerY, 0f)

            drawNotifyIconTile(contentX, contentY, tileSize, a, notification.switchOn, notification.icon)
            drawNotificationLines(notification, textX, titleY, messageY, alphaInt, layout.lineWidths)
            // The caption (if any) belongs on top of the bar, so the bar goes down first.
            drawNotificationProgress(barX1, barY1, rowWidth, progress, a, barHeight)
            drawProgressLabel(notification, barX1, barY1, rowWidth, barHeight, alphaInt)
        } finally {
            GlStateManager.popMatrix()
        }
    }

    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        val mc = Minecraft.getMinecraft()
        if (mc.thePlayer == null || mc.theWorld == null) return
        val font = Fonts.font35

        val currentTime = System.currentTimeMillis()
        val delta = (currentTime - lastRenderTime).toFloat() / 16.6666f
        lastRenderTime = currentTime
        val safeDelta = delta.coerceIn(0.1f, 3.0f)

        try {
            val allModules = Launch.moduleManager.modules
            if (isFirstLoad) {
                for (m in allModules) moduleStates[m.name] = m.state
                isFirstLoad = false
            }

            for (m in allModules) {
                if (m.name == this.name) continue
                val lastState = moduleStates[m.name] ?: false
                if (m.state != lastState) {
                    triggerToggleNotification(m.name, m.state)
                    moduleStates[m.name] = m.state
                }
            }
        } catch (e: Exception) {
        }

        // Fetch the stack of live rows. Rows expire on their own stamps, so this is the only place
        // that decides what is still on screen; the Scaffold status row is added/refreshed here too,
        // which is what keeps it in step with the module's state rather than with a timer.
        // TASK(Island-multirow): these three lines replace
        // `startNextNotificationIfIdle(currentTime); val notification = currentNotification;
        // val isNotifying = notification != null && currentTime < notifyEndTime`.
        val scaffoldRow = Launch.moduleManager.getModule(Scaffold::class.java)
        updateScaffoldRow(scaffoldRow, currentTime)
        fillVisibleNotifications(currentTime)
        val rowCount = visibleNotifications.size
        val isNotifying = rowCount > 0

        val iconSize = 12f
        val iconGap = 2f
        val separator = " · "

        // The separator never changes, so its width is measured once per font instead of per frame.
        if (font !== separatorWidthFont) {
            separatorWidthFont = font
            separatorWidthCache = font.getStringWidth(separator).toFloat()
        }
        val sepWidth = separatorWidthCache

        // Third segment shows Scaffold's remaining blocks while Scaffold is on, FPS otherwise.
        val scaffold = scaffoldRow
        val showBlocks = scaffold?.state == true
        val showFps = showFpsValue.get()

        // Refresh the cached HUD text (and only then re-measure it), then read the three entries back
        // out. Nothing below this point measures a string or builds a segment again.
        updateTextCaches(
            font,
            mc.thePlayer.name,
            showBlocks,
            showFps,
            if (scaffold == null) 0 else scaffold.blocksAmount,
            Minecraft.getDebugFPS()
        )

        // A two element view onto the cache while the third segment is off, and the full three
        // element view otherwise. Both already exist, so no array is built here.
        val baseSegments = if (showBlocks || showFps) allSegments else twoSegments
        val baseTextWidths = if (showBlocks || showFps) allSegmentWidths else twoSegmentWidths
        val baseCount = baseSegments.size

        // Purely local: recomputed every frame from the cached widths, so the layout still tracks the
        // animation, and nothing is written back to a field from here.
        var baseTotalWidth = 0f
        for (index in 0 until baseCount) {
            baseTotalWidth += iconSize + iconGap + baseTextWidths[index]
            if (index < baseCount - 1) baseTotalWidth += sepWidth
        }

        val fontHeight = font.FONT_HEIGHT.toFloat()

        // While no row is live there is no text to measure, so the layout collapses to zero; the
        // targets below ignore it whenever `isNotifying` is false. Mid-fade the rows are still in the
        // snapshot, so their cached layouts stay in use.
        //
        // Measuring happens once per row: [layoutFor] writes the result onto the record, so the loop
        // below only re-reads a field. The aggregate height is the sum of the rows' own heights plus
        // the gaps between them, and the width is the widest row's content width, both recomputed
        // here from cached values.
        var widestRow = 0f
        var stackHeight = 0f
        for (index in 0 until rowCount) {
            val row = visibleNotifications[index]
            val cached = row.cachedLayout ?: layoutFor(font, fontHeight, row)
            if (cached.contentWidth > widestRow) widestRow = cached.contentWidth
            stackHeight += cached.contentHeight
            if (index > 0) stackHeight += NOTIFY_STACK_GAP
        }

        val notifyContentWidth = widestRow
        val notifyStackHeight = stackHeight
        val notifyHeight = notifyStackHeight + 14f

        val targetBaseWidth = baseTotalWidth + 24f
        val targetNotifyWidth = notifyContentWidth + 30f

        val tension = speedValue.get()
        val friction = 1.0f - bouncinessValue.get()

        val targetWidth: Float
        val targetHeight: Float
        val targetAlpha: Float

        val targetSplitWidth: Float
        val targetSplitHeight: Float
        val targetSplitAlpha: Float

        if (noDisappearValue.get()) {
            targetWidth = targetBaseWidth
            targetHeight = 22f
            targetAlpha = 0f

            targetSplitWidth = if (isNotifying) targetNotifyWidth else 0f
            targetSplitHeight = if (isNotifying) notifyHeight else 0f
            targetSplitAlpha = if (isNotifying) 1f else 0f
        } else {
            targetWidth = if (isNotifying) targetNotifyWidth else targetBaseWidth
            targetHeight = if (isNotifying) notifyHeight else 22f
            targetAlpha = if (isNotifying) 1f else 0f

            targetSplitWidth = 0f
            targetSplitHeight = 0f
            targetSplitAlpha = 0f
        }

        widthSpring.update(targetWidth, tension, friction, safeDelta)
        heightSpring.update(targetHeight, tension, friction, safeDelta)
        alphaSpring.update(targetAlpha, tension * 0.8f, 0.4f, safeDelta)

        splitWidthSpring.update(targetSplitWidth, tension, friction * 0.9f, safeDelta)
        splitHeightSpring.update(targetSplitHeight, tension * 1.1f, friction * 0.85f, safeDelta)
        splitAlphaSpring.update(targetSplitAlpha, tension * 1.2f, 0.3f, safeDelta)

        // Pop-in spring. `safeDelta` can reach 3 frames after a stall, which an explicit Euler step
        // would overshoot badly, so the multiplier handed to the draw code is clamped: the bounce
        // never scales a row past 1.3 nor visibly crushes it below 0.86.
        popSpring.update(1f, tension, friction, safeDelta)
        val pop = popSpring.value.coerceIn(0.86f, 1.3f)

        // The order the rows are drawn in, bottom line last: the persistent Scaffold row is kept at
        // the end of the list by [addNotificationRow], so no sorting is needed here.
        rowOrder.clear()
        for (index in 0 until rowCount) rowOrder.add(visibleNotifications[index])

        val sr = ScaledResolution(mc)
        val islandWidth = max(20f, widthSpring.value)
        val islandHeight = max(10f, heightSpring.value)

        val x1 = (sr.scaledWidth / 2f) - (islandWidth / 2f)
        val y1 = 6f
        val x2 = x1 + islandWidth
        val y2 = y1 + islandHeight
        val radius = cornerRadiusValue.get()

        try {
            GlStateManager.pushMatrix()
            GlStateManager.enableBlend()
            GlStateManager.disableTexture2D()

            if (blurValue.get()) {
                RenderUtils.glColor(withAlpha(capsuleBlurRgb, (bgAlphaValue.get() * 0.7f).toInt()))
                RenderUtils.fastRoundedRect(x1, y1, x2, y2, radius)
            }
            RenderUtils.glColor(withAlpha(capsuleRgb, bgAlphaValue.get().toInt()))
            RenderUtils.fastRoundedRect(x1, y1, x2, y2, radius)
            // 液态玻璃：胶囊上叠流动高光 + 玻璃边（白底上很淡，但一动就能看出来）。
            Glass.sheen(x1, y1, x2, y2, radius, 1f, 11)
            Glass.edges(x1, y1, x2, y2, radius, 1f)
            GlStateManager.enableTexture2D()

            val alphaIntNotify = (alphaSpring.value * 255).toInt().coerceIn(0, 255)
            val alphaIntBase = if (noDisappearValue.get()) {
                (bgAlphaValue.get()).toInt().coerceIn(0, 255)
            } else {
                ((1f - alphaSpring.value) * 255).toInt().coerceIn(0, 255)
            }

            val splitAlphaVal = splitAlphaSpring.value
            val independentSplitAlpha = (255f * splitAlphaVal).toInt().coerceIn(0, 255)

            val isCutApart = cutApartValue.get()
            val splitWidth = splitWidthSpring.value
            val splitHeight = splitHeightSpring.value

            val visualSy1 = if (isCutApart) y2 + 3f else y2
            val rectSy1 = if (isCutApart) visualSy1 else visualSy1 - radius
            val sx1 = (sr.scaledWidth / 2f) - (splitWidth / 2f)
            val sx2 = sx1 + splitWidth
            val sy2 = visualSy1 + splitHeight
            val splitRadius = radius * 0.85f

            if (noDisappearValue.get() && splitAlphaVal > 0.02f) {
                val factor = sr.scaleFactor
                glEnable(GL_SCISSOR_TEST)
                // TASK(Scissor-nonnegative):宽高都夹到 >= 0。glScissor 收到负数会报 GL_INVALID_VALUE
                // (1281),而这里的高度是"屏幕高度 - 胶囊底边",只要胶囊被放到屏幕下缘之外就会变负;
                // 其余几个 glScissor 调用点(Interface / RenderUtils.makeScissorBox)同样已夹。
                glScissor(0, 0, mc.displayWidth.coerceAtLeast(0), ((sr.scaledHeight - y2) * factor).toInt().coerceAtLeast(0))

                GlStateManager.disableTexture2D()
                if (blurValue.get()) {
                    RenderUtils.glColor(withAlpha(capsuleBlurRgb, (independentSplitAlpha * 0.7f).toInt()))
                    RenderUtils.fastRoundedRect(sx1, rectSy1, sx2, sy2, splitRadius)
                }
                RenderUtils.glColor(withAlpha(capsuleRgb, independentSplitAlpha))
                RenderUtils.fastRoundedRect(sx1, rectSy1, sx2, sy2, splitRadius)
                GlStateManager.enableTexture2D()
                glDisable(GL_SCISSOR_TEST)
            }

            if (borderValue.get()) {
                val thickness = borderThicknessValue.get()
                val mainBorderColor = withAlpha(borderRgb, max(50, alphaIntBase))
                val splitBorderColor = withAlpha(borderRgb, independentSplitAlpha)

                if (noDisappearValue.get() && splitAlphaVal > 0.02f) {
                    if (!isCutApart) {
                        val unifiedColor = withAlpha(borderRgb, max(alphaIntBase, independentSplitAlpha))
                        drawConnectedOutline(x1, y1, x2, y2, radius, sx1, sx2, sy2, splitRadius, thickness, unifiedColor)
                    } else {
                        drawRoundedOutline(x1, y1, x2, y2, radius, thickness, mainBorderColor)
                        drawRoundedOutline(sx1, visualSy1, sx2, sy2, splitRadius, thickness, splitBorderColor)
                    }
                } else {
                    drawRoundedOutline(x1, y1, x2, y2, radius, thickness, mainBorderColor)
                }
            }

            if (alphaIntBase > 5) {
                var currentX = x1 + (islandWidth - baseTotalWidth) / 2f
                val baseColor = withAlpha(textRgb, alphaIntBase)
                val separatorColor = withAlpha(separatorRgb, alphaIntBase)
                val iconAlpha = alphaIntBase / 255f
                val baseIconY = y1 + (islandHeight - iconSize) / 2f
                val baseTextY = y1 + (islandHeight - fontHeight) / 2f + 2f

                for (index in 0 until baseCount) {
                    val segment = baseSegments[index]
                    drawIslandIcon(segment.icon, currentX, baseIconY, iconSize, iconAlpha)
                    currentX += iconSize + iconGap
                    font.drawString(segment.text, currentX, baseTextY, baseColor)
                    // Cached width, not a fresh measurement: this used to be the second
                    // getStringWidth() call per segment on every single frame.
                    currentX += baseTextWidths[index]
                    if (index < baseCount - 1) {
                        font.drawString(separator, currentX, baseTextY, separatorColor)
                        currentX += sepWidth
                    }
                }
            }

            // TASK(Island-multirow): both branches below are new. They replace the two single
            // notification draw blocks (the normal one and the NoDisappear split one); if you keep
            // one, keep both, or the two modes will disagree about what is on screen.
            // The normal branch: the stacked rows *are* the island. Every row is drawn with its own
            // fade ([notificationRowAlpha]) and its own pop, and the whole stack is centred together
            // so a row appearing or expiring only changes the capsule's size.
            if (rowCount > 0 && !noDisappearValue.get() && alphaIntNotify > 5) {
                val stackTop = y1 + (islandHeight - notifyStackHeight) / 2f

                for (index in 0 until rowCount) {
                    val row = rowOrder[index]
                    val rowLayout = row.cachedLayout ?: continue
                    val rowAlpha = notificationRowAlpha(row, currentTime) * (alphaIntNotify / 255f)
                    if (rowAlpha <= 0.02f) continue

                    val rowContentWidth = rowLayout.contentWidth
                    val rowContentHeight = rowLayout.contentHeight
                    val contentX = x1 + (islandWidth - rowContentWidth) / 2f
                    var contentY = stackTop
                    for (previous in 0 until index) {
                        contentY += (rowOrder[previous].cachedLayout?.contentHeight ?: 0f) + NOTIFY_STACK_GAP
                    }

                    drawNotificationRow(
                        row,
                        contentX,
                        contentY,
                        fontHeight,
                        rowAlpha,
                        pop,
                        rowProgress(row, currentTime)
                    )
                }
            }

            // The NoDisappear branch: the same stack, in the detached capsule below the island. Only
            // the alpha source differs, so the rows come from the same snapshot and the same cached
            // layouts; skipping it is what the old code did when the split capsule was too small.
            if (rowCount > 0 && noDisappearValue.get() && splitAlphaVal > 0.02f) {
                if (splitHeight > notifyStackHeight + 6f && splitWidth > notifyContentWidth * 0.6f) {
                    val stackTop = visualSy1 + (splitHeight - notifyStackHeight) / 2f
                    for (index in 0 until rowCount) {
                        val row = rowOrder[index]
                        val rowLayout = row.cachedLayout ?: continue
                        val rowAlpha = notificationRowAlpha(row, currentTime) * (independentSplitAlpha / 255f)
                        if (rowAlpha <= 0.02f) continue

                        val rowContentWidth = rowLayout.contentWidth
                        val contentX = sx1 + (splitWidth - rowContentWidth) / 2f
                        var contentY = stackTop
                        for (previous in 0 until index) {
                            contentY += (rowOrder[previous].cachedLayout?.contentHeight ?: 0f) + NOTIFY_STACK_GAP
                        }

                        drawNotificationRow(
                            row,
                            contentX,
                            contentY,
                            fontHeight,
                            rowAlpha,
                            pop,
                            rowProgress(row, currentTime)
                        )
                    }
                }
            }

        } catch (e: Exception) {
        } finally {
            GlStateManager.color(1f, 1f, 1f, 1f)
            GlStateManager.enableTexture2D()
            GlStateManager.disableBlend()
            GlStateManager.popMatrix()
        }
    }

    private fun drawRoundedOutline(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, thickness: Float, color: Int) {
        val f = (color shr 24 and 255) / 255.0f
        val f1 = (color shr 16 and 255) / 255.0f
        val f2 = (color shr 8 and 255) / 255.0f
        val f3 = (color and 255) / 255.0f
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        GlStateManager.color(f1, f2, f3, f)
        // TASK(LineWidth-positive):glLineWidth(<= 0) 会报 GL_INVALID_VALUE(1281)。设置项的最小值是
        // 0.5,但配置文件读进来是不夹的(FloatValue.fromJson),所以这里兜一层。
        GL11.glLineWidth(thickness.coerceAtLeast(0.5f))
        GL11.glEnable(GL11.GL_LINE_SMOOTH)
        GL11.glBegin(GL11.GL_LINE_LOOP)

        // Four quarter arcs, walked over exactly the ranges the original four Math.sin/Math.cos loops
        // walked: 0..90, 90..180, 180..270 and 270..360, shared endpoints included on both sides. The
        // theta values, and therefore every vertex, are identical - only the sin/cos source changed.
        // The old code rebuilt both per vertex through Math.toRadians, which boxes two Doubles for
        // every one of the 76 vertices, several times per frame.
        for (theta in 0..90 step ARC_STEP_DEG) {
            val sample = arcSample(theta)
            GL11.glVertex2d(x1 + radius - ARC_SINE[sample] * radius, y1 + radius - ARC_COSINE[sample] * radius)
        }
        for (theta in 90..180 step ARC_STEP_DEG) {
            val sample = arcSample(theta)
            GL11.glVertex2d(x1 + radius - ARC_SINE[sample] * radius, y2 - radius - ARC_COSINE[sample] * radius)
        }
        for (theta in 180..270 step ARC_STEP_DEG) {
            val sample = arcSample(theta)
            GL11.glVertex2d(x2 - radius - ARC_SINE[sample] * radius, y2 - radius - ARC_COSINE[sample] * radius)
        }
        for (theta in 270..360 step ARC_STEP_DEG) {
            val sample = arcSample(theta)
            GL11.glVertex2d(x2 - radius - ARC_SINE[sample] * radius, y1 + radius - ARC_COSINE[sample] * radius)
        }

        GL11.glEnd()
        GL11.glDisable(GL11.GL_LINE_SMOOTH)
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun drawConnectedOutline(
        x1: Float, y1: Float, x2: Float, y2: Float, r1: Float,
        sx1: Float, sx2: Float, sy2: Float, r2: Float,
        thickness: Float, color: Int
    ) {
        val f = (color shr 24 and 255) / 255.0f
        val f1 = (color shr 16 and 255) / 255.0f
        val f2 = (color shr 8 and 255) / 255.0f
        val f3 = (color and 255) / 255.0f
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        GlStateManager.color(f1, f2, f3, f)
        // TASK(LineWidth-positive):同 drawRoundedOutline,见那里的说明。
        GL11.glLineWidth(thickness.coerceAtLeast(0.5f))
        GL11.glEnable(GL11.GL_LINE_SMOOTH)
        GL11.glBegin(GL11.GL_LINE_LOOP)

        // Same vertices, same order and the same count as the original loops: the two straight
        // connectors sit right where the old code put them, and both arcs of each of the two rounded
        // rectangles are walked over their original 90..180 / 180..270 ranges, so the corner samples
        // they share are submitted twice exactly as before. Only the Math.sin/Math.cos/
        // Math.toRadians work is gone. This outline is used whenever NoDisappear and CutApart are both
        // off, and the old form boxed two Doubles per vertex.
        for (theta in 0..90 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(x1 + r1 - sine * r1, y1 + r1 - cosine * r1)
        }
        for (theta in 90..180 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(x1 + r1 - sine * r1, y2 - r1 - cosine * r1)
        }

        GL11.glVertex2d(sx1.toDouble(), y2.toDouble())

        for (theta in 90..180 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(sx1 + r2 - sine * r2, sy2 - r2 - cosine * r2)
        }
        for (theta in 180..270 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(sx2 - r2 - sine * r2, sy2 - r2 - cosine * r2)
        }

        GL11.glVertex2d(sx2.toDouble(), y2.toDouble())

        for (theta in 180..270 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(x2 - r1 - sine * r1, y2 - r1 - cosine * r1)
        }
        for (theta in 270..360 step ARC_STEP_DEG) {
            val cosine = ARC_COSINE[arcSample(theta)]
            val sine = ARC_SINE[arcSample(theta)]
            GL11.glVertex2d(x2 - r1 - sine * r1, y1 + r1 - cosine * r1)
        }

        GL11.glEnd()
        GL11.glDisable(GL11.GL_LINE_SMOOTH)
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    /**
     * An iOS style toggle built from two rounded rectangles: the track pill (radius = half its
     * height) and the round knob (radius = its own half). The knob sits right when on, left when
     * off, so no texture is needed for either state.
     *
     * Both colours are handed in by [drawNotifyIconTile] rather than read from a fixed palette, so
     * the switch follows the AccentColor setting exactly like the card it sits on. The knob colour is
     * always the lighter of the two, which keeps the switch readable on a card of any brightness.
     */
    private fun drawToggleSwitch(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        on: Boolean,
        alpha: Float,
        trackRgb: Int,
        knobRgb: Int
    ) {
        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0.02f) return

        val trackRadius = height / 2f
        val knobRadius = trackRadius - 1.5f
        if (knobRadius <= 0.5f || width <= height) return

        val alphaInt = (a * 255).toInt().coerceIn(0, 255)
        val trackColor = withAlpha(trackRgb, alphaInt)
        val knobColor = withAlpha(knobRgb, alphaInt)

        RenderUtils.drawRoundedRect(x, y, x + width, y + height, trackRadius, trackColor)

        val knobCenterX = if (on) x + width - trackRadius else x + trackRadius
        val knobCenterY = y + trackRadius
        RenderUtils.drawRoundedRect(
            knobCenterX - knobRadius,
            knobCenterY - knobRadius,
            knobCenterX + knobRadius,
            knobCenterY + knobRadius,
            knobRadius,
            knobColor
        )
    }

    /**
     * Both text rows of a notification, drawn left aligned at [textX].
     *
     * The second row is up to three pieces in a row: the dark head ([IslandNotification.message] in
     * [textRgb]), the coloured status word ([IslandNotification.accent]) and the grey tail
     * ([IslandNotification.detail]). Empty pieces simply take no space.
     *
     * [lineWidths] carries the three pieces' widths, already measured by
     * [layoutFor] for this very notification, so the tails continue from the measured
     * head without measuring anything again.
     */
    private fun drawNotificationLines(
        notification: IslandNotification,
        textX: Float,
        titleY: Float,
        messageY: Float,
        alphaInt: Int,
        lineWidths: FloatArray
    ) {
        val font = Fonts.font35
        val messageColor = withAlpha(textRgb, alphaInt)
        val messageWidth = lineWidths[LINE_MESSAGE]
        val accentWidth = lineWidths[LINE_ACCENT]

        font.drawString(notification.title, textX, titleY, messageColor)
        font.drawString(notification.message, textX, messageY, messageColor)

        // Both tails continue from the measured head, so nothing overlaps whatever font is loaded.
        var tailX = textX + messageWidth
        if (notification.accent.isNotEmpty()) {
            font.drawString(notification.accent, tailX, messageY, withAlpha(notification.accentColor, alphaInt))
            tailX += accentWidth
        }
        if (notification.detail.isNotEmpty()) {
            font.drawString(notification.detail, tailX + DETAIL_GAP, messageY, withAlpha(detailRgb, alphaInt))
        }
    }

    /**
     * The bar along a row's bottom inner edge.
     *
     * [progress] is *not* always "time remaining" any more: a transient row hands its own remaining
     * fraction (so each row carries its own little countdown), while the persistent Scaffold row
     * hands the constant 1f, because a status line has no deadline to count down and a bar that
     * drained on its own would read as one. What that row has to say travels in its caption.
     */
    private fun drawNotificationProgress(
        x: Float,
        y: Float,
        width: Float,
        progress: Float,
        alpha: Float,
        // TASK(Island-multirow): barHeight is the new sixth parameter (the old signature ended at
        // `alpha`). The body now uses `barHeight` wherever it used to read PROGRESS_HEIGHT.
        barHeight: Float
    ) {
        val a = alpha.coerceIn(0f, 1f)
        val fraction = progress.coerceIn(0f, 1f)
        if (a <= 0.02f || fraction <= 0f || width <= barHeight || barHeight <= 0f) return

        val alphaInt = (a * 255).toInt().coerceIn(0, 255)
        val radius = barHeight / 2f
        val filledWidth = width * fraction
        if (filledWidth < barHeight) return

        RenderUtils.drawRoundedRect(
            x,
            y,
            x + filledWidth,
            y + barHeight,
            radius,
            withAlpha(progressRgb, alphaInt)
        )
    }

    // TASK(Island-multirow): drawProgressLabel, isBrightRgb and rowProgress are new; delete all three
    // to revert. Nothing else calls them except drawNotificationRow.
    /**
     * Writes a row's caption centred across its progress bar, if it has one.
     *
     * The bar itself is 4px on a transient row, which cannot hold a glyph, so a row with a caption
     * uses the taller [PROGRESS_LABEL_HEIGHT] bar ([progressBarHeight]) and the text is drawn one
     * pixel above the bar's lower edge. The colour is picked by brightness rather than by a second
     * constant: the bar is the only thing behind the text, and the accent-coloured fill is dark
     * enough for flat white, while a bright one is not, so white/dark is chosen per row.
     */
    private fun drawProgressLabel(
        notification: IslandNotification,
        x: Float,
        y: Float,
        width: Float,
        barHeight: Float,
        alphaInt: Int
    ) {
        if (notification.progressLabel.isEmpty()) return
        val layout = notification.cachedLayout ?: return

        val textX = x + (width - layout.labelWidth) / 2f
        val textY = y + (barHeight - Fonts.font35.FONT_HEIGHT) / 2f + 1f
        val textColor = if (isBrightRgb(progressRgb)) textRgb else tileGlyphRgb
        Fonts.font35.drawString(notification.progressLabel, textX, textY, withAlpha(textColor, alphaInt))
    }

    /** Whether a packed RGB triple reads bright enough to need dark ink written on top of it. */
    private fun isBrightRgb(rgb: Int): Boolean {
        val red = (rgb shr CHANNEL_RED) and 0xFF
        val green = (rgb shr CHANNEL_GREEN) and 0xFF
        val blue = rgb and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000 >= 150
    }

    /**
     * How full [notification]'s bar is at [now].
     *
     * A transient row shows its own remaining lifetime, which is what makes each row's bar a private
     * countdown. The persistent Scaffold row shows a full bar: it has no lifetime, and the number
     * that matters to the user is the live block count written across it, not a fraction of a bar.
     */
    private fun rowProgress(notification: IslandNotification, now: Long): Float {
        if (notification.persistent) return 1f
        val duration = notification.durationMs
        if (duration <= 0L) return 0f
        return ((notification.expiresAtMs - now).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * The notification's leading tile: a rounded "button" card in the AccentColor, lifted a step
     * brighter than the pick it is built from so it reads as raised, with the glyph described by
     * [icon] drawn [tileGlyphRgb] (flat white) inside it. Only two glyphs exist: the iOS style
     * toggle, and the wire-frame cube Scaffold uses.
     *
     * None of this follows [textRgb]: the button is the AccentColor artwork and stays exactly as it
     * was, which is also why it is the one part of the island that did not have to be darkened when
     * the capsule turned white - a coloured card is more visible on white, not less.
     *
     * Everything the button draws is derived from the accent colour here and nowhere else:
     * - the card keeps a brightness floor (see [liftToFloor]) so a very dark pick still separates
     *   from the capsule and the flat white glyph inside stays legible;
     * - the switch's track is the accent colour *darkened*, so the toggle reads against the card,
     *   and its knob is the accent colour pushed towards white, so a light knob sits on a darker
     *   track in either state. [on] is the state the notification reports, and only moves the knob
     *   and picks the darker of the two track shades.
     */
    private fun drawNotifyIconTile(x: Float, y: Float, size: Float, alpha: Float, on: Boolean, icon: NotifyIcon) {
        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0.02f || size <= 4f) return

        val alphaInt = (a * 255).toInt().coerceIn(0, 255)
        val radius = size * 0.25f
        val accent = liftToFloor(accentRgb)

        RenderUtils.drawRoundedRect(
            x,
            y,
            x + size,
            y + size,
            radius,
            withAlpha(brightenRgb(accent, TILE_LIFT), alphaInt)
        )

        when (icon) {
            NotifyIcon.SWITCH -> {
                val switchWidth = size * 0.68f
                val switchHeight = size * 0.40f
                val trackAlpha = withAlpha(accent, alphaInt)
                drawToggleSwitch(
                    x + (size - switchWidth) / 2f,
                    y + (size - switchHeight) / 2f,
                    switchWidth,
                    switchHeight,
                    on,
                    a,
                    scaleArgb(trackAlpha, if (on) SWITCH_TRACK_ON else SWITCH_TRACK_OFF),
                    blendToWhite(trackAlpha, if (on) SWITCH_KNOB_ON else SWITCH_KNOB_OFF)
                )
            }

            NotifyIcon.CUBE -> drawNotifyCubeIcon(x, y, size, alphaInt)
        }
    }

    /**
     * A wire-frame block drawn straight from primitives: an outer rounded square, an inner square
     * and three short straight edges reaching from the inner square out to the outer one. At 24px
     * that reads as a cube without any diagonal line work.
     *
     * It is drawn in [tileGlyphRgb] because it sits inside the accent coloured card, not on the
     * capsule: the card's contents stay white like they always were.
     */
    private fun drawNotifyCubeIcon(tileX: Float, tileY: Float, tileSize: Float, alphaInt: Int) {
        val glyph = tileSize * 0.40f
        val cx = tileX + tileSize / 2f
        val cy = tileY + tileSize / 2f
        val half = glyph / 2f
        val thickness = 1f
        val lineColor = withAlpha(tileGlyphRgb, alphaInt)
        // The inner silhouettes are drawn a touch dimmer, so the three straight edges that spell
        // out the depth stay readable at this size.
        val edgeColor = withAlpha(tileGlyphRgb, (alphaInt * 0.7f).toInt().coerceIn(0, 255))

        val left = cx - half
        val right = cx + half
        val top = cy - half
        val bottom = cy + half

        val innerLeft = left + glyph * 0.28f
        val innerRight = right - glyph * 0.28f
        val innerTop = top + glyph * 0.28f
        val innerBottom = bottom - glyph * 0.28f

        // Outer silhouette, inner square, then the three straight edges towards the outer square.
        drawRoundedOutline(left, top, right, bottom, thickness, thickness, lineColor)
        drawRoundedOutline(innerLeft, innerTop, innerRight, innerBottom, thickness, thickness, edgeColor)
        RenderUtils.drawRect(cx - thickness / 2f, top, cx + thickness / 2f, innerTop, edgeColor)
        RenderUtils.drawRect(left, cy - thickness / 2f, innerLeft, cy + thickness / 2f, edgeColor)
        RenderUtils.drawRect(right - thickness, cy - thickness / 2f, right, cy + thickness / 2f, edgeColor)
    }

    /**
     * Draws one island glyph from primitives, always in [textRgb] (the dark ink) so the glyph reads
     * cleanly on the white capsule. Coordinates are laid out from the glyph centre and
     * `r = size / 2`.
     */
    private fun drawIslandIcon(icon: IslandIcon, x: Float, y: Float, size: Float, alpha: Float, angle: Float = 0f) {
        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0.02f) return
        val alphaInt = (a * 255).toInt().coerceIn(0, 255)
        val color = withAlpha(textRgb, alphaInt)
        val cx = x + size / 2f
        val cy = y + size / 2f
        val r = size / 2f

        GlStateManager.pushMatrix()
        if (angle != 0f) {
            GlStateManager.translate(cx, cy, 0f)
            GlStateManager.rotate(angle, 0f, 0f, 1f)
            GlStateManager.translate(-cx, -cy, 0f)
        }

        when (icon) {
            IslandIcon.LOGO -> {
                // A sun, for Daylight. The eight rays are still eight rects (one per direction, so
                // the shape is unchanged), but their unit-circle offsets come from the tables.
                RenderUtils.drawRoundedRect(cx - r * 0.4f, cy - r * 0.4f, cx + r * 0.4f, cy + r * 0.4f, r * 0.4f, color)
                for (i in 0 until RAY_COUNT) {
                    val tx = cx + (RAY_COSINE[i] * r * 0.8).toFloat()
                    val ty = cy + (RAY_SINE[i] * r * 0.8).toFloat()
                    RenderUtils.drawRect(tx - r * 0.14f, ty - r * 0.14f, tx + r * 0.14f, ty + r * 0.14f, color)
                }
            }

            IslandIcon.USER -> {
                RenderUtils.drawRoundedRect(cx - r * 0.32f, cy - r * 0.9f, cx + r * 0.32f, cy - r * 0.2f, r * 0.32f, color)
                RenderUtils.drawRoundedRect(cx - r * 0.72f, cy + r * 0.12f, cx + r * 0.72f, cy + r * 0.86f, r * 0.37f, color)
            }

            IslandIcon.FPS -> {
                drawRoundedOutline(cx - r * 0.9f, cy - r * 0.75f, cx + r * 0.9f, cy + r * 0.25f, r * 0.16f, 1f, color)
                RenderUtils.drawRect(cx - r * 0.22f, cy + r * 0.25f, cx + r * 0.22f, cy + r * 0.5f, color)
                RenderUtils.drawRect(cx - r * 0.46f, cy + r * 0.5f, cx + r * 0.46f, cy + r * 0.72f, color)
            }

            IslandIcon.BLOCKS -> {
                // A solid block: the body is slightly translucent and the top face is at the full
                // ink alpha, which reads as a lit upper edge without leaving the dark palette.
                val bodyColor = withAlpha(textRgb, (alphaInt * 0.82f).toInt())
                RenderUtils.drawRect(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy + r * 0.72f, bodyColor)
                RenderUtils.drawRect(cx - r * 0.72f, cy - r * 0.72f, cx + r * 0.72f, cy - r * 0.44f, color)
            }

            IslandIcon.GEAR -> {
                for (i in 0 until RAY_COUNT) {
                    val tx = cx + (GEAR_COSINE[i] * r * 0.72).toFloat()
                    val ty = cy + (GEAR_SINE[i] * r * 0.72).toFloat()
                    RenderUtils.drawRect(tx - r * 0.15f, ty - r * 0.15f, tx + r * 0.15f, ty + r * 0.15f, color)
                }
                drawRoundedOutline(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f, r * 0.6f, 1.2f, color)
                RenderUtils.drawRoundedRect(cx - r * 0.2f, cy - r * 0.2f, cx + r * 0.2f, cy + r * 0.2f, r * 0.2f, color)
            }
        }

        GlStateManager.popMatrix()
        GlStateManager.resetColor()
    }

    // TASK(Island-multirow): onDisable used to clear notifyQueue + currentNotification +
    // notifyEndTime; it now clears the row lists and the Scaffold-row bookkeeping instead.
    override fun onDisable() {
        isFirstLoad = true
        moduleStates.clear()
        activeNotifications.clear()
        visibleNotifications.clear()
        rowOrder.clear()
        scaffoldRowBlocks = Int.MIN_VALUE
        scaffoldRowPresent = false
        popSpring.value = 1f
        popSpring.velocity = 0f

        // Nothing is drawn while the module is off, so the caches are dropped rather than kept warm;
        // onEnable rebuilds them on the first frame back.
        invalidateDisplayCaches()
    }

    private inner class Spring(var value: Float = 0f) {
        var velocity = 0f
        fun update(target: Float, tension: Float, friction: Float, delta: Float) {
            val displacement = target - value
            val springForce = displacement * tension
            val dampingForce = velocity * friction
            velocity += (springForce - dampingForce) * delta
            value += velocity * delta
        }
    }
}
