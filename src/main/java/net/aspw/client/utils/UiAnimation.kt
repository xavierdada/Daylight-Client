package net.aspw.client.utils

/**
 * A reversible, clock-based cubic tween. Sampling more often does not change its duration.
 * The explicit clock parameter also lets tests run without Minecraft or an OpenGL context.
 */
class UiAnimation(initialValue: Float = 0f) {
    private var from = initialValue
    private var target = initialValue
    private var startedAt = 0L
    private var durationNanos = 0L
    var value = initialValue
        private set

    @JvmOverloads
    fun animate(target: Float, durationMillis: Long = 180L, now: Long = System.nanoTime()): Float {
        sample(now)
        if (durationMillis <= 0L) {
            reset(target)
        } else if (target != this.target) {
            from = value
            this.target = target
            startedAt = now
            durationNanos = durationMillis * 1_000_000L
        }
        return value
    }

    fun reset(value: Float) {
        this.value = value
        from = value
        target = value
        durationNanos = 0L
    }

    private fun sample(now: Long) {
        if (durationNanos <= 0L) return
        val progress = ((now - startedAt).toDouble() / durationNanos).coerceIn(0.0, 1.0).toFloat()
        val inverse = 1f - progress
        value = from + (target - from) * (1f - inverse * inverse * inverse)
        if (progress >= 1f) {
            value = target
            durationNanos = 0L
        }
    }
}
