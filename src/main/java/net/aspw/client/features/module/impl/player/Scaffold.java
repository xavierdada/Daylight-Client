package net.aspw.client.features.module.impl.player;

import net.aspw.client.Launch;
import net.aspw.client.compat.leaderlite.BlockUtil;
import net.aspw.client.compat.leaderlite.BooleanProperty;
import net.aspw.client.compat.leaderlite.EventType;
import net.aspw.client.compat.leaderlite.FloatProperty;
import net.aspw.client.compat.leaderlite.IntProperty;
import net.aspw.client.compat.leaderlite.ItemUtil;
import net.aspw.client.compat.leaderlite.ModeProperty;
import net.aspw.client.compat.leaderlite.MoveUtil;
import net.aspw.client.compat.leaderlite.PlayerUtil;
import net.aspw.client.compat.leaderlite.Priority;
import net.aspw.client.compat.leaderlite.RotationUtil;
import net.aspw.client.compat.leaderlite.UpdateEvent;
import net.aspw.client.event.EventState;
import net.aspw.client.event.EventTarget;
import net.aspw.client.event.MotionEvent;
import net.aspw.client.event.Render2DEvent;
import net.aspw.client.event.Render3DEvent;
import net.aspw.client.event.StrafeEvent;
import net.aspw.client.event.TickEvent;
import net.aspw.client.event.WorldEvent;
import net.aspw.client.features.module.Module;
import net.aspw.client.features.module.ModuleCategory;
import net.aspw.client.features.module.ModuleInfo;
import net.aspw.client.features.module.impl.movement.LongJump;
import net.aspw.client.features.module.impl.visual.Interface;
import net.aspw.client.utils.Rotation;
import net.aspw.client.utils.RotationUtils;
import net.aspw.client.utils.misc.RandomUtils;
import net.aspw.client.utils.render.RenderUtils;
import net.aspw.client.value.ListValue;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Scaffold, ported from LeaderLite's {@code leader.module.modules.player.Scaffold}.
 *
 * <p>This is that file's body, with only the adaptations the surrounding client needs (listed in
 * full in {@code net.aspw.client.compat.leaderlite}):</p>
 * <ul>
 *   <li>{@code leader.module.Module} / {@code @Module} -&gt; this project's {@link Module} /
 *       {@link ModuleInfo}.</li>
 *   <li>{@code leader.property.properties.*} -&gt; the {@code compat.leaderlite.*Property} adapters,
 *       which <em>are</em> this project's {@code Value} objects ({@code BooleanProperty extends BoolValue},
 *       {@code IntProperty extends IntegerValue}, {@code FloatProperty extends FloatValue},
 *       {@code ModeProperty extends ListValue}) so that {@code Module#values} discovers every one of them
 *       and the click GUI shows them. Setting names are kept from LeaderLite.</li>
 *   <li>{@code ModeProperty}'s index API is spelled {@code getModeIndex()}/{@code setValue(int)} here,
 *       because {@code ListValue#getValue()} already returns the selected mode name; every index
 *       comparison below was rewritten from {@code getValue()} to {@code getModeIndex()}.
 *       {@code getModeString()} still returns the mode name.</li>
 *   <li>{@code leader.events.UpdateEvent} -&gt; the ported {@link UpdateEvent} adapter over this
 *       project's {@link MotionEvent}, so the {@code PRE}/{@code POST} flow and
 *       {@code setRotation}/{@code setPervRotation} calls are unchanged.</li>
 *   <li>{@code leader.util.*} -&gt; the {@code compat.leaderlite} stand-ins.</li>
 *   <li>The render/counter HUD is gone: this client's {@code DynamicIsland} already renders the
 *       Scaffold row, so the port only maintains {@link #getBlocksAmount()} and
 *       {@link #getBlocksPerSecond()} for it. See the report for the full list.</li>
 *   <li>{@code Leader.moduleManager.modules.get(X.class)} -&gt; {@code Launch.moduleManager.getModule(X.class)};
 *       the two modules LeaderLite consulted but this client does not have (BedNuker, Stuck) are
 *       gone too, which is why {@code canPlace()} and {@code updateClutch()} are shortened.</li>
 * </ul>
 */
@ModuleInfo(name = "Scaffold", category = ModuleCategory.PLAYER)
public class Scaffold extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
            0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
            0.78125, 0.84375, 0.90625, 0.96875
    };

    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Telly", "Snap", "Legit", "LegitTelly"});
    public final ModeProperty rotationMode = new ModeProperty("Rotate Mode", 3, new String[]{"None", "Vanilla", "Backwards", "Prediction", "Strict", "GodBridge"});
    public final BooleanProperty noUpdateWhenCanPlace = new BooleanProperty("Do Not Update Rotation When Can Place", false, () -> rotationMode.getModeIndex() == 5);
    public final BooleanProperty edgeLimit = new BooleanProperty("Edge Limit", false, () -> rotationMode.getModeIndex() == 5);
    public final FloatProperty godBridgeTolerance = new FloatProperty("GodBridge Yaw Tolerance", 5.0F, 0.0F, 10.0F, () -> rotationMode.getModeIndex() == 5);
    public final ModeProperty moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent"});
    public final IntProperty jumpDelay = new IntProperty("Jump Delay", 2, 0, 5, () -> mode.getModeIndex() == 1 || mode.getModeIndex() == 4);
    public final IntProperty placeDelay = new IntProperty("Place Delay", 1, 0, 5);
    public final FloatProperty startRotSpeed = new FloatProperty("Start Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 1);
    public final FloatProperty normalRotSpeed = new FloatProperty("Normal Rotate Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 1);
    public final FloatProperty normalModeSpeed = new FloatProperty("Normal Mode Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 0);
    public final FloatProperty legitModeSpeed = new FloatProperty("Legit Mode Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 3);
    public final BooleanProperty swing = new BooleanProperty("Swing", true);
    // TASK(Scaffold-clutch-off-by-default):上游默认 true —— 它每 10 tick 里 9 tick 冻结本地 motion
    // (现在改成切 Stuck,效果相同)。"本地速度被抹掉、服务端预测还在按旧速度外推"正是 Grim 的
    // Simulation 检查所测的东西(实测 触发了 Simulation (×120) .345144),所以在预测型反作弊服上默认关。
    // 原版行为一字未改:需要的时候在 GUI 里打开即可。
    public final BooleanProperty clutch = new BooleanProperty("Clutch", false);
    public final BooleanProperty onlyInVoid = new BooleanProperty("Only Void", false, () -> clutch.getValue());
    public final BooleanProperty bPSRender = new BooleanProperty("Render BPS", true);
    public final BooleanProperty blockCounter = new BooleanProperty("Block Counter", false);
    public final BooleanProperty airRescue = new BooleanProperty("Air Rescue", true);
    public final BooleanProperty strictRaytrace = new BooleanProperty("Strict Raytrace", false);
    public final BooleanProperty ctrlToSwitchTelly = new BooleanProperty("Ctrl To Switch Telly", false);
    public final FloatProperty edgeThreshold = new FloatProperty("Edge Threshold", 0.15F, 0.01F, 0.5F, () -> mode.getModeIndex() == 2);
    public final BooleanProperty ticksLimit = new BooleanProperty("Ticks Limit", false, () -> mode.getModeIndex() == 2);
    public final IntProperty limitTicks = new IntProperty("Limit Ticks", 10, 1, 40, () -> mode.getModeIndex() == 2 && ticksLimit.getValue());
    public final FloatProperty snapForwardSpeed = new FloatProperty("Forward Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 2);
    public final FloatProperty snapBackSpeed = new FloatProperty("Back Speed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 2);
    public final BooleanProperty snapRotation = new BooleanProperty("Snap Rotation", false, () -> mode.getModeIndex() == 2);
    public final BooleanProperty speedLimit = new BooleanProperty("Speed Limit", false, () -> mode.getModeIndex() == 1);
    public final IntProperty speedLimitTicks = new IntProperty("Speed Limit Ticks", 3, 0, 5, () -> mode.getModeIndex() == 1 && speedLimit.getValue());
    public final IntProperty forwardRotationTicks = new IntProperty("Forward Rotation Ticks", 1, 1, 5, () -> mode.getModeIndex() == 1 && speedLimit.getValue());
    public final IntProperty legitSneakDelay = new IntProperty("Legit Sneak Delay", 4, 1, 5, () -> mode.getModeIndex() == 3);
    public final FloatProperty forwardSpeed = new FloatProperty("ForwardSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 4);
    public final FloatProperty backSpeed = new FloatProperty("BackSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 4);
    public final FloatProperty placeSpeed = new FloatProperty("PlaceSpeed", 180.0F, 1.0F, 180.0F, () -> mode.getModeIndex() == 4);
    public final IntProperty tellyTicks = new IntProperty("TellyTicks", 3, 1, 6, () -> mode.getModeIndex() == 4);

    /**
     * Sprint handling, kept as LeaderLite spelled it: {@code MixinEntityPlayerSP} reads this field
     * directly (it must stay public, non-static and of type {@link ListValue}).
     */
    public final ListValue sprintModeValue = new ListValue("SprintMode", new String[]{"Same", "Ground", "Air", "Off"}, "Same", () -> true);

    private int rotationTick = 0;
    /** Hotbar slot the module restores on disable. Read by {@code PlayerUtils}; public, not static. */
    public int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int tellyJumpDelayTimer = 0;
    private int jumpDelayOverride = -1;
    private boolean wasInAir = false;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;

    /**
     * Whether the module is currently towering. LeaderLite kept the same flag private as
     * {@code towering} with no accessor; this client's {@code MixinEntityPlayerSP} reads it through
     * {@code scaffold.getCanTower()}, so the LeaderLite pipeline uses that name here.
     */
    public boolean canTower = false;

    /**
     * A public Java field does not generate a bean accessor, and the client's
     * {@code MixinEntityPlayerSP} reads this through {@code scaffold.getCanTower()}.
     */
    public boolean getCanTower() {
        return canTower;
    }

    private boolean clutchActive = false;
    private int clutchTickCounter = 0;

    /** Clutch 期间是不是"本模块把 Stuck 打开的"，退出时只关我们开的那一份。 */
    private boolean clutchStuckOwned = false;
    private EnumFacing targetFacing = null;
    public static int count = 0;
    private int placeDelayCounter = 0;

    /**
     * 连续多少个 tick 因为"服务器那条朝向看不到要点的方块"而忍住没放。超过上限就照放,
     * 避免又变成一块都放不出来。见 {@link #place}。
     */
    private int ghostTicks = 0;
    private double prevBpsX, prevBpsZ;
    private float currentBps;
    private int counterMax = 0;
    private float animBps = 0.0F;
    private float animPercent = 0.0F;
    private long lastHudFrame = 0L;
    private boolean snapForward = true;
    private int snapForwardTimer = 0;
    private boolean snapLocked = false;
    private int airTicks = 0;
    private boolean pendingSpeedLimitRot = false;
    private int forwardRotateTicksLeft = 0;
    private int legitEdgeState = 0;
    private int legitEdgeTimer = 0;
    private boolean legitWasOnEdge = false;
    private int legitTellyPhase = 0;
    private int legitTellyPhaseTicks = 0;
    private boolean legitTellyWasAirborne = false;
    private boolean legitTellyPlacedFirstBlock = false;
    private float legitTellySilentYaw;
    private float legitTellySilentPitch;
    private BlockData legitTellyLockedBlockData;
    private UpdateEvent currentEvent;
    private boolean ctrlTellyActive = false;
    private int ctrlSavedMode = -1;
    private int ctrlSavedRotationMode = -1;

    /** Yaw written through {@code setPervRotation}, consumed by {@link #handleMoveInput()}. */
    private float moveYaw = Float.NaN;

    /** Rolling window of placement timestamps, the source of {@link #getBlocksPerSecond()}. */
    private final ArrayDeque<Long> placementTimes = new ArrayDeque<>();

    /** Width of the window {@link #getBlocksPerSecond()} averages over. */
    private static final long PLACEMENT_WINDOW_MS = 1000L;

    /** Hard cap on remembered placements. */
    private static final int MAX_PLACEMENT_SAMPLES = 64;

    // LeaderLite's Module base class took the name and "array list" flag in its constructor; this
    // project's takes both from @ModuleInfo, so no constructor is declared at all.

    @Override
    public String getTag() {
        return mode.getModeString();
    }

    /**
     * The hotbar slot the module switched away from. LeaderLite exposed the same value through
     * {@code getSlot()}, but nothing in this client reads it, so it is not declared here; the field
     * itself stays public (see above) because {@code PlayerUtils} reads the property form.
     */

    // ---------------------------------------------------------------------------------------------
    // Hotbar bookkeeping
    // ---------------------------------------------------------------------------------------------

    /**
     * Total amount of usable scaffold blocks in the hotbar.
     *
     * <p>Public because this client's {@code DynamicIsland} renders "{@code N blocks left}" for the
     * Scaffold row; the old Kotlin module exposed the same property under the same name, and a Java
     * getter keeps the Kotlin call site {@code scaffold.blocksAmount} working unchanged.</p>
     *
     * @return the number of placeable blocks the player is carrying
     */
    public int getBlocksAmount() {
        if (mc.thePlayer == null) {
            return 0;
        }
        int amount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || stack.stackSize <= 0) {
                continue;
            }
            if (ItemUtil.isBlock(stack)) {
                amount += stack.stackSize;
            }
        }
        return amount;
    }

    /**
     * Placements per second over the last second. Public for {@code DynamicIsland}, same contract as
     * the old Kotlin module's {@code blocksPerSecond}.
     *
     * @return the number of placements still inside the one second window
     */
    public float getBlocksPerSecond() {
        long cutoff = System.currentTimeMillis() - PLACEMENT_WINDOW_MS;
        while (!this.placementTimes.isEmpty() && this.placementTimes.peekFirst() < cutoff) {
            this.placementTimes.pollFirst();
        }
        return this.placementTimes.size();
    }

    /** Remembers one successful placement for {@link #getBlocksPerSecond()}. */
    private void recordPlacement() {
        long now = System.currentTimeMillis();
        this.placementTimes.addLast(now);
        long cutoff = now - PLACEMENT_WINDOW_MS;
        while (this.placementTimes.size() > MAX_PLACEMENT_SAMPLES
                || (!this.placementTimes.isEmpty() && this.placementTimes.peekFirst() < cutoff)) {
            this.placementTimes.pollFirst();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // LeaderLite's helper block, unchanged apart from the utility call sites
    // ---------------------------------------------------------------------------------------------

    /**
     * LeaderLite's {@code canPlace()}, with the BedNuker check dropped (this client has no BedNuker)
     * and the LongJump check kept in the form this client's LongJump module can answer.
     *
     * @return whether scaffolding may act this tick
     */
    private boolean canPlace() {
        LongJump longJump = Launch.moduleManager.getModule(LongJump.class);
        if (longJump == null || !longJump.getState()) {
            return true;
        }
        // LeaderLite only blocked while its LongJump was in its own Auto mode; this client's LongJump
        // has no equivalent flag, so an enabled LongJump blocks for as long as it is on.
        return false;
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5,
                            (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private static final double FACE_DEPTH = 0.001;

    private boolean isValidHit(MovingObjectPosition mop, BlockPos blockPos, EnumFacing facing) {
        return mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockPos) && mop.sideHit == facing;
    }

    private Vec3 facePoint(BlockPos blockPos, EnumFacing facing, double a, double b) {
        double n = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0 - FACE_DEPTH : FACE_DEPTH;
        switch (facing.getAxis()) {
            case X:
                return new Vec3(blockPos.getX() + n, blockPos.getY() + a, blockPos.getZ() + b);
            case Y:
                return new Vec3(blockPos.getX() + a, blockPos.getY() + n, blockPos.getZ() + b);
            default:
                return new Vec3(blockPos.getX() + a, blockPos.getY() + b, blockPos.getZ() + n);
        }
    }

    /**
     * TASK(Grim-RotationPlace):取一维候选坐标里"位于方块面内侧"的那些(离四条边都 &gt;= 0.25)。
     *
     * <p>长度 &lt;= 1 的数组是"钉死在被点那个面上的那一维"(0.02 / 0.98),必须原样保留,否则射线就
     * 不再指向被点的那个面了。一个内侧点都没有时也返回原数组,调用方据此退回全量搜索。</p>
     *
     * @param offsets the candidate offsets of one axis
     * @return the inner subset of {@code offsets}, or {@code offsets} itself when there is none
     */
    private static double[] innerFaceOffsets(double[] offsets) {
        // TASK(Scaffold-align-upstream):这一层"优先挑面内侧点"已经停用,恢复 LeaderLite 原版的候选
        // 打分方式(25 个候选点里挑离当前朝向最近的那个)。
        //
        // 原因:它虽然能让射线更稳,但**改变了最终瞄的点、也就是改变了发出去的朝向**;而用户实测
        // LeaderLite 原版在 Grim 上(Snap 模式)本来就干净 —— 原版也不需要这一层。
        // 恒等返回会让调用方那套"两段搜索"退化成原版的单段搜索:第一段(全量候选)就会命中,
        // 第二段永远不会跑。
        return offsets;
    }

    private Vec3 applyRescueRotation(BlockData blockData, UpdateEvent event) {
        BlockPos pos = blockData.blockPos();
        EnumFacing facing = blockData.facing();
        float bestYaw = -180.0F, bestPitch = 0.0F;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestHit = null;
        for (double a : placeOffsets) {
            for (double b : placeOffsets) {
                Vec3 target = this.facePoint(pos, facing, a, b);
                float[] rot = RotationUtil.getRotations(target.xCoord, target.yCoord, target.zCoord);
                rot[1] = Math.max(-90.0F, Math.min(90.0F, rot[1]));
                MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1],
                        mc.playerController.getBlockReachDistance(), 1.0F);
                if (!this.isValidHit(mop, pos, facing)) {
                    continue;
                }
                float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - event.getYaw()));
                float pitchDiff = rot[1] - event.getPitch();
                double dist = yawDiff * yawDiff + pitchDiff * pitchDiff;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestYaw = rot[0];
                    bestPitch = rot[1];
                    bestHit = mop.hitVec;
                }
            }
        }
        if (bestHit == null) {
            return null;
        }
        this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
        this.pitch = bestPitch;
        this.canRotate = true;
        return bestHit;
    }

    private BlockData getBlockData() {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(playerY, this.startY) : playerY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        return this.getBlockData(targetPos);
    }

    private BlockData getBlockData(BlockPos targetPos) {
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        }
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos) && !BlockUtil.isInteractable(pos)
                            && mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5,
                            (double) pos.getZ() + 0.5) <= (double) mc.playerController.getBlockReachDistance()) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN && BlockUtil.isReplaceable(pos.offset(facing))) {
                                positions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5,
                (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)));

        // TASK(Scaffold-align-upstream):这里原本加过一段"优先挑服务器手上那条朝向看得见的支撑方块"。
        // 已照 LeaderLite 原版去掉 —— 换支撑方块等于改变**点的是哪个方块**,而 Snap 模式的安全性来自
        // 放置时机本身(向前 snap 的半程不做方块搜索,见 update 里 `mode == 2 && snapForward` 那一段),
        // 在选方块这里动手脚只会让搭出来的位置和原版不一致。
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private boolean place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (!ItemUtil.isHoldingBlock() || this.blockCount <= 0) {
            return false;
        }

        // TASK(Scaffold-align-upstream):这里原本加过一层"服务器那条朝向看不见这个方块就先不放
        // (最多忍 8 tick)"的前置校验。已照 LeaderLite 原版去掉。
        //
        // 原因(用户实测 + 上游源码):
        //   * 上游 Snap 模式(`mode.getValue() == 2 && snapForward -> blockData = null`)只在回摆的
        //     半程放置,位置与朝向都是稳的 —— 安全性来自**放置时机**,不需要拿旧朝向来卡;
        //   * 拿 serverRotation 预判还会因为 FakeLag/BackTrack/NoFall 这类延迟重排包的模块而失准,
        //     结果是"该放的没放"(空档),而不是更安全;
        //   * RotationPlace 那种 ×138 是 Rotate Mode = Backwards 造成的(朝向与被点方块方向相反),
        //     把 Rotate Mode 设成 Prediction / 用 Snap 模式即可,不需要在 place() 里加校验。
        //
        // 下面只保留上游原本就有的可选项 Strict Raytrace(默认关)。

        if (this.strictRaytrace.getValue()) {
            float aimYaw = this.currentEvent != null ? this.currentEvent.getNewYaw() : mc.thePlayer.rotationYaw;
            float aimPitch = this.currentEvent != null ? this.currentEvent.getNewPitch() : mc.thePlayer.rotationPitch;
            MovingObjectPosition mop = RotationUtil.rayTrace(aimYaw, aimPitch,
                    mc.playerController.getBlockReachDistance(), 1.0F);
            if (!this.isValidHit(mop, blockPos, enumFacing)) {
                return false;
            }
            vec3 = mop.hitVec;
        }
        if (!mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
            return false;
        }
        if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
            this.blockCount--;
        }
        if (this.swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
        }
        this.recordPlacement();
        return true;
    }

    private void selectScaffoldBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = ItemUtil.isBlock(held) ? held.stackSize : 0;
        this.blockCount = Math.min(this.blockCount, heldCount);
        if (this.blockCount > 0) {
            return;
        }

        int slot = mc.thePlayer.inventory.currentItem;
        if (this.blockCount == 0) {
            slot--;
        }
        for (int i = slot; i > slot - 9; i--) {
            int hotbarSlot = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
            if (ItemUtil.isBlock(candidate)) {
                mc.thePlayer.inventory.currentItem = hotbarSlot;
                this.blockCount = candidate.stackSize;
                return;
            }
        }
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(mc.thePlayer.rotationYaw,
                (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue());
    }

    private boolean isLegitTellyMode() {
        return this.mode.getModeIndex() == 4;
    }

    private float getLegitTellyRotationStep(float speed) {
        return Math.max(1.0F, Math.min(180.0F, speed));
    }

    private float[] smoothLegitTellyRotation(float fromYaw, float fromPitch, float targetYaw,
                                             float targetPitch, float speed) {
        float yawStep = this.getLegitTellyRotationStep(speed);
        float pitchStep = Math.max(1.0F, yawStep * 0.55F);
        float yawDelta = MathHelper.wrapAngleTo180_float(targetYaw - fromYaw);
        float pitchDelta = targetPitch - fromPitch;
        float nextYaw = fromYaw + RotationUtil.clampAngle(yawDelta, yawStep);
        float nextPitch = fromPitch + RotationUtil.clampAngle(pitchDelta, pitchStep);
        return new float[]{
                RotationUtil.quantizeAngle(nextYaw),
                RotationUtil.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F))
        };
    }

    private void resetLegitTellyCycle() {
        this.legitTellyPhase = 0;
        this.legitTellyPhaseTicks = 0;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
    }

    private void updateLegitTelly(UpdateEvent event) {
        if (this.placeDelayCounter > 0) {
            this.placeDelayCounter--;
        }

        boolean onGround = mc.thePlayer.onGround;

        if (onGround && this.legitTellyWasAirborne) {
            this.resetLegitTellyCycle();
        }
        if (!onGround && !this.legitTellyWasAirborne && this.legitTellyPhase == 0) {
            this.legitTellyPhase = 1;
            this.legitTellyPhaseTicks = 0;
        }

        if (this.legitTellyPhase == 1) {
            this.legitTellyPhaseTicks++;
            if (this.legitTellyPhaseTicks >= this.tellyTicks.getValue()) {
                this.legitTellyPhase = 2;
                this.legitTellyPhaseTicks = 0;
            }
        }

        float speed = this.forwardSpeed.getValue();
        if (this.legitTellyPhase == 2) {
            speed = this.backSpeed.getValue();
        } else if (this.legitTellyPhase == 3) {
            speed = this.placeSpeed.getValue();
        }

        float targetYaw = mc.thePlayer.rotationYaw;
        float targetPitch = mc.thePlayer.rotationPitch;

        if (this.legitTellyPhase == 1) {
            targetYaw = this.getCurrentYaw();
            targetPitch = 0.0F;
        }

        if (this.legitTellyPhase >= 2) {
            if (this.legitTellyLockedBlockData != null
                    && !BlockUtil.isReplaceable(this.legitTellyLockedBlockData.blockPos()
                    .offset(this.legitTellyLockedBlockData.facing()))) {
                // The face is gone; drop the lock below.
                this.legitTellyLockedBlockData = null;
            }
            if (this.legitTellyLockedBlockData == null) {
                BlockPos targetPos = new BlockPos(
                        MathHelper.floor_double(mc.thePlayer.posX),
                        this.startY,
                        MathHelper.floor_double(mc.thePlayer.posZ)
                );
                this.legitTellyLockedBlockData = this.getBlockData(targetPos);
            }

            if (this.legitTellyLockedBlockData != null) {
                BlockData bd = this.legitTellyLockedBlockData;
                double[] offsets = {0.15, 0.35, 0.5, 0.65, 0.85};
                double[] xOff = offsets, yOff = offsets, zOff = offsets;
                switch (bd.facing()) {
                    case NORTH:
                        zOff = new double[]{0.02};
                        break;
                    case EAST:
                        xOff = new double[]{0.98};
                        break;
                    case SOUTH:
                        zOff = new double[]{0.98};
                        break;
                    case WEST:
                        xOff = new double[]{0.02};
                        break;
                    case DOWN:
                        yOff = new double[]{0.02};
                        break;
                    case UP:
                    default:
                        yOff = new double[]{0.98};
                        break;
                }
                double bestDist = Double.MAX_VALUE;
                for (double dx : xOff) {
                    for (double dy : yOff) {
                        for (double dz : zOff) {
                            float[] rot = RotationUtil.getRotations(
                                    bd.blockPos().getX() + dx,
                                    bd.blockPos().getY() + dy,
                                    bd.blockPos().getZ() + dz);
                            MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1],
                                    mc.playerController.getBlockReachDistance(), 1.0F);
                            if (!this.isValidHit(mop, bd.blockPos(), bd.facing())) {
                                continue;
                            }
                            double dist = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.legitTellySilentYaw))
                                    + Math.abs(rot[1] - this.legitTellySilentPitch);
                            if (dist < bestDist) {
                                bestDist = dist;
                                targetYaw = rot[0];
                                targetPitch = rot[1];
                            }
                        }
                    }
                }
            }
        }

        float[] smoothed = this.smoothLegitTellyRotation(
                this.legitTellySilentYaw, this.legitTellySilentPitch, targetYaw, targetPitch, speed
        );
        this.legitTellySilentYaw = smoothed[0];
        this.legitTellySilentPitch = smoothed[1];
        this.yaw = smoothed[0];
        this.pitch = smoothed[1];
        this.canRotate = true;

        event.setRotation(this.yaw, this.pitch, 3);
        if (this.moveFix.getModeIndex() == 1) {
            event.setPervRotation(this.yaw, 3);
        }

        if (this.legitTellyPhase >= 2 && this.legitTellyLockedBlockData != null && this.placeDelayCounter <= 0) {
            MovingObjectPosition trace = RotationUtil.rayTrace(
                    this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F
            );
            if (this.isValidHit(trace, this.legitTellyLockedBlockData.blockPos(),
                    this.legitTellyLockedBlockData.facing())) {
                if (this.place(this.legitTellyLockedBlockData.blockPos(),
                        this.legitTellyLockedBlockData.facing(), trace.hitVec)) {
                    this.placeDelayCounter = this.placeDelay.getValue();
                    if (this.legitTellyPhase == 2) {
                        this.legitTellyPlacedFirstBlock = true;
                        this.legitTellyPhase = 3;
                    }
                    this.legitTellyLockedBlockData = null;
                }
            }
        }

        this.legitTellyWasAirborne = !onGround;
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (!MoveUtil.isForwardPressed()) {
            return false;
        }
        if (PlayerUtil.isAirAbove()) {
            return false;
        }
        if (mc.thePlayer.onGround) {
            if (this.stage > 0 || mc.gameSettings.keyBindJump.isKeyDown()) {
                return true;
            }
        }
        return this.tellyJumpDelayTimer > 0;
    }

    private boolean isOnEdge() {
        if (!mc.thePlayer.onGround) {
            return true;
        }
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) {
            return true;
        }
        double threshold = this.edgeThreshold.getValue();
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < threshold || xOff > 1.0 - threshold || zOff < threshold || zOff > 1.0 - threshold) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX)
                    + (xOff < threshold ? -1 : (xOff > 1.0 - threshold ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ)
                    + (zOff < threshold ? -1 : (zOff > 1.0 - threshold ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX)
                    || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGodBridgeOnEdge() {
        if (!mc.thePlayer.onGround) {
            return true;
        }
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) {
            return true;
        }
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < 0.15 || xOff > 0.85 || zOff < 0.15 || zOff > 0.85) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < 0.15 ? -1 : (xOff > 0.85 ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < 0.15 ? -1 : (zOff > 0.85 ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX)
                    || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) {
                    return true;
                }
            }
        }
        return false;
    }

    private float quantizeDiagonal(float yaw) {
        return 45.0F + 90.0F * Math.round((yaw - 45.0F) / 90.0F);
    }

    /**
     * LeaderLite's {@code updateClutch}: every tenth tick it toggles its {@code Stuck} module on
     * (nine ticks frozen, one tick released so the fall continues).
     *
     * <p>Daylight now has that module too (see {@code movement/Stuck}), so this is the real thing
     * instead of the inline {@code motionX/Y/Z = 0} stand-in the original port had to use. The
     * difference matters: {@code Stuck} also holds the outbound position packets (via Blink), so
     * the server does not extrapolate the motion the client just threw away — that mismatch was
     * the rubber-band the inline stand-in produced.</p>
     */
    private void updateClutch() {
        if (!this.clutch.getValue()) {
            if (this.clutchActive) {
                this.clutchReset();
            }
            return;
        }
        if (mc.thePlayer.onGround) {
            if (this.clutchActive) {
                this.clutchReset();
            }
            return;
        }
        if (this.bbUnC()) {
            if (this.clutchActive) {
                this.clutchReset();
            }
            return;
        }
        double fallDistance = mc.thePlayer.fallDistance;
        boolean shouldClutch = fallDistance > 2 && !PlayerUtil.isAirAbove()
                && !mc.thePlayer.isCollidedHorizontally
                && (!this.onlyInVoid.getValue() || this.isFallingIntoVoid());
        if (shouldClutch && !this.clutchActive) {
            this.clutchActive = true;
            this.clutchTickCounter = 0;
        }
        if (this.clutchActive) {
            this.clutchTickCounter++;
            net.aspw.client.features.module.impl.movement.Stuck clutchStuck = Launch.moduleManager.getModule(net.aspw.client.features.module.impl.movement.Stuck.class);
            if (clutchStuck != null) {
                if (this.clutchTickCounter % 10 != 0) {
                    // 冻结的那 9 tick:开着(是我们开的才记 ownership,退出时只关我们开的那一份)。
                    if (!clutchStuck.getState()) {
                        this.clutchStuckOwned = true;
                        clutchStuck.setState(true);
                    }
                } else if (this.clutchStuckOwned) {
                    clutchStuck.setState(false);
                    this.clutchStuckOwned = false;
                }
            }
        }
    }

    private void clutchReset() {
        this.clutchActive = false;
        this.clutchTickCounter = 0;
        if (this.clutchStuckOwned) {
            this.clutchStuckOwned = false;
            net.aspw.client.features.module.impl.movement.Stuck clutchStuck = Launch.moduleManager.getModule(net.aspw.client.features.module.impl.movement.Stuck.class);
            if (clutchStuck != null) {
                clutchStuck.setState(false);
            }
        }
    }

    private boolean isFallingIntoVoid() {
        if (mc.thePlayer == null) {
            return false;
        }
        for (int i = 0; i <= 128; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY) - i, MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private boolean bbUnC() {
        if (mc.thePlayer == null) {
            return false;
        }
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        for (int i = 1; i <= 2; i++) {
            BlockPos checkPos = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), playerY - i,
                    MathHelper.floor_double(mc.thePlayer.posZ));
            if (mc.theWorld.getBlockState(checkPos).getBlock().getMaterial().isSolid()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    /**
     * LeaderLite's {@code onUpdate(UpdateEvent)}. The event is the ported adapter, so the body below
     * is unchanged; only the subscription moved from LeaderLite's bus to this project's
     * {@link MotionEvent}. {@code Priority.HIGH} is kept as the ordering hint.
     */
    @EventTarget(priority = Priority.HIGH)
    public void onUpdate(MotionEvent motionEvent) {
        UpdateEvent event = new UpdateEvent(motionEvent);
        if (this.getState() && event.getType() == EventType.PRE) {
            this.currentEvent = event;
            this.moveYaw = Float.NaN;
            boolean tellyMode = this.mode.getModeIndex() == 1;
            boolean legitTellyMode = this.isLegitTellyMode();
            boolean tellyLikeMode = tellyMode || legitTellyMode;
            boolean legitMode = this.mode.getModeIndex() == 3;

            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (this.forwardRotateTicksLeft > 0) {
                this.forwardRotateTicksLeft--;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.canTower = false;
                if (this.wasInAir) {
                    this.tellyJumpDelayTimer = tellyLikeMode
                            ? (this.jumpDelayOverride >= 0 ? this.jumpDelayOverride : this.jumpDelay.getValue())
                            : 0;
                    this.wasInAir = false;
                }
                if (this.tellyJumpDelayTimer > 0) {
                    this.tellyJumpDelayTimer--;
                }
                if (this.speedLimit.getValue()) {
                    this.pendingSpeedLimitRot = false;
                    this.airTicks = 0;
                }
            } else {
                if (this.speedLimit.getValue()) {
                    this.airTicks++;
                }
                this.wasInAir = true;
            }
            if (tellyLikeMode && mc.thePlayer.onGround && MoveUtil.isForwardPressed()
                    && !mc.gameSettings.keyBindJump.isKeyDown() && this.stage == 0) {
                this.stage = 1;
            }
            if (tellyLikeMode) {
                this.jumpDelayOverride = mc.gameSettings.keyBindJump.isKeyDown() ? 2 : -1;
            } else {
                this.jumpDelayOverride = -1;
                this.tellyJumpDelayTimer = 0;
            }
            this.updateClutch();

            if (legitTellyMode) {
                this.selectScaffoldBlock();
                this.updateLegitTelly(event);
                return;
            }

            if (this.mode.getModeIndex() == 2) {
                if (this.ticksLimit.getValue()) {
                    boolean canForward = mc.thePlayer.onGround && !this.isOnEdge();
                    if (!canForward) {
                        this.snapForward = false;
                        this.snapForwardTimer = 0;
                        this.snapLocked = false;
                    } else {
                        if (this.snapLocked) {
                            this.snapForward = false;
                        } else {
                            if (!this.snapForward) {
                                this.snapForward = true;
                                this.snapForwardTimer = 1;
                            } else {
                                this.snapForwardTimer++;
                                if (this.snapForwardTimer >= this.limitTicks.getValue()) {
                                    this.snapForward = false;
                                    this.snapLocked = true;
                                    this.snapForwardTimer = 0;
                                }
                            }
                        }
                    }
                } else {
                    this.snapForward = mc.thePlayer.onGround && !this.isOnEdge();
                }
            }

            if (legitMode) {
                boolean onGround = mc.thePlayer.onGround;
                boolean atEdge = onGround && this.isOnEdge();
                boolean holdingBlock = ItemUtil.isHoldingBlock();
                boolean justReachedEdge = atEdge && !this.legitWasOnEdge;
                if (!onGround) {
                    this.legitEdgeState = 0;
                    this.legitEdgeTimer = 0;
                } else if (atEdge && holdingBlock) {
                    switch (this.legitEdgeState) {
                        case 0:
                            if (justReachedEdge || this.legitEdgeTimer == 0) {
                                this.legitEdgeState = 1;
                                this.legitEdgeTimer = this.legitSneakDelay.getValue();
                            }
                            break;
                        case 1:
                            this.legitEdgeTimer--;
                            if (this.legitEdgeTimer <= 0) {
                                this.legitEdgeState = 2;
                                this.legitEdgeTimer = 0;
                            }
                            break;
                        case 2:
                            break;
                        default:
                            this.legitEdgeState = 0;
                            this.legitEdgeTimer = 0;
                            break;
                    }
                } else {
                    this.legitEdgeState = 0;
                    this.legitEdgeTimer = 0;
                }
                this.legitWasOnEdge = atEdge;
            }

            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int held = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, held);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }

                // TASK(Scaffold-align-upstream-snap):这里保持 LeaderLite 原版逐行一致 —— 两个分支都保留。
                //
                // 说明(免得以后又有人把它当 bug 改掉):
                //   * getCurrentYaw() = MoveUtil.adjustYaw(鼠标朝向, 前后输入, 左右输入),和鼠标朝向可能差
                //     90~180°;所以"没方块可放"时(贴边缘、没有可点支撑)这两行会把朝向甩到 ±180°;
                //   * 上游就是这个行为,而且它是**静默朝向** —— 只通过 event.setRotation 发给服务器,
                //     LeaderLite 里玩家自己的视角不会动,所以玩的时候看不见;
                //   * Daylight 能看见猛甩,是因为多了个 LeaderLite 没有的模块 SilentRotations:它每帧执行
                //     mc.thePlayer.rotationYaw/Pitch = RotationUtils.targetRotation,等于把这个静默朝向
                //     显示出来。要"看不见",关掉 SilentRotations(见 Grim.txt 第一节),不是改这里。
                if (this.mode.getModeIndex() == 2) {
                    if (this.snapForward) {
                        this.yaw = RotationUtil.quantizeAngle(this.getCurrentYaw());
                        this.pitch = 80.0F;
                        this.canRotate = true;
                    } else if (!this.snapRotation.getValue()) {
                        this.yaw = RotationUtil.quantizeAngle(this.getCurrentYaw() + 180.0F);
                        this.pitch = 85.0F;
                        this.canRotate = true;
                    }
                }

                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw) ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F
                        * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());

                if (!this.canRotate) {
                    switch (this.rotationMode.getModeIndex()) {
                        case 1:
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                        case 5:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(RotationUtil.wrapAngleDiff(
                                        this.quantizeDiagonal(currentYaw + 180.0F), event.getYaw()));
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                        default:
                            break;
                    }
                }

                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (this.mode.getModeIndex() == 2 && this.snapForward) {
                    blockData = null;
                }

                if (blockData != null) {
                    if (this.rotationMode.getModeIndex() == 4) {
                        double centerX = blockData.blockPos().getX() + 0.5
                                + blockData.facing().getDirectionVec().getX() * 0.5;
                        double centerY = blockData.blockPos().getY() + 0.5
                                + blockData.facing().getDirectionVec().getY() * 0.5;
                        double centerZ = blockData.blockPos().getZ() + 0.5
                                + blockData.facing().getDirectionVec().getZ() * 0.5;
                        float[] strictRot = RotationUtil.getRotations(centerX, centerY, centerZ);
                        MovingObjectPosition strictMop = RotationUtil.rayTrace(strictRot[0], strictRot[1],
                                mc.playerController.getBlockReachDistance(), 1.0F);
                        if (this.isValidHit(strictMop, blockData.blockPos(), blockData.facing())) {
                            this.yaw = RotationUtil.wrapAngleDiff(strictRot[0], event.getYaw());
                            this.pitch = strictRot[1];
                            this.canRotate = true;
                            hitVec = strictMop.hitVec;
                        }
                    } else if (this.rotationMode.getModeIndex() == 5) {
                        float diagYaw = this.quantizeDiagonal(this.getCurrentYaw() + 180.0F);
                        float tolerance = this.godBridgeTolerance.getValue();
                        double centerX = blockData.blockPos().getX() + 0.5
                                + blockData.facing().getDirectionVec().getX() * 0.5;
                        double centerY = blockData.blockPos().getY() + 0.5
                                + blockData.facing().getDirectionVec().getY() * 0.5;
                        double centerZ = blockData.blockPos().getZ() + 0.5
                                + blockData.facing().getDirectionVec().getZ() * 0.5;
                        float[] centerRot = RotationUtil.getRotations(centerX, centerY, centerZ);
                        float centerPitch = Math.max(-89.0F, Math.min(89.0F, centerRot[1]));

                        float lastOff = MathHelper.wrapAngleTo180_float(this.yaw - diagYaw);
                        if (Math.abs(lastOff) > tolerance) {
                            lastOff = (float) ((Math.random() * 2.0D - 1.0D) * tolerance * 0.8D);
                        }
                        float realOff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - diagYaw);
                        ArrayList<Float> yawCandidates = new ArrayList<>();
                        if (Math.abs(realOff) <= tolerance) {
                            yawCandidates.add(diagYaw + realOff);
                        }
                        yawCandidates.add(diagYaw + lastOff);
                        for (float step = 1.0F; step <= tolerance * 2.0F; step += 1.0F) {
                            float up = lastOff + step;
                            float down = lastOff - step;
                            if (Math.abs(up) <= tolerance) {
                                yawCandidates.add(diagYaw + up);
                            }
                            if (Math.abs(down) <= tolerance) {
                                yawCandidates.add(diagYaw + down);
                            }
                        }

                        float bestPitch = Float.NaN;
                        float bestYaw = diagYaw;
                        Vec3 bestHitVec = null;
                        for (float candidateYaw : yawCandidates) {
                            double bestScore = Double.MAX_VALUE;
                            for (float p = centerPitch + 30.0F; p >= centerPitch - 30.0F; p -= 0.5F) {
                                if (p > 89.0F || p < -89.0F) {
                                    continue;
                                }
                                MovingObjectPosition mop = RotationUtil.rayTrace(candidateYaw, p,
                                        mc.playerController.getBlockReachDistance(), 1.0F);
                                if (this.isValidHit(mop, blockData.blockPos(), blockData.facing())) {
                                    double score = Math.abs(p - centerPitch);
                                    if (score < bestScore) {
                                        bestScore = score;
                                        bestPitch = p;
                                        bestHitVec = mop.hitVec;
                                        bestYaw = candidateYaw;
                                    }
                                }
                            }
                            if (bestHitVec != null) {
                                break;
                            }
                        }
                        if (bestHitVec != null) {
                            hitVec = bestHitVec;
                            this.canRotate = true;
                            boolean updateRotation = true;
                            if (this.noUpdateWhenCanPlace.getValue()) {
                                MovingObjectPosition curMop = RotationUtil.rayTrace(mc.thePlayer.rotationYaw,
                                        mc.thePlayer.rotationPitch, mc.playerController.getBlockReachDistance(), 1.0F);
                                if (this.isValidHit(curMop, blockData.blockPos(), blockData.facing())) {
                                    updateRotation = false;
                                }
                            }
                            if (this.edgeLimit.getValue() && !this.isGodBridgeOnEdge()) {
                                updateRotation = false;
                            }
                            if (updateRotation) {
                                this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
                                this.pitch = bestPitch;
                            } else {
                                this.yaw = mc.thePlayer.rotationYaw;
                                this.pitch = mc.thePlayer.rotationPitch;
                            }
                        } else if (!mc.thePlayer.onGround && this.airRescue.getValue()) {
                            hitVec = this.applyRescueRotation(blockData, event);
                        }
                    } else if (this.rotationMode.getModeIndex() == 3) {
                        double[] offsets = {0.1, 0.3, 0.5, 0.7, 0.9};
                        double[] x = offsets, y = offsets, z = offsets;
                        switch (blockData.facing()) {
                            case NORTH:
                                z = new double[]{0.02};
                                break;
                            case EAST:
                                x = new double[]{0.98};
                                break;
                            case SOUTH:
                                z = new double[]{0.98};
                                break;
                            case WEST:
                                x = new double[]{0.02};
                                break;
                            case DOWN:
                                y = new double[]{0.02};
                                break;
                            case UP:
                            default:
                                y = new double[]{0.98};
                                break;
                        }
                        float bestYaw = -180.0F, bestPitch = 0.0F;
                        double bestDist = Double.MAX_VALUE;
                        Vec3 bestHitVec = null;
                        // TASK(Grim-RotationPlace):两趟搜索。第一趟只允许"离方块面四条边都 >= 0.25"的点
                        // (0.3 / 0.5 / 0.7),第二趟才放开到贴边的 0.1 / 0.9。
                        //
                        // 原因:Grim 的 RotationPlace 还有一条 onPostFlyingBlockPlace(verbose 里的
                        // post-flying)分支,它用的位置是"这一 tick 移动之后"的位置 —— 比本 tick 算射线
                        // 用的位置晚一 tick。冲刺跳跃搭路时水平方向能差 0.3~0.6 格,而这个位移会原样传到
                        // 命中点上(射线与水平面相截,交点水平位移 = 眼睛水平位移)。瞄 0.1 / 0.9 这种贴边
                        // 点,位置一挪射线就出格,每放一块白送一次 flag;瞄内侧的点则留出接近半格的余量。
                        // 第一趟一个都没命中时仍会退回原来那 25 个候选,所以不会因为这条而放不出方块。
                        double[] xInner = innerFaceOffsets(x), yInner = innerFaceOffsets(y), zInner = innerFaceOffsets(z);
                        for (int pass = 0; pass < 2 && bestHitVec == null; pass++) {
                            double[] xs = pass == 0 ? xInner : x;
                            double[] ys = pass == 0 ? yInner : y;
                            double[] zs = pass == 0 ? zInner : z;
                            for (double dx : xs) {
                                for (double dy : ys) {
                                    for (double dz : zs) {
                                        double targetX = blockData.blockPos().getX() + dx;
                                        double targetY = blockData.blockPos().getY() + dy;
                                        double targetZ = blockData.blockPos().getZ() + dz;
                                        float[] rot = RotationUtil.getRotations(targetX, targetY, targetZ);
                                        MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1],
                                                mc.playerController.getBlockReachDistance(), 1.0F);
                                        if (this.isValidHit(mop, blockData.blockPos(), blockData.facing())) {
                                            float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                            float pitchDiff = Math.abs(rot[1] - this.pitch);
                                            double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                            if (dist < bestDist) {
                                                bestDist = dist;
                                                bestYaw = rot[0];
                                                bestPitch = rot[1];
                                                bestHitVec = mop.hitVec;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            bestYaw += RandomUtils.nextFloat(-0.5F, 0.5F);
                            bestPitch += RandomUtils.nextFloat(-0.3F, 0.3F);
                            this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
                            this.pitch = bestPitch;
                            this.canRotate = true;
                            hitVec = bestHitVec;
                        }
                    } else {
                        double[] x = placeOffsets, y = placeOffsets, z = placeOffsets;
                        switch (blockData.facing()) {
                            case NORTH:
                                z = new double[]{0.0};
                                break;
                            case EAST:
                                x = new double[]{1.0};
                                break;
                            case SOUTH:
                                z = new double[]{1.0};
                                break;
                            case WEST:
                                x = new double[]{0.0};
                                break;
                            case DOWN:
                                y = new double[]{0.0};
                                break;
                            case UP:
                            default:
                                y = new double[]{1.0};
                                break;
                        }
                        float bestYaw = -180.0F, bestPitch = 0.0F, bestDiff = 0.0F;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                    double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY
                                            - (double) mc.thePlayer.getEyeHeight();
                                    double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                    float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1],
                                            mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (this.isValidHit(mop, blockData.blockPos(), blockData.facing())) {
                                        float totalDiff = Math.abs(rotations[0] - baseYaw)
                                                + Math.abs(rotations[1] - this.pitch);
                                        if (bestYaw == -180.0F || totalDiff < bestDiff) {
                                            bestYaw = rotations[0];
                                            bestPitch = rotations[1];
                                            bestDiff = totalDiff;
                                            hitVec = mop.hitVec;
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            this.yaw = bestYaw;
                            this.pitch = bestPitch;
                            this.canRotate = true;
                        }
                    }
                }

                if (blockData != null && hitVec == null && !mc.thePlayer.onGround && this.airRescue.getValue()) {
                    hitVec = this.applyRescueRotation(blockData, event);
                }

                if (this.canRotate && MoveUtil.isForwardPressed()
                        && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    if (this.rotationMode.getModeIndex() == 2) {
                        this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    }
                }

                if (!legitMode && this.rotationMode.getModeIndex() != 0 && this.mode.getModeIndex() != 2) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    if (!tellyMode) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                        float tolerance = this.normalModeSpeed.getValue();
                        if (Math.abs(yawDiff) > tolerance) {
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                                    + RotationUtil.clampAngle(yawDiff, tolerance));
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    } else {
                        if (this.speedLimit.getValue() && this.forwardRotateTicksLeft > 0) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                            this.yaw = RotationUtil.quantizeAngle(event.getYaw()
                                    + yawDelta * RandomUtils.nextFloat(0.98F, 0.99F));
                            this.pitch = RotationUtil.quantizeAngle(
                                    RandomUtils.nextFloat(30.0F, 80.0F));
                            this.rotationTick = 0;
                        } else if (this.canTower && (mc.thePlayer.motionY > 0.0
                                || mc.thePlayer.posY > (double) (this.startY + 1))) {
                            float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                            float tolerance = this.rotationTick >= 2
                                    ? this.startRotSpeed.getValue() : this.normalRotSpeed.getValue();
                            if (Math.abs(yawDiff) > tolerance) {
                                float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                                targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                                this.rotationTick = Math.max(this.rotationTick, 1);
                            }
                        }
                        if (this.isTowering() && this.tellyJumpDelayTimer <= 0 && this.forwardRotateTicksLeft <= 0) {
                            if (!this.speedLimit.getValue()) {
                                float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                                targetYaw = RotationUtil.quantizeAngle(event.getYaw()
                                        + yawDelta * RandomUtils.nextFloat(0.98F, 0.99F));
                                targetPitch = RotationUtil.quantizeAngle(
                                        RandomUtils.nextFloat(30.0F, 80.0F));
                                this.rotationTick = 3;
                                this.canTower = true;
                            } else {
                                this.pendingSpeedLimitRot = true;
                                this.airTicks = 0;
                            }
                        } else if (this.tellyJumpDelayTimer > 0) {
                            targetYaw = this.yaw != -180.0F ? this.yaw
                                    : RotationUtil.quantizeAngle(
                                    MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw())
                                            + event.getYaw());
                            targetPitch = Math.abs(this.pitch) > 10 ? this.pitch : 60.0F;
                        }
                        if (this.speedLimit.getValue() && this.pendingSpeedLimitRot && !mc.thePlayer.onGround
                                && this.airTicks >= this.speedLimitTicks.getValue()) {
                            float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                            this.yaw = RotationUtil.quantizeAngle(event.getYaw()
                                    + yawDelta * RandomUtils.nextFloat(0.98F, 0.99F));
                            this.pitch = RotationUtil.quantizeAngle(
                                    RandomUtils.nextFloat(30.0F, 80.0F));
                            this.forwardRotateTicksLeft = this.forwardRotationTicks.getValue();
                            this.rotationTick = 0;
                            this.canTower = true;
                            this.pendingSpeedLimitRot = false;
                            this.airTicks = 0;
                        }
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getModeIndex() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                } else if (this.mode.getModeIndex() == 2 && this.rotationMode.getModeIndex() != 0 && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = this.snapForward ? this.snapForwardSpeed.getValue() : this.snapBackSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) {
                        float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                        this.rotationTick = Math.max(this.rotationTick, 1);
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getModeIndex() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                } else if (legitMode && this.rotationMode.getModeIndex() != 0 && this.canRotate) {
                    float targetYaw = this.yaw, targetPitch = this.pitch;
                    float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                    float tolerance = this.legitModeSpeed.getValue();
                    if (Math.abs(yawDiff) > tolerance) {
                        float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                        this.rotationTick = Math.max(this.rotationTick, 1);
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getModeIndex() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }

                boolean legitCanPlace = !legitMode || !mc.thePlayer.onGround
                        || this.legitEdgeState == 0 || this.legitEdgeState == 2;

                if (blockData != null && hitVec != null && this.rotationTick <= 0 && legitCanPlace) {
                    if (this.placeDelayCounter > 0) {
                        this.placeDelayCounter--;
                    } else {
                        MovingObjectPosition finalCheck = RotationUtil.rayTrace(this.yaw, this.pitch,
                                mc.playerController.getBlockReachDistance(), 1.0F);
                        if (this.isValidHit(finalCheck, blockData.blockPos(), blockData.facing())) {
                            this.place(blockData.blockPos(), blockData.facing(), finalCheck.hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        } else if (this.canRotate) {
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                            this.placeDelayCounter = this.placeDelay.getValue();
                        }
                    }
                }

                if (this.targetFacing != null && this.rotationTick <= 0) {
                    int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                    int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                    int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                    BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                    hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                    this.place(belowPlayer, this.targetFacing, hitVec);
                    this.targetFacing = null;
                }
            }

            if (event.hasPervRotation()) {
                this.moveYaw = event.getPervYaw();
            }
        }
    }

    /**
     * LeaderLite's {@code onStrafe}: while the clutch holds, the player's inputs are frozen on every
     * tick except the tenth. LeaderLite read its local strafe/forward variables here; this project's
     * event carries the same two values, and {@code MixinEntity.moveFlying} writes them back into the
     * movement, so the two are equivalent.
     */
    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.getState() && this.clutchActive && this.clutchTickCounter % 10 != 0) {
            event.setStrafe(0.0F);
            event.setForward(0.0F);
        }
    }

    /**
     * LeaderLite's {@code onMoveInput}, driven from this project's {@link TickEvent} because this
     * client fires no move-input event. The body is unchanged.
     */
    @EventTarget
    public void onTick(TickEvent event) {
        this.handleMoveInput();
    }

    private void handleMoveInput() {
        if (!this.getState() || mc.thePlayer == null) {
            return;
        }
        if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
            return;
        }
        if (this.moveFix.getModeIndex() == 1 && !Float.isNaN(this.moveYaw) && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(this.moveYaw);
        }
        if (this.mode.getModeIndex() == 1 && mc.thePlayer.onGround
                && this.stage > 0 && MoveUtil.isForwardPressed() && this.tellyJumpDelayTimer <= 0) {
            mc.thePlayer.movementInput.jump = true;
        }
        if (this.isLegitTellyMode() && mc.thePlayer.onGround && MoveUtil.isForwardPressed()) {
            mc.thePlayer.movementInput.jump = true;
        }
        if (this.mode.getModeIndex() == 3 && mc.currentScreen == null && !this.clutchActive) {
            if (mc.thePlayer.onGround && (this.legitEdgeState == 1 || this.legitEdgeState == 2)) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            }
        }
    }

    /**
     * LeaderLite's {@code onLivingUpdate}: the per-tick speed sample and the clutch's motion freeze.
     * Subscribed on the {@code POST} half of {@link MotionEvent}, which is this project's
     * once-per-tick point after the movement packet.
     */
    @EventTarget
    public void onLivingUpdate(MotionEvent motionEvent) {
        if (motionEvent.getEventState() != EventState.POST || !this.getState() || mc.thePlayer == null) {
            return;
        }
        double dx = mc.thePlayer.posX - this.prevBpsX;
        double dz = mc.thePlayer.posZ - this.prevBpsZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        this.currentBps = (float) (dist * 20.0);
        this.prevBpsX = mc.thePlayer.posX;
        this.prevBpsZ = mc.thePlayer.posZ;
        if (this.clutchActive && this.clutchTickCounter % 10 != 0) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
            return;
        }
        if (this.shouldStopSprint()) {
            mc.thePlayer.setSprinting(false);
        }
        this.updateCounters();
    }

    private boolean shouldStopSprint() {
        return !this.isTowering() && this.stage <= 0 && this.mode.getModeIndex() != 2;
    }

    /**
     * LeaderLite's {@code onRender} computed its counters at frame time; this client's
     * {@code DynamicIsland} reads {@link #getBlocksAmount()} / {@link #getBlocksPerSecond()} every
     * frame instead, so the smoothed values are maintained here once per tick.
     */
    private void updateCounters() {
        int hotbarCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                if (ItemUtil.isBlock(stack)) {
                    hotbarCount += stack.stackSize;
                }
            }
        }
        Scaffold.count = hotbarCount;
        if (Scaffold.count > this.counterMax) {
            this.counterMax = Scaffold.count;
        }
        if (Scaffold.count <= 0) {
            this.counterMax = 0;
        }

        long now = System.currentTimeMillis();
        float dt = this.lastHudFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastHudFrame) / 1000.0F);
        this.lastHudFrame = now;
        float k = 1.0F - (float) Math.exp(-dt * 12.0F);
        float percent = this.counterMax > 0 ? Scaffold.count / (float) this.counterMax : 0.0F;
        this.animBps += (this.currentBps - this.animBps) * k;
        this.animPercent += (percent - this.animPercent) * k;
    }

    /**
     * LeaderLite's render HUD is not ported (see the class note). This handler only keeps the loop
     * that used to call it alive for the {@code Render BPS} / {@code Block Counter} settings, so
     * enabling them still tracks the counters that {@code DynamicIsland} shows.
     */
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.getState() || mc.thePlayer == null) {
            return;
        }
        if (this.blockCounter.getValue() || this.bPSRender.getValue()) {
            this.updateCounters();
        }
    }

    /**
     * LeaderLite cancelled its own same-slot swap through a {@code SwapItemEvent} this client does
     * not have, and cancelled left/right clicks through mouse events it also does not have. Those
     * handlers are therefore absent rather than re-expressed on packets (cancelling
     * {@code C0APacketAnimation} would cancel this module's own swing when {@code Swing} is off).
     * See the report for the two places this changes behaviour.
     */

    @EventTarget
    public void onKey(net.aspw.client.event.KeyEvent event) {
        this.handleKey(event.getKey());
    }

    private void handleKey(int key) {
        if (!this.getState() || !this.ctrlToSwitchTelly.getValue()) {
            return;
        }
        if (key != Keyboard.KEY_LCONTROL && key != Keyboard.KEY_RCONTROL) {
            return;
        }
        if (!this.ctrlTellyActive) {
            this.ctrlSavedMode = this.mode.getModeIndex();
            this.ctrlSavedRotationMode = this.rotationMode.getModeIndex();
            this.mode.setValue(1);
            this.rotationMode.setValue(4);
            this.ctrlTellyActive = true;
            this.chat("Scaffold: Telly / Strict");
        } else {
            this.restoreCtrlTelly();
            this.chat("Scaffold: " + this.mode.getModeString() + " / " + this.rotationMode.getModeString());
        }
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.stage = 0;
        this.rotationTick = 1;
    }

    private void restoreCtrlTelly() {
        if (!this.ctrlTellyActive) {
            return;
        }
        if (this.ctrlSavedMode >= 0) {
            this.mode.setValue(this.ctrlSavedMode);
        }
        if (this.ctrlSavedRotationMode >= 0) {
            this.rotationMode.setValue(this.ctrlSavedRotationMode);
        }
        this.ctrlTellyActive = false;
        this.ctrlSavedMode = -1;
        this.ctrlSavedRotationMode = -1;
    }

    /**
     * The marker the old Kotlin module drew when {@code Interface}'s {@code ScaffoldHighlight} was
     * on. LeaderLite's Scaffold had no equivalent, so this keeps the setting meaningful instead of
     * silently dropping it.
     */
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.getState() || mc.thePlayer == null) {
            return;
        }
        Interface visualInterface = Launch.moduleManager.getModule(Interface.class);
        if (visualInterface != null && visualInterface.getState() && visualInterface.getScaffoldHighlight().get()) {
            RenderUtils.drawBlockBox(
                    new BlockPos(mc.thePlayer.posX, this.startY - 1, mc.thePlayer.posZ),
                    new java.awt.Color(255, 255, 255, 40),
                    false);
        }
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        this.clutchReset();
        this.resetLegitTellyCycle();
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer == null) {
            return;
        }
        this.lastSlot = mc.thePlayer.inventory.currentItem;
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.canTower = false;
        this.placeDelayCounter = 0;
        this.prevBpsX = mc.thePlayer.posX;
        this.prevBpsZ = mc.thePlayer.posZ;
        this.currentBps = 0.0F;
        this.animBps = 0.0F;
        this.animPercent = 0.0F;
        this.lastHudFrame = 0L;
        this.snapForward = true;
        this.snapForwardTimer = 0;
        this.snapLocked = false;
        this.airTicks = 0;
        this.pendingSpeedLimitRot = false;
        this.forwardRotateTicksLeft = 0;
        this.legitEdgeState = 0;
        this.legitEdgeTimer = 0;
        this.legitWasOnEdge = false;
        this.legitTellyPhase = 0;
        this.legitTellyPhaseTicks = 0;
        this.legitTellyWasAirborne = false;
        this.legitTellyPlacedFirstBlock = false;
        this.legitTellyLockedBlockData = null;
        this.legitTellySilentYaw = mc.thePlayer.rotationYaw;
        this.legitTellySilentPitch = mc.thePlayer.rotationPitch;
        this.moveYaw = Float.NaN;
        this.stage = 0;
        this.startY = MathHelper.floor_double(mc.thePlayer.posY);
        this.placementTimes.clear();
        this.counterMax = 0;
        this.clutchReset();
    }

    @Override
    public void onDisable() {
        this.clutchReset();
        this.restoreCtrlTelly();
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // LeaderLite's nested BlockData, unchanged
    // ---------------------------------------------------------------------------------------------

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}
