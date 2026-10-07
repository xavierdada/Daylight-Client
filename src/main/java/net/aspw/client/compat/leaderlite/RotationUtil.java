package net.aspw.client.compat.leaderlite;

import net.aspw.client.utils.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Stand-in for LeaderLite's {@code leader.util.RotationUtil}, restricted to what its Scaffold calls.
 *
 * <p>This project already has a {@link RotationUtils}, but its helpers are expressed as
 * {@code Rotation} objects and a silent-rotation target rather than LeaderLite's
 * {@code float[]{yaw, pitch}} pairs, and it has no "ray trace from an arbitrary angle" helper. Every
 * method below is a thin translation of LeaderLite's own body onto this project's types, so the
 * ported module keeps its exact call sites. Only the pieces Scaffold uses are present.</p>
 */
public final class RotationUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private RotationUtil() {
    }

    /**
     * LeaderLite verbatim: turns an angle into one inside +/-{@code maxAngle}.
     *
     * @param angle    the angle to clamp
     * @param maxAngle the bound, itself clamped to 0..180
     * @return the clamped angle
     */
    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0F, Math.min(180.0F, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    /**
     * LeaderLite verbatim: damps an angle by a randomised factor.
     *
     * @param angle        the angle
     * @param smoothFactor the smoothing factor
     * @return the smoothed angle
     */
    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5F + 0.5F
                * (1.0F - Math.max(0.0F, Math.min(1.0F,
                smoothFactor + net.aspw.client.utils.misc.RandomUtils.nextFloat(-0.1F, 0.1F)))));
    }

    /**
     * LeaderLite verbatim: snaps an angle onto its 0.0096 degree grid.
     *
     * @param angle the angle
     * @return the quantised angle
     */
    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096F);
    }

    /**
     * LeaderLite verbatim: re-expresses {@code angle} as a difference from {@code target}.
     *
     * @param angle  the angle
     * @param target the reference angle
     * @return the equivalent angle closest to {@code target}
     */
    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    /**
     * LeaderLite verbatim: yaw/pitch from the player's eye to a point.
     *
     * @param posX the target x
     * @param posY the target y
     * @param posZ the target z
     * @return {@code {yaw, pitch}}
     */
    public static float[] getRotations(double posX, double posY, double posZ) {
        return getRotations(posX, posY, posZ, mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    /**
     * LeaderLite verbatim: yaw/pitch from an explicit origin to a point.
     *
     * @param rotX   the target x
     * @param rotY   the target y
     * @param rotZ   the target z
     * @param startX the origin x
     * @param startY the origin y
     * @param startZ the origin z
     * @return {@code {yaw, pitch}}
     */
    public static float[] getRotations(double rotX, double rotY, double rotZ,
                                       double startX, double startY, double startZ) {
        double x = rotX - startX;
        double y = rotY - startY;
        double z = rotZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    /**
     * LeaderLite's smoothing variant, used by the ported module only through
     * {@link #getRotationsTo(double, double, double, float, float)}.
     *
     * @param targetX      the target x
     * @param targetY      the target y
     * @param targetZ      the target z
     * @param currentYaw   the current yaw
     * @param currentPitch the current pitch
     * @param maxAngle     the per-call turn cap
     * @param smoothFactor the smoothing factor
     * @return {@code {yaw, pitch}} relative to the current angles
     */
    public static float[] getRotations(double targetX, double targetY, double targetZ,
                                       float currentYaw, float currentPitch,
                                       float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0F - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0F ? 0.0F : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta)};
    }

    /**
     * LeaderLite verbatim.
     *
     * @param targetX      the target x
     * @param targetY      the target y
     * @param targetZ      the target z
     * @param currentYaw   the current yaw
     * @param currentPitch the current pitch
     * @return {@code {yaw, pitch}} relative to the current angles
     */
    public static float[] getRotationsTo(double targetX, double targetY, double targetZ,
                                         float currentYaw, float currentPitch) {
        return getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0F, 0.0F);
    }

    /**
     * LeaderLite verbatim: a block ray trace from an arbitrary view angle.
     *
     * <p>LeaderLite reached the look vector through its {@code IAccessorEntity} mixin accessor.
     * {@link RotationUtils#getVectorForRotation(net.aspw.client.utils.Rotation)} already exposes the
     * same vanilla math, so this is a call into this project's helper rather than a copied body.</p>
     *
     * @param yaw         the view yaw
     * @param pitch       the view pitch
     * @param distance    the reach
     * @param partialTicks the eye-position interpolation factor
     * @return the block hit, or null
     */
    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        Vec3 eyePos = mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = RotationUtils.getVectorForRotation(new net.aspw.client.utils.Rotation(yaw, pitch));
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    /**
     * LeaderLite's {@code isVisible}, kept for the helper surface Scaffold's block search relies on.
     *
     * @param targetVec the point to test
     * @return whether the point can be seen from the player's eye
     */
    public static boolean isVisible(Vec3 targetVec) {
        if (mc.thePlayer == null || mc.theWorld == null || targetVec == null) {
            return false;
        }
        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        if (eyePos.squareDistanceTo(targetVec) > 4096.0) {
            return false;
        }
        Vec3 lookVec = mc.thePlayer.getLookVec();
        Vec3 toTarget = targetVec.subtract(eyePos).normalize();
        if (lookVec.dotProduct(toTarget) < 0) {
            return false;
        }
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eyePos, targetVec, false, true, false);
        return result == null;
    }

    /**
     * LeaderLite's {@code getClosestPointOnBox}, used by its {@code getNearestPointBB}.
     *
     * @param point the reference point
     * @param bb    the box
     * @return the point on the box closest to {@code point}
     */
    public static Vec3 getClosestPointOnBox(Vec3 point, AxisAlignedBB bb) {
        double x = MathHelper.clamp_double(point.xCoord, bb.minX, bb.maxX);
        double y = MathHelper.clamp_double(point.yCoord, bb.minY, bb.maxY);
        double z = MathHelper.clamp_double(point.zCoord, bb.minZ, bb.maxZ);
        return new Vec3(x, y, z);
    }

    /**
     * Kept for parity: LeaderLite's nearest visible point on a box.
     *
     * @param box the box
     * @return the point, or null when there is no world or player
     */
    public static Vec3 getNearestPointBB(AxisAlignedBB box) {
        if (mc.thePlayer == null) {
            return null;
        }
        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        double x = MathHelper.clamp_double(eyePos.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyePos.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyePos.zCoord, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    /**
     * Convenience overload of {@link #rayTrace(float, float, double, float)} for entities.
     *
     * @param entity the entity to aim at
     * @return the block hit along the way, or null
     */
    public static MovingObjectPosition rayTrace(Entity entity) {
        if (entity == null) {
            return null;
        }
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = getClosestPointOnBox(eyePos,
                entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }
}
