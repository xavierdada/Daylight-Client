package net.aspw.client.compat.leaderlite;

/**
 * Stand-in for LeaderLite's {@code leader.events.KeyEvent}.
 *
 * <p>This project's {@code KeyEvent} is an unrelated class in {@code net.aspw.client.event} (there is
 * no such event class here at all - keys arrive as {@code net.aspw.client.event.KeyEvent}, and the
 * ported module needs LeaderLite's name not to clash), so the port keeps LeaderLite's class here and
 * the ported module converts at the boundary. See the ported Scaffold's {@code onKey}.</p>
 */
public class KeyEvent {

    private final int key;

    public KeyEvent(int key) {
        this.key = key;
    }

    /**
     * @return the LWJGL key code
     */
    public int getKey() {
        return this.key;
    }
}
