package net.aspw.client.compat.leaderlite;

import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;

/**
 * Stand-in for LeaderLite's {@code leader.util.PlayerUtil}, restricted to what its Scaffold calls.
 *
 * <p>The old Kotlin Scaffold already worked around the same two gaps by inlining the bodies (see its
 * {@code hasBlocksAbove} / {@code hasSolidBelow} helpers); this layer exists so the ported Java module
 * can keep LeaderLite's call sites. {@link #isAirAbove()} keeps LeaderLite's exact body, including
 * the misleading name: it returns true when something collides in the box one block above the
 * player, which is how its {@code isTowering} rejects a tower under a ceiling.</p>
 */
public final class PlayerUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private PlayerUtil() {
    }

    /**
     * LeaderLite verbatim (despite the name: true when the box one block up collides).
     *
     * @return whether something solid sits directly above the player
     */
    public static boolean isAirAbove() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        AxisAlignedBB axisAlignedBB = mc.thePlayer.getEntityBoundingBox().offset(0.0, 1.0, 0.0);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, axisAlignedBB).isEmpty();
    }

    /**
     * LeaderLite verbatim.
     *
     * @return whether something solid sits directly below the player
     */
    public static boolean isAirBelow() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        AxisAlignedBB axisAlignedBB = mc.thePlayer.getEntityBoundingBox().offset(0.0, -1.0, 0.0);
        return !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, axisAlignedBB).isEmpty();
    }

    /**
     * LeaderLite verbatim.
     *
     * @param blockPos the position
     * @param x        the reference x
     * @param y        the reference y
     * @param z        the reference z
     * @param reach    the reach
     * @return whether the block centre is inside the reach
     */
    public static boolean isBlockWithinReach(net.minecraft.util.BlockPos blockPos, double x, double y, double z, double reach) {
        return blockPos.distanceSqToCenter(x, y, z) < Math.pow(reach, 2.0);
    }

    /**
     * LeaderLite verbatim.
     *
     * @param blockPos the position
     * @param reach    the reach
     * @return whether the block centre is inside the reach from the player's eyes
     */
    public static boolean canReach(net.minecraft.util.BlockPos blockPos, double reach) {
        return isBlockWithinReach(blockPos, mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ, reach);
    }
}
