package net.aspw.client.compat.leaderlite;

import net.aspw.client.event.EventState;
import net.aspw.client.event.MotionEvent;
import net.aspw.client.utils.Rotation;
import net.aspw.client.utils.RotationUtils;

/**
 * Stand-in for LeaderLite's {@code leader.events.UpdateEvent}.
 *
 * <p>LeaderLite fires one {@code UpdateEvent} per phase around {@code EntityPlayerSP.onUpdate} and
 * the module answers with {@link #setRotation} / {@link #setPervRotation}, which several of its
 * modules compete over through a priority number. This project fires {@link MotionEvent} twice per
 * tick instead ({@code EventState.PRE} before the C03 packet is built, {@code POST} after it), and
 * silent rotations go through {@link RotationUtils#setTargetRotation(Rotation)}.</p>
 *
 * <p>So this class is an adapter, not a copy:</p>
 * <ul>
 *   <li>{@link #getType()} maps {@code MotionEvent.eventState} onto LeaderLite's
 *       {@link EventType}, so the ported module keeps its {@code PRE}-gated flow.</li>
 *   <li>{@link #setRotation} feeds {@link RotationUtils#setTargetRotation(Rotation)} on
 *       {@code PRE}. The ported module passes priority {@code 3}; this client has no competing
 *       rotation writer inside that handler, so the priority argument is accepted and ignored.</li>
 *   <li>{@link #setPervRotation} records the yaw the movement should follow and does nothing else.
 *       The ported module is the only caller that pairs it with {@code moveFix}, and it reads the
 *       result through {@link #getPervYaw()} and applies it itself (LeaderLite reached the same value
 *       through {@code RotationState.getSmoothedYaw()}).</li>
 * </ul>
 */
public class UpdateEvent {

    private final MotionEvent source;
    private final EventType type;

    /** Last prev-rotation written through {@link #setPervRotation}. */
    private float pervYaw = Float.NaN;

    /** Priority of the last {@link #setPervRotation}, for parity with LeaderLite's guard. */
    private int pervPriority = -1;

    /** LeaderLite's {@code prevYaw}: the rotation the previous tick reported. */
    private final float prevYaw;

    /** LeaderLite's {@code prevPitch}: the rotation the previous tick reported. */
    private final float prevPitch;

    public UpdateEvent(MotionEvent source) {
        this.source = source;
        this.type = source.getEventState() == EventState.POST ? EventType.POST : EventType.PRE;
        this.prevYaw = source.getYaw();
        this.prevPitch = source.getPitch();
    }

    /**
     * @return the wrapped event, so callers can read position and ground state
     */
    public MotionEvent getSource() {
        return this.source;
    }

    /**
     * @return {@link EventType#PRE} or {@link EventType#POST}
     */
    public EventType getType() {
        return this.type;
    }

    /**
     * @return the yaw that was current when this event fired
     */
    public float getYaw() {
        return this.prevYaw;
    }

    /**
     * @return the pitch that was current when this event fired
     */
    public float getPitch() {
        return this.prevPitch;
    }

    /**
     * The rotation the previous tick reported. LeaderLite tracked this separately from
     * {@link #getYaw()}; here both come from the packet's own angle, which is what
     * {@code MotionEvent.yaw} carries.
     *
     * @return the previous tick's yaw
     */
    public float getPreYaw() {
        return this.prevYaw;
    }

    /**
     * @return the previous tick's pitch
     */
    public float getPrePitch() {
        return this.prevPitch;
    }

    /**
     * The rotation that will reach the server for this tick, as last written by
     * {@link #setRotation}.
     *
     * @return the pending packet yaw
     */
    public float getNewYaw() {
        Rotation target = RotationUtils.targetRotation;
        return target != null ? target.getYaw() : this.source.getYaw();
    }

    /**
     * @return the pending packet pitch
     */
    public float getNewPitch() {
        Rotation target = RotationUtils.targetRotation;
        return target != null ? target.getPitch() : this.source.getPitch();
    }

    /**
     * @return true once a rotation was written for this tick
     */
    public boolean isRotated() {
        return RotationUtils.targetRotation != null;
    }

    /**
     * @return the priority of the last {@link #setPervRotation}, or -1 when none ran
     */
    public int isRotating() {
        return this.pervPriority;
    }

    /**
     * LeaderLite's silent rotation write: the module's angle goes to the server while the client
     * keeps looking where the mouse points.
     *
     * @param yaw      the angle to send
     * @param pitch    the angle to send
     * @param priority LeaderLite's arbitration number; unused here (see class notes)
     */
    public void setRotation(float yaw, float pitch, int priority) {
        if (this.type != EventType.PRE) {
            return;
        }
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
            return;
        }
        RotationUtils.setTargetRotation(new Rotation(yaw, pitch));
    }

    /**
     * LeaderLite's movement rotation write. This client has no rotation manager to hold a separate
     * "previous" angle, so the value is recorded here and handed back through
     * {@link #getPervYaw()} for the ported module's own move fix.
     *
     * @param yaw      the yaw the movement should follow
     * @param priority LeaderLite's arbitration number
     */
    public void setPervRotation(float yaw, int priority) {
        if (this.type != EventType.PRE) {
            return;
        }
        if (this.pervPriority < priority) {
            this.pervYaw = yaw;
            this.pervPriority = priority;
        }
    }

    /**
     * @return the yaw recorded by {@link #setPervRotation}, or NaN when none ran
     */
    public float getPervYaw() {
        return this.pervYaw;
    }

    /**
     * @return whether {@link #setPervRotation} ran for this event
     */
    public boolean hasPervRotation() {
        return !Float.isNaN(this.pervYaw);
    }
}
