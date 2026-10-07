package net.aspw.client.compat.leaderlite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Stand-in for LeaderLite's {@code leader.util.MoveUtil}, restricted to what its Scaffold calls.
 *
 * <p>Only the input readers and {@link #adjustYaw} are ported verbatim: this project's
 * {@code MovementUtils} has the same job but a different shape, so the ported module would have had
 * to translate every call site. {@link #isForwardPressed}, {@link #getForwardValue},
 * {@link #getLeftValue}, {@link #adjustYaw} and {@link #fixStrafe} mirror LeaderLite's bodies one to
 * one. {@link #adjustYaw} drops LeaderLite's {@code TargetStrafe} override, which this project
 * expresses through its own {@code TargetStrafe} module instead.</p>
 */
public final class MoveUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private MoveUtil() {
    }

    /**
     * LeaderLite verbatim: true when the forward/back or left/right keys disagree.
     *
     * @return whether a movement key is held in a direction
     */
    public static boolean isForwardPressed() {
        if (mc.gameSettings.keyBindForward.isKeyDown() != mc.gameSettings.keyBindBack.isKeyDown()) {
            return true;
        }
        return mc.gameSettings.keyBindLeft.isKeyDown() != mc.gameSettings.keyBindRight.isKeyDown();
    }

    /**
     * @return +1 when forward is held, -1 when back is held, 0 otherwise
     */
    public static int getForwardValue() {
        int forwardValue = 0;
        if (mc.gameSettings.keyBindForward.isKeyDown()) {
            forwardValue++;
        }
        if (mc.gameSettings.keyBindBack.isKeyDown()) {
            forwardValue--;
        }
        return forwardValue;
    }

    /**
     * @return +1 when left is held, -1 when right is held, 0 otherwise
     */
    public static int getLeftValue() {
        int leftValue = 0;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) {
            leftValue++;
        }
        if (mc.gameSettings.keyBindRight.isKeyDown()) {
            leftValue--;
        }
        return leftValue;
    }

    /**
     * LeaderLite verbatim, minus its TargetStrafe override.
     *
     * @param yaw     the base yaw
     * @param forward the forward input
     * @param strafe  the strafe input
     * @return the yaw the movement keys point at
     */
    public static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        if (strafe != 0.0F) {
            float multiplier = forward == 0.0F ? 1.0F : 0.5F * Math.signum(forward);
            yaw += -90.0F * multiplier * Math.signum(strafe);
        }
        return net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw);
    }

    /**
     * LeaderLite verbatim: rotates the raw WASD input so the movement follows a silent rotation.
     *
     * @param targetYaw the yaw the movement should follow
     */
    public static void fixStrafe(float targetYaw) {
        float angle = net.minecraft.util.MathHelper.wrapAngleTo180_float(
                adjustYaw(mc.thePlayer.rotationYaw, getForwardValue(), getLeftValue()) - targetYaw + 22.5F);
        switch ((int) (angle + 180.0F) / 45 % 8) {
            case 0:
                mc.thePlayer.movementInput.moveForward = -1.0F;
                mc.thePlayer.movementInput.moveStrafe = 0.0F;
                break;
            case 1:
                mc.thePlayer.movementInput.moveForward = -1.0F;
                mc.thePlayer.movementInput.moveStrafe = 1.0F;
                break;
            case 2:
                mc.thePlayer.movementInput.moveForward = 0.0F;
                mc.thePlayer.movementInput.moveStrafe = 1.0F;
                break;
            case 3:
                mc.thePlayer.movementInput.moveForward = 1.0F;
                mc.thePlayer.movementInput.moveStrafe = 1.0F;
                break;
            case 4:
                mc.thePlayer.movementInput.moveForward = 1.0F;
                mc.thePlayer.movementInput.moveStrafe = 0.0F;
                break;
            case 5:
                mc.thePlayer.movementInput.moveForward = 1.0F;
                mc.thePlayer.movementInput.moveStrafe = -1.0F;
                break;
            case 6:
                mc.thePlayer.movementInput.moveForward = 0.0F;
                mc.thePlayer.movementInput.moveStrafe = -1.0F;
                break;
            case 7:
                mc.thePlayer.movementInput.moveForward = -1.0F;
                mc.thePlayer.movementInput.moveStrafe = -1.0F;
                break;
            default:
                break;
        }
        if (mc.thePlayer.movementInput.sneak) {
            mc.thePlayer.movementInput.moveForward *= 0.3F;
            mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        }
    }

    /**
     * Kept for parity with LeaderLite's API surface; Scaffold does not call it.
     *
     * @return whether the jump key is down
     */
    public static boolean isJumpPressed() {
        return GameSettings.isKeyDown(mc.gameSettings.keyBindJump);
    }
}
