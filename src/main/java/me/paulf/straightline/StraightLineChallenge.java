package me.paulf.straightline;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EnderPearlEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.structure.FortressPieces;
import net.minecraft.world.gen.feature.structure.StrongholdPieces;
import net.minecraft.world.gen.feature.structure.StrongholdStructure;
import net.minecraft.world.gen.feature.structure.StructurePiece;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.structure.Structures;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;

@Mod("straightline")
public final class StraightLineChallenge {
    private static final Logger LOGGER = LogManager.getLogger();

    public StraightLineChallenge() {
    }

    public static Vec3d getAllowedMovement(final Vec3d vec, final Entity entity) {
        if (entity instanceof PlayerEntity || entity.getControllingPassenger() instanceof PlayerEntity || entity instanceof EnderPearlEntity) {
            return new Vec3d(vec.x, vec.y, 0.0D);
        }
        return vec;
    }

    public static void startRiding(final Entity entity, final Entity other) {
        if (entity instanceof PlayerEntity && other.isAddedToWorld()) {
            other.setPositionAndRotation(other.posX, other.posY, entity.posZ, other.rotationYaw, other.rotationPitch);
        }
    }

    public static boolean isNotCollidingHorizontally(final boolean result, final Entity entity) {
        return result || entity instanceof PlayerEntity || entity instanceof EnderPearlEntity;
    }

    public static boolean makeSpawnLocation(final ServerPlayerEntity player, final ServerWorld world) {
        if (world.dimension.getType() != DimensionType.OVERWORLD) {
            return false;
        }
        final int x = world.getSpawnPoint().getX();
        final int z = world.getSpawnPoint().getZ();
        final int borderDistance = MathHelper.floor(world.getWorldBorder().getClosestDistance(x, z));
        final int radius = borderDistance <= 1 ? 1 : Math.min(borderDistance, Math.max(0, player.server.getSpawnRadius(world)));
        final int diameter = radius * 2 + 1;
        final int coprime = diameter <= 16 ? diameter - 1 : 17;
        final int offset = new Random().nextInt(diameter);
        for (int n = 0; n < diameter; n++) {
            final int dx = (offset + coprime * n) % diameter;
            final BlockPos candidate = world.getDimension().findSpawn(x + dx - radius, z, false);
            if (candidate == null) {
                continue;
            }
            player.moveToBlockPosAndAngles(candidate, -90.0F, 0.0F);
            if (world.areCollisionShapesEmpty(player)) {
                break;
            }
        }
        return true;
    }

    private static final Method CREATE_BONUS_CHEST = ObfuscationReflectionHelper.findMethod(ServerWorld.class, "func_73047_i");

    public static boolean createSpawnLocation(final ServerWorld world, final WorldSettings settings) {
        if (world.dimension.getType() != DimensionType.OVERWORLD) {
            return false;
        }
        final BlockPos stronghold = locateStronghold(world);
        final ChunkGenerator<?> generator = world.getChunkProvider().getChunkGenerator();
        final BiomeProvider provider = generator.getBiomeProvider();
        final int z = stronghold.getZ();
        final BlockPos blockpos = provider.findBiomePosition(0, z, 256, provider.getBiomesToSpawnIn(), new Random(world.getSeed()));
        final boolean checkSurface = BlockTags.VALID_SPAWN.getAllElements().stream()
            .map(Block::getDefaultState)
            .anyMatch(provider.getSurfaceBlocks()::contains);
        final int x = blockpos == null ? 0 : new ChunkPos(blockpos).getXStart();
        world.getWorldInfo().setSpawn(new BlockPos(x + 8, generator.getGroundHeight(), z));
        final int diameter = 2 * (16 << 4);
        for (int n = 0; n < 2 * diameter; n++) {
            final @Nullable BlockPos pos = world.dimension.findSpawn(x + n / 2 * (n % 2 * 2 - 1), z, checkSurface);
            if (pos != null) {
                world.getWorldInfo().setSpawn(pos);
                break;
            }
        }
        if (settings.isBonusChestEnabled()) {
            try {
                CREATE_BONUS_CHEST.invoke(world);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    public static BlockPos changeDimension(final BlockPos pos, final ServerWorld origin, final ServerWorld destination) {
        if (origin.dimension.getType() == DimensionType.OVERWORLD && destination.dimension.getType() == DimensionType.THE_NETHER) {
            return new BlockPos(pos.getX(), pos.getY(), locateFortress(destination, pos).getZ());
        }
        if (origin.dimension.getType() == DimensionType.THE_NETHER && destination.dimension.getType() == DimensionType.OVERWORLD) {
            return new BlockPos(pos.getX(), pos.getY(), locateStronghold(destination).getZ());
        }
        return pos;
    }

    public static void changeDimension(final ServerPlayerEntity player, final ServerWorld origin, final ServerWorld destination) {
        if (origin.dimension.getType() == DimensionType.OVERWORLD && destination.dimension.getType() == DimensionType.THE_NETHER) {
            final BlockPos p = locateFortress(destination, new BlockPos(player));
            player.setLocationAndAngles(player.posX, player.posY, p.getZ() + 0.5D, player.rotationYaw, player.rotationPitch);
        }
        if (origin.dimension.getType() == DimensionType.THE_NETHER && destination.dimension.getType() == DimensionType.OVERWORLD) {
            final BlockPos p = locateStronghold(destination);
            player.setLocationAndAngles(player.posX, player.posY, p.getZ() + 0.5D, player.rotationYaw, player.rotationPitch);
        }
    }

    public static double getPortalZ(final double z, final Entity entity, final World world) {
        if (entity instanceof PlayerEntity && (world.dimension.getType() == DimensionType.THE_NETHER || world.dimension.getType() == DimensionType.OVERWORLD)) {
            return entity.posZ;
        }
        return z;
    }

    public static boolean canUseBed(final PlayerEntity player, final BlockPos pos) {
        return MathHelper.floor(player.posZ) == pos.getZ();
    }

    public static Optional<Vec3d> getBedSpawnPosition(final Optional<Vec3d> result, final Entity entity) {
        return entity instanceof PlayerEntity ? Optional.empty() : result;
    }

    public static Optional<Vec3d> getBedSpawnPosition(final Optional<Vec3d> result, final BlockPos pos) {
        final BlockPos up = pos.up();
        return Optional.of(new Vec3d(up.getX() + 0.5D, up.getY() + 0.1D, up.getZ() + 0.5D));
    }

    public static BlockPos getEyeOfEnderTarget(final @Nullable BlockPos result, final World world) {
        return locateStronghold(world);
    }

    public static BlockPos locateStronghold(final World world) {
        if (Structures.STRONGHOLD.findNearest(world, world.getChunkProvider().getChunkGenerator(), BlockPos.ZERO, 100, false) == null) {
            throw new RuntimeException("Unable to locate a Stronghold as they do not appear to generate in the Overworld");
        }
        final ChunkPos[] strongholds = ObfuscationReflectionHelper.getPrivateValue(StrongholdStructure.class, (StrongholdStructure) Structures.STRONGHOLD, "field_75057_g");
        if (strongholds == null) {
            throw new RuntimeException("Stronghold cache has not been initialized as expected");
        }
        final ChunkPos chosen = Arrays.stream(strongholds)
            .sorted(Comparator.comparing(p -> p.getXStart() * p.getXStart() + p.getZStart() * p.getZStart()))
            .limit(3)
            .sorted(Comparator.comparing(p -> MathHelper.abs(p.getXStart())))
            .skip(1)
            .findFirst().orElseThrow(NoSuchElementException::new);
        final IChunk chunk = world.getChunk(chosen.x, chosen.z, ChunkStatus.STRUCTURE_STARTS);
        final StructureStart start = chunk.getStructureStart(Structures.STRONGHOLD.getStructureName());
        if (start == null) {
            throw new RuntimeException(String.format("Expected Stronghold start at (%s, %s) but none was found", chosen.x, chosen.z));
        }
        for (final StructurePiece piece : start.getComponents()) {
            if (piece instanceof StrongholdPieces.PortalRoom) {
                return transform(piece, 5, 3, 10);
            }
        }
        throw new RuntimeException("Stronghold without a portal room");
    }

    public static BlockPos locateFortress(final World world, final BlockPos origin) {
        if (!world.getChunkProvider().getChunkGenerator().getBiomeProvider().hasStructure(Structures.FORTRESS)) {
            throw new RuntimeException("Unable to locate a Nether Fortress as they do not appear to generate in the Nether");
        }
        final ChunkPos oc = new ChunkPos(origin);
        final int ox = oc.x >> 4;
        final int oz = oc.z >> 4;
        final SharedSeedRandom rng = new SharedSeedRandom();
        double shortestDistance = Double.POSITIVE_INFINITY;
        BlockPos closest = BlockPos.ZERO;
        final int range = 5;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                final int x = ox + dx;
                final int z = oz + dz;
                // FortressStructure#hasStartAt
                rng.setSeed((long) (x ^ z << 4) ^ world.getSeed());
                rng.nextInt();
                if (rng.nextInt(3) != 0) {
                    continue;
                }
                final int cx = (x << 4) + 4 + rng.nextInt(8);
                final int cz = (z << 4) + 4 + rng.nextInt(8);
                final IChunk chunk = world.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
                final StructureStart start = chunk.getStructureStart(Structures.FORTRESS.getStructureName());
                if (start == null) {
                    LOGGER.warn("Expected Fortress start at ({}, {}) but none was found", cx, cz);
                    continue;
                }
                for (final StructurePiece piece : start.getComponents()) {
                    if (piece instanceof FortressPieces.Throne) {
                        final BlockPos spawner = transform(piece, 3, 5, 5);
                        final double distance = origin.distanceSq(spawner);
                        if (distance < shortestDistance) {
                            shortestDistance = distance;
                            closest = spawner;
                        }
                    }
                }
            }
        }
        if (Double.isNaN(shortestDistance)) {
            throw new RuntimeException("Unable to locate any Nether Fortress");
        }
        return closest;
    }

    private static BlockPos transform(final StructurePiece piece, final int x, final int y, final int z) {
        final @Nullable Direction direction = piece.getCoordBaseMode();
        if (direction != null) {
            final MutableBoundingBox bb = piece.getBoundingBox();
            switch (direction) {
                case NORTH:
                    return new BlockPos(bb.minX + x, bb.minY + y, bb.maxZ - z);
                case SOUTH:
                    return new BlockPos(bb.minX + x, bb.minY + y, bb.minZ + z);
                case WEST:
                    return new BlockPos(bb.maxX - z, bb.minY + y, bb.minZ + x);
                case EAST:
                    return new BlockPos(bb.minX + z, bb.minY + y, bb.minZ + x);
            }
        }
        return new BlockPos(x, y, z);
    }
}
