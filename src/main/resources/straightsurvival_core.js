function initializeCoreMod() {
// BEGIN

Java.type("net.minecraftforge.coremod.api.ASMAPI").loadFile("easycorelib.js");

easycore.include("me");

var String = java.lang.String;
var List = java.util.List;
var Long = java.lang.Long;
var Mod = me.paulf.straightline.StraightLineChallenge;
var Entity = net.minecraft.entity.Entity;
var LivingEntity = net.minecraft.entity.LivingEntity;
var Vec3d = net.minecraft.util.math.Vec3d;
var MoverType = net.minecraft.entity.MoverType;
var MathHelper = net.minecraft.util.math.MathHelper;
var DimensionType = net.minecraft.world.dimension.DimensionType;
var BlockPos = net.minecraft.util.math.BlockPos;
var ServerWorld = net.minecraft.world.server.ServerWorld;
var World = net.minecraft.world.World;
var AxisAlignedBB = net.minecraft.util.math.AxisAlignedBB;
var Teleporter = net.minecraft.world.Teleporter;
var ServerPlayerEntity = net.minecraft.entity.player.ServerPlayerEntity;
var WorldSettings = net.minecraft.world.WorldSettings;
var EnderEyeItem = net.minecraft.item.EnderEyeItem;
var ActionResult = net.minecraft.util.ActionResult;
var Hand = net.minecraft.util.Hand;
var ChunkGenerator = net.minecraft.world.gen.ChunkGenerator;
var EndSpikeCacheLoader = net.minecraft.world.gen.feature.EndSpikeFeature$EndSpikeCacheLoader;
var PlayerEntity = net.minecraft.entity.player.PlayerEntity;
var Pose = net.minecraft.entity.Pose;
var BlockState = net.minecraft.block.BlockState;
var IWorldReader = net.minecraft.world.IWorldReader;
var EnderPearlEntity = net.minecraft.entity.item.EnderPearlEntity;
var ItemStack = net.minecraft.item.ItemStack;

// Prevent movement along z axis
easycore.inMethod(Entity.func_213306_e(Vec3d))
    .atEach(areturn).prepend(
        aload(0),
        invokestatic(Mod.getAllowedMovement(Vec3d, Entity), Vec3d)
    );

// Move vehicle to player
easycore.inMethod(Entity.func_184205_a(Entity, boolean))
    .atFirst(putfield(Entity.field_184239_as)).prepend(
        aload(0),
        aload(1),
        iload(2),
        invokestatic(Mod.startRiding(Entity, Entity, boolean))
    );

// Omit z-axis collision (allows sprinting, stops unintentional water hopping)
easycore.inMethod(Entity.func_213315_a(MoverType, Vec3d))
    .atLast(invokestatic(MathHelper.func_219806_b(double, double))).append(
        aload(0),
        invokestatic(Mod.isNotCollidingHorizontally(boolean, Entity), boolean)
    );

// Dimension positioning
easycore.inMethod(Entity.func_212321_a(DimensionType))
    .atFirst(invokespecial(BlockPos._init_(double, double, double))).append(
        aload(4),
        aload(5),
        invokestatic(Mod.changeDimension(BlockPos, ServerWorld, ServerWorld), BlockPos)
    );

// Dismount at position of vehicle
easycore.inMethod(LivingEntity.func_110145_l(Entity))
    .atEach(invokevirtual(World.func_195586_b(Entity, AxisAlignedBB))).append(
        iconst_0,
        iand
    );

easycore.inMethod(Teleporter.func_85188_a(Entity))
    // Force portal to face x
    .atEach(istore(11)).prepend(pop, iconst_0)
    // Don't move z-axis
    .atEach(bipush(16)).after(iload(7)).replace(bipush(0));

// Maintain z position inside portal
easycore.inMethod(Teleporter.func_222268_a(Entity, float))
    .atEach(getfield(Vec3d.field_72449_c)).append(
        aload(1),
        aload(0),
        getfield(Teleporter.field_85192_a, ServerWorld),
        invokestatic(Mod.getPortalZ(double, Entity, World), double)
    );

// Place player at spawn location along x-axis
easycore.inMethod(ServerPlayerEntity.func_205734_a(ServerWorld))
    .atFirst().prepend(
        aload(0),
        aload(1),
        invokestatic(Mod.makeSpawnLocation(ServerPlayerEntity, ServerWorld), boolean),
        ifeq(L0 = label()),
        _return,
        L0
    );

easycore.inMethod(ServerPlayerEntity.func_212321_a(DimensionType))
    // Center player in block when going to end
    .atFirst(i2d).after(iload(28)).append(
        ldc(double(0.5)),
        dadd
    )
    .atFirst(i2d).after(iload(30)).append(
        ldc(double(0.5)),
        dadd
    )
    // Dimension positioning
    .atFirst(invokevirtual(Teleporter.func_222268_a(Entity, float))).prepend(
        aload(0),
        aload(3),
        aload(4),
        invokestatic(Mod.changeDimension(ServerPlayerEntity, ServerWorld, ServerWorld))
    );

// Create world spawn aligned to stronghold
easycore.inMethod(ServerWorld.func_73052_b(WorldSettings))
    .atFirst().prepend(
        aload(0),
        aload(1),
        invokestatic(Mod.createSpawnLocation(ServerWorld, WorldSettings), boolean),
        ifeq(L0 = label()),
        _return,
        L0
    );

easycore.inMethod(EnderEyeItem.func_77659_a(World, PlayerEntity, Hand))
    .atFirst(invokevirtual(ChunkGenerator.func_211403_a(World, String, BlockPos, int, boolean))).append(
        aload(1),
        invokestatic(Mod.getEyeOfEnderTarget(BlockPos, World), BlockPos)
    );

var PI = 3.14159265358979323846;

easycore.inMethod(EndSpikeCacheLoader.load(Long))
    .atEach(ldc(double(-PI))).replace(ldc(double(-PI / 20.0)))

// Only allow entering beds on x-axis
easycore.inMethod(PlayerEntity.func_190774_a(BlockPos, net.minecraft.util.Direction))
    .atFirst().prepend(
        aload(0),
        aload(1),
        invokestatic(Mod.canUseBed(PlayerEntity, BlockPos), boolean),
        ifne(L0 = label()),
        iconst_0,
        ireturn,
        L0
    );

var getBedSpawnPosition = invokevirtual(BlockState.getBedSpawnPosition(net.minecraft.entity.EntityType, IWorldReader, BlockPos, LivingEntity));

// Place player on line when waking up
easycore.inMethod(LivingEntity.lambda$wakeUp$6(BlockPos))
    .atFirst(getBedSpawnPosition).append(
        aload(0),
        invokestatic(Mod.getBedSpawnPosition(java.util.Optional, Entity), java.util.Optional)
    );

// Leave/join server while in bed
easycore.inMethod(PlayerEntity.func_213822_a(IWorldReader, BlockPos, boolean))
    .atLast(getBedSpawnPosition).append(
        invokestatic(Mod.getBedSpawnPosition(java.util.Optional), java.util.Optional)
    );

// Fix wake up position not centered
easycore.inMethod(LivingEntity.func_213366_dy())
    .atFirst().prepend(
        aload(0),
        getstatic(Pose.STANDING, Pose),
        invokevirtual(LivingEntity.func_213301_b(Pose))
    );

// Don't move ender pearl along x-axis
easycore.inMethod(EnderPearlEntity.func_70071_h_())
    .atFirst().prepend(
        aload(0),
        invokestatic(Mod.tickEnderPearl(EnderPearlEntity))
    );

// Don't teleport along z-axis
easycore.inMethod(net.minecraft.item.ChorusFruitItem.func_77654_b(ItemStack, World, LivingEntity))
    .atLast(ldc(double(16.0))).replace(dconst_0);

return easycore.build();

// END
}