package net.aspw.client.compat.leaderlite;

/**
 * Stand-in for LeaderLite's {@code leader.event.types.EventType}, with the same two constants.
 *
 * <p>LeaderLite fires one {@code UpdateEvent} class twice per tick and tells the two apart by this
 * enum. This project fires {@code MotionEvent} twice per tick and tells them apart with its own
 * {@code EventState}. {@link UpdateEvent} converts between the two.</p>
 */
public enum EventType {
    PRE,
    POST
}
