package net.aspw.client.compat.leaderlite;

/**
 * Stand-in for LeaderLite's {@code leader.event.types.Priority}.
 *
 * <p>LeaderLite's own event bus sorts listeners by this; this project's {@code EventManager} sorts on
 * the integer in its own {@code @EventTarget(priority = ...)} annotation instead, so the constants
 * only exist so the ported module keeps its {@code @EventTarget(Priority.HIGH)} spelling. The values
 * mirror LeaderLite's ordering (a higher constant runs earlier there); this client's bus runs the
 * ported handler in registration order, and Scaffold is the only listener that writes a rotation, so
 * no ordering guarantee is lost.</p>
 */
public final class Priority {

    /** LeaderLite's highest priority. */
    public static final int HIGHEST = 3;

    /** LeaderLite's high priority, the one Scaffold asks for. */
    public static final int HIGH = 2;

    /** LeaderLite's default priority. */
    public static final int MEDIUM = 1;

    /** LeaderLite's lowest priority. */
    public static final int LOW = 0;

    private Priority() {
    }
}
