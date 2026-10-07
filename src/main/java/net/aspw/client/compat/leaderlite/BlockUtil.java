package net.aspw.client.compat.leaderlite;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockEndPortal;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTNT;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockTripWire;
import net.minecraft.block.BlockTripWireHook;
import net.minecraft.block.BlockVine;
import net.minecraft.block.BlockWall;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Stand-in for LeaderLite's {@code leader.util.BlockUtil}, restricted to what its Scaffold calls.
 *
 * <p>This project's own {@code BlockUtils} answers "is this position replaceable" from the block
 * material alone, which is not the same test: LeaderLite additionally rejects snow layers thicker
 * than one pixel, and it owns the "is this block a container / is it a real solid cube" pair that
 * its Scaffold uses to pick a block to place and to decide whether a slot holds a usable block.
 * Those bodies are ported here verbatim so the ported module keeps its exact behaviour. The ray
 * trace inside {@link #getHitVec} goes through this layer's {@link RotationUtil}.</p>
 */
public final class BlockUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private BlockUtil() {
    }

    /**
     * LeaderLite verbatim.
     *
     * @param blockPos the position
     * @return whether the block there can be replaced
     */
    public static boolean isReplaceable(BlockPos blockPos) {
        if (blockPos == null || mc.theWorld == null) {
            return false;
        }
        return isReplaceable(mc.theWorld.getBlockState(blockPos).getBlock());
    }

    /**
     * LeaderLite verbatim: material driven, plus the snow-layer special case.
     *
     * @param block the block
     * @return whether the block can be replaced
     */
    public static boolean isReplaceable(Block block) {
        if (!block.getMaterial().isReplaceable()) {
            return false;
        }
        if (!(block instanceof BlockSnow)) {
            return true;
        }
        return !(block.getBlockBoundsMaxY() > 0.125);
    }

    /**
     * LeaderLite verbatim.
     *
     * @param blockPos the position
     * @return whether right clicking the block opens something
     */
    public static boolean isInteractable(BlockPos blockPos) {
        if (blockPos == null || mc.theWorld == null) {
            return false;
        }
        return isInteractable(mc.theWorld.getBlockState(blockPos).getBlock());
    }

    /**
     * LeaderLite verbatim.
     *
     * @param block the block
     * @return whether right clicking the block opens something
     */
    public static boolean isInteractable(Block block) {
        if (block instanceof BlockContainer) {
            return true;
        }
        if (block instanceof BlockWorkbench) {
            return true;
        }
        if (block instanceof BlockAnvil) {
            return true;
        }
        if (block instanceof BlockBed) {
            return true;
        }
        if (block instanceof BlockDoor) {
            if (block.getMaterial() != Material.iron) {
                return true;
            }
        }
        if (block instanceof BlockTrapDoor) {
            return true;
        }
        if (block instanceof BlockFenceGate) {
            return true;
        }
        if (block instanceof BlockFence) {
            return true;
        }
        if (block instanceof BlockButton) {
            return true;
        }
        if (block instanceof BlockLever) {
            return true;
        }
        return block instanceof BlockJukebox;
    }

    /**
     * LeaderLite verbatim: the block list its Scaffold treats as a placeable cube.
     *
     * @param block the block
     * @return whether the block counts as a solid scaffold block
     */
    public static boolean isSolid(Block block) {
        if (block instanceof BlockStairs) {
            return false;
        }
        if (block instanceof BlockSlab) {
            return false;
        }
        if (block instanceof BlockEndPortalFrame) {
            return false;
        }
        if (block instanceof BlockEndPortal) {
            return false;
        }
        if (block instanceof BlockVine) {
            return false;
        }
        if (block instanceof BlockPumpkin) {
            return false;
        }
        if (block instanceof BlockCactus) {
            return false;
        }
        if (block instanceof BlockBush) {
            return false;
        }
        if (block instanceof BlockFalling) {
            return false;
        }
        if (block instanceof BlockWeb) {
            return false;
        }
        if (block instanceof BlockPane) {
            return false;
        }
        if (block instanceof BlockCarpet) {
            return false;
        }
        if (block instanceof BlockSnow) {
            return false;
        }
        if (block instanceof BlockFence) {
            return false;
        }
        if (block instanceof BlockFenceGate) {
            return false;
        }
        if (block instanceof BlockWall) {
            return false;
        }
        if (block instanceof BlockLadder) {
            return false;
        }
        if (block instanceof BlockTorch) {
            return false;
        }
        if (block instanceof BlockRedstoneWire) {
            return false;
        }
        if (block instanceof BlockRedstoneDiode) {
            return false;
        }
        if (block instanceof BlockBasePressurePlate) {
            return false;
        }
        if (block instanceof BlockTripWire) {
            return false;
        }
        if (block instanceof BlockTripWireHook) {
            return false;
        }
        if (block instanceof BlockRailBase) {
            return false;
        }
        if (block instanceof BlockSlime) {
            return false;
        }
        return !(block instanceof BlockTNT);
    }

    /**
     * LeaderLite verbatim: the hit vector for a face, from the ray trace when it agrees with the
     * requested position and face, otherwise a random point on that face.
     *
     * @param blockPos    the position
     * @param enumFacing  the face
     * @param yaw         the view yaw used for the ray trace
     * @param pitch       the view pitch used for the ray trace
     * @return the hit vector to click with
     */
    public static Vec3 getHitVec(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        MovingObjectPosition movingObjectPosition =
                RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (movingObjectPosition != null
                && movingObjectPosition.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && movingObjectPosition.getBlockPos().equals(blockPos)
                && movingObjectPosition.sideHit == enumFacing) {
            return movingObjectPosition.hitVec;
        }
        return getClickVec(blockPos, enumFacing);
    }

    /**
     * LeaderLite verbatim: a random point on the requested face.
     *
     * @param blockPos   the position
     * @param enumFacing the face
     * @return the click vector
     */
    public static Vec3 getClickVec(BlockPos blockPos, EnumFacing enumFacing) {
        Block block = mc.theWorld.getBlockState(blockPos).getBlock();
        Vec3 vec3 = new Vec3(
                (double) blockPos.getX() + Math.min(Math.max(
                        net.aspw.client.utils.misc.RandomUtils.nextDouble(0.0, 1.0), block.getBlockBoundsMinX()),
                        block.getBlockBoundsMaxX()),
                (double) blockPos.getY() + Math.min(Math.max(
                        net.aspw.client.utils.misc.RandomUtils.nextDouble(0.0, 1.0), block.getBlockBoundsMinY()),
                        block.getBlockBoundsMaxY()),
                (double) blockPos.getZ() + Math.min(Math.max(
                        net.aspw.client.utils.misc.RandomUtils.nextDouble(0.0, 1.0), block.getBlockBoundsMinZ()),
                        block.getBlockBoundsMaxZ()));
        switch (enumFacing) {
            case UP:
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMaxY(), vec3.zCoord);
            case NORTH:
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMinZ());
            case EAST:
                return new Vec3((double) blockPos.getX() + block.getBlockBoundsMaxX(), vec3.yCoord, vec3.zCoord);
            case SOUTH:
                return new Vec3(vec3.xCoord, vec3.yCoord, (double) blockPos.getZ() + block.getBlockBoundsMaxZ());
            case WEST:
                return new Vec3((double) blockPos.getX() + block.getBlockBoundsMinX(), vec3.yCoord, vec3.zCoord);
            case DOWN:
            default:
                return new Vec3(vec3.xCoord, (double) blockPos.getY() + block.getBlockBoundsMinY(), vec3.zCoord);
        }
    }
}
