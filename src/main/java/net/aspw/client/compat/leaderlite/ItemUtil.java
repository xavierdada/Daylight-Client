package net.aspw.client.compat.leaderlite;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * Stand-in for LeaderLite's {@code leader.util.ItemUtil}, restricted to what its Scaffold calls.
 *
 * <p>This project's {@code ItemUtils} is a different utility (item creation and enchantment
 * lookups); the three predicates LeaderLite's Scaffold needs - "is this stack a placeable block",
 * "is this ItemBlock a container", "is a block held right now" - live in LeaderLite's
 * {@code ItemUtil} and are ported here verbatim on top of this layer's {@link BlockUtil}.</p>
 */
public final class ItemUtil {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private ItemUtil() {
    }

    /**
     * LeaderLite verbatim: a placeable block stack is an {@link ItemBlock} whose block is a solid
     * non-container.
     *
     * @param itemStack the stack
     * @return whether the stack can be used as a scaffold block
     */
    public static boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemBlock) {
            return isContainerBlock((ItemBlock) item);
        }
        return false;
    }

    /**
     * LeaderLite verbatim.
     *
     * @param itemBlock the item block
     * @return whether the block is solid and not right-clickable
     */
    public static boolean isContainerBlock(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (BlockUtil.isInteractable(block)) {
            return false;
        }
        return BlockUtil.isSolid(block);
    }

    /**
     * LeaderLite verbatim: the held stack is a placeable block.
     *
     * @return whether a usable scaffold block is held
     */
    public static boolean isHoldingBlock() {
        return isBlock(mc.thePlayer.getHeldItem());
    }
}
