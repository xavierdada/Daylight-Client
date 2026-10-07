package net.aspw.client.utils.render

import net.aspw.client.utils.MinecraftInstance
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.AxisAlignedBB
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.nio.ByteBuffer
import java.nio.FloatBuffer

/**
 * World-space (3D) trajectory renderer used by module path visualisations
 * (currently [net.aspw.client.features.module.impl.combat.StrafeBot]).
 *
 * The recorded trajectory lives in primitive ring buffers (no boxing, no
 * per-point objects) and the polyline is written straight into a single
 * `GL11.glBegin(GL_LINE_STRIP)` block, so a frame costs one draw call and zero
 * allocations.
 *
 * GL handling follows the same save/restore pattern the project already uses in
 * [RenderUtils.renderLine], Tracers and Trajectories:
 *  - matrix push/pop are paired inside try/finally,
 *  - GL_TEXTURE_2D / GL_DEPTH_TEST / GL_CULL_FACE / GL_BLEND / GL_LINE_SMOOTH
 *    are toggled back on the way out,
 *  - the line width and the depth mask are captured with glGetFloat /
 *    glGetBoolean and restored, and the colour is reset through
 *    `GlStateManager.resetColor()`.
 */
object PathRender : MinecraftInstance() {

    /** Hard cap of stored trajectory points; rendering never exceeds this. */
    const val CAPACITY = 100

    private val ringX = DoubleArray(CAPACITY)
    private val ringY = DoubleArray(CAPACITY)
    private val ringZ = DoubleArray(CAPACITY)

    /** Index the next point is written to. */
    private var ringHead = 0

    /** Number of valid points, clamped to [CAPACITY]. */
    private var ringCount = 0

    /** Scratch buffer reused by every frame -> no allocation inside render. */
    private val pointBuf = DoubleArray((CAPACITY + 1) * 3)

    /** Scratch for reading back the previous line width. */
    private val lineWidthBuf: FloatBuffer = org.lwjgl.BufferUtils.createFloatBuffer(1)

    /** Scratch for reading back the previous depth mask. */
    private val depthMaskBuf: ByteBuffer = org.lwjgl.BufferUtils.createByteBuffer(1)

    val pointCount: Int
        get() = ringCount

    /** X of the newest recorded point, 0.0 when the buffer is empty. */
    fun getX(age: Int): Double = if (ringCount <= 0) 0.0 else ringX[indexOfAge(age)]

    /** Y of the newest recorded point, 0.0 when the buffer is empty. */
    fun getY(age: Int): Double = if (ringCount <= 0) 0.0 else ringY[indexOfAge(age)]

    /** Z of the newest recorded point, 0.0 when the buffer is empty. */
    fun getZ(age: Int): Double = if (ringCount <= 0) 0.0 else ringZ[indexOfAge(age)]

    fun clear() {
        ringHead = 0
        ringCount = 0
    }

    /**
     * Appends one point to the trajectory, dropping the oldest point once
     * [CAPACITY] is reached.
     */
    fun addPoint(x: Double, y: Double, z: Double) {
        ringX[ringHead] = x
        ringY[ringHead] = y
        ringZ[ringHead] = z
        ringHead = (ringHead + 1) % CAPACITY
        if (ringCount < CAPACITY) ringCount++
    }

    /** Ring index of the newest stored point. */
    private fun newestIndex(): Int = (ringHead - 1 + CAPACITY) % CAPACITY

    /** Ring index of the point stored `age` ticks ago (0 = newest). */
    private fun indexOfAge(age: Int): Int = ((newestIndex() - age) % CAPACITY + CAPACITY) % CAPACITY

    private fun nextIndex(index: Int): Int = (index + 1) % CAPACITY

    /**
     * Builds the polyline as of "now" into [buf].
     *
     * @param phase how far the newest point has travelled towards the current
     *              player position, 0..1. This is what makes the trail move
     *              smoothly with the interpolated player instead of snapping
     *              once per tick.
     * @param buf   destination buffer, at least `(CAPACITY + 1) * 3` doubles
     * @return the number of vertices written, 0 when nothing is recorded
     */
    private fun buildPolyline(
        phase: Double,
        curX: Double, curY: Double, curZ: Double,
        buf: DoubleArray
    ): Int {
        if (ringCount <= 0) return 0

        val oldest = if (ringCount < CAPACITY) 0 else ringHead
        val back = 1.0 - phase

        // Oldest first, so the strip is drawn chronologically.
        for (k in 0 until ringCount) {
            val out = k * 3
            val x: Double
            val y: Double
            val z: Double
            if (k == 0) {
                // Oldest vertex slides away from the oldest stored point.
                val next = nextIndex(oldest)
                x = ringX[oldest] + (ringX[next] - ringX[oldest]) * phase
                y = ringY[oldest] + (ringY[next] - ringY[oldest]) * phase
                z = ringZ[oldest] + (ringZ[next] - ringZ[oldest]) * phase
            } else {
                // Every other vertex trails `1 - phase` behind its stored point.
                val idx = indexOfAge(ringCount - 1 - k)
                val next = nextIndex(idx)
                x = ringX[idx] + (ringX[next] - ringX[idx]) * back
                y = ringY[idx] + (ringY[next] - ringY[idx]) * back
                z = ringZ[idx] + (ringZ[next] - ringZ[idx]) * back
            }
            buf[out] = x
            buf[out + 1] = y
            buf[out + 2] = z
        }

        // Head vertex is the real (interpolated) player position, so the line
        // never lies about where the bot is right now.
        val head = ringCount * 3
        buf[head] = curX
        buf[head + 1] = curY
        buf[head + 2] = curZ
        return ringCount + 1
    }

    /**
     * Draws the recorded trajectory plus the endpoint markers.
     *
     * All coordinates are absolute world coordinates; [renderPosX] / [renderPosY]
     * / [renderPosZ] are the camera offsets taken from
     * `mc.renderManager.renderPosX/Y/Z` (the same convention as
     * [RenderUtils.renderLine]).
     *
     * @param lineColor   ARGB colour of the polyline
     * @param markerColor ARGB colour of the endpoint markers
     * @param showMarker  draw a small box at the head and, once the buffer is
     *                    full, also at the tail
     */
    fun drawHistory(
        curX: Double, curY: Double, curZ: Double,
        renderPosX: Double, renderPosY: Double, renderPosZ: Double,
        phase: Double,
        lineColor: Int, markerColor: Int,
        lineWidth: Float, showMarker: Boolean
    ) {
        val phaseClamped = phase.coerceIn(0.0, 1.0)
        val vertexCount = buildPolyline(phaseClamped, curX, curY, curZ, pointBuf)
        if (vertexCount <= 0) return

        GL11.glPushMatrix()
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glShadeModel(GL11.GL_FLAT)
            lineWidthBuf.clear()
            GL11.glGetFloat(GL11.GL_LINE_WIDTH, lineWidthBuf)
            // TASK(LineWidth-positive):glLineWidth(<= 0) 报 GL_INVALID_VALUE(1281),设置项读自配置
            // 文件时不夹,所以调用前夹一次。finally 里回写的是查询到的原值,一定合法。
            GL11.glLineWidth(lineWidth.coerceAtLeast(0.5f))
            depthMaskBuf.clear()
            GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK, depthMaskBuf)
            GL11.glDepthMask(false)

            RenderUtils.color(lineColor)
            GL11.glBegin(GL11.GL_LINE_STRIP)
            for (i in 0 until vertexCount) {
                val b = i * 3
                GL11.glVertex3d(pointBuf[b] - renderPosX, pointBuf[b + 1] - renderPosY, pointBuf[b + 2] - renderPosZ)
            }
            GL11.glEnd()

            if (showMarker) {
                val head = (vertexCount - 1) * 3
                drawMarker(
                    pointBuf[head] - renderPosX,
                    pointBuf[head + 1] - renderPosY,
                    pointBuf[head + 2] - renderPosZ,
                    markerColor
                )
                // Tail marker only once the buffer is full, i.e. once the trail
                // is actually being trimmed.
                if (ringCount >= CAPACITY) {
                    drawMarker(
                        pointBuf[0] - renderPosX,
                        pointBuf[1] - renderPosY,
                        pointBuf[2] - renderPosZ,
                        markerColor
                    )
                }
            }
        } finally {
            // Put back everything that was changed, then let the project's own
            // cap tracker resynchronise the tracked GL states.
            GL11.glDepthMask(depthMaskBuf.get(0) != 0.toByte())
            GL11.glLineWidth(lineWidthBuf.get(0))
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glEnable(GL11.GL_CULL_FACE)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_BLEND)
            GL11.glPopMatrix()
            GlStateManager.resetColor()
            RenderUtils.resetCaps()
        }
    }

    /**
     * Small world-space box drawn through the project's existing helper, so the
     * marker shares the exact state handling of the other ESP modules.
     * `drawAxisAlignedBB` already translates by nothing, so the caller's matrix
     * stack carries the offset.
     */
    private fun drawMarker(x: Double, y: Double, z: Double, color: Int) {
        val size = 0.12
        GL11.glPushMatrix()
        try {
            GL11.glTranslated(x, y, z)
            RenderUtils.drawAxisAlignedBB(
                AxisAlignedBB(-size, -size, -size, size, size, size),
                Color(color, true)
            )
        } finally {
            GL11.glPopMatrix()
        }
    }
}
