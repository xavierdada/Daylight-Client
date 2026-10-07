package net.aspw.client.compat.leaderlite;

/**
 * Stand-in for LeaderLite's {@code leader.events.StrafeEvent}, kept only for the clutch branch.
 *
 * <p>This project already has a {@code net.aspw.client.event.StrafeEvent} with the same three fields
 * plus the rotation yaw, and that is the one the ported module actually subscribes to. This class
 * exists so the port keeps LeaderLite's own handler signature (and so the friction field LeaderLite
 * exposed is not lost); the body is a field-for-field copy of LeaderLite's class.</p>
 */
public class StrafeEvent {

    private float strafe;
    private float forward;
    private float friction;

    public StrafeEvent(float strafe, float forward, float friction) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public float getForward() {
        return this.forward;
    }

    public float getFriction() {
        return this.friction;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }
}
