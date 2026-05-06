package cn.kafei.pathswalked;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class Pathswalked extends JavaPlugin implements Listener {

    private static final int GRASS_TO_DIRT_STEPS = 4;
    private static final int DIRT_TO_COARSE_DIRT_STEPS = 6;
    private static final int COARSE_DIRT_TO_PATH_STEPS = 8;
    private static final int MAX_RESTORE_BLOCKS_PER_CHECK = 256;
    private static final long GAME_DAY_TICKS = 24_000L;
    private static final long MIN_RESTORE_IDLE_TICKS = GAME_DAY_TICKS;
    private static final long MAX_RESTORE_IDLE_TICKS = GAME_DAY_TICKS * 3L;
    private static final long RESTORE_CHECK_TICKS = 20L * 30L;

    private final Random random = new Random();
    private final Map<BlockKey, Integer> walkedBlocks = new HashMap<>();
    private final LinkedHashMap<BlockKey, PathBlock> pluginPaths = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::restoreIdlePaths, RESTORE_CHECK_TICKS, RESTORE_CHECK_TICKS);
    }

    @Override
    public void onDisable() {
        walkedBlocks.clear();
        pluginPaths.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null
                || from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.isFlying() || player.isInsideVehicle()) {
            return;
        }

        Block block = to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY() - 1, to.getBlockZ());
        Material material = block.getType();
        int requiredSteps = getRequiredSteps(material);

        if (requiredSteps == 0) {
            return;
        }

        World world = block.getWorld();
        BlockKey key = new BlockKey(world.getUID(), block.getX(), block.getY(), block.getZ());
        long fullTime = world.getFullTime();
        PathBlock pathBlock = pluginPaths.get(key);

        if (material != Material.GRASS_BLOCK && pathBlock == null) {
            return;
        }

        int steps = walkedBlocks.getOrDefault(key, 0) + 1;

        if (pathBlock != null) {
            pluginPaths.put(key, pathBlock.touch(fullTime, randomRestoreTicks()));
        }

        if (steps < requiredSteps) {
            walkedBlocks.put(key, steps);
            return;
        }

        Material nextMaterial = getNextMaterial(material);

        if (nextMaterial == null) {
            walkedBlocks.remove(key);
            return;
        }

        block.setType(nextMaterial, true);
        walkedBlocks.remove(key);
        pluginPaths.put(key, new PathBlock(nextMaterial, fullTime, randomRestoreTicks()));
    }

    private void restoreIdlePaths() {
        int checkedBlocks = 0;
        LinkedHashMap<BlockKey, PathBlock> deferredPaths = new LinkedHashMap<>();
        Iterator<Map.Entry<BlockKey, PathBlock>> iterator = pluginPaths.entrySet().iterator();

        while (iterator.hasNext() && checkedBlocks < MAX_RESTORE_BLOCKS_PER_CHECK) {
            checkedBlocks++;
            Map.Entry<BlockKey, PathBlock> entry = iterator.next();
            BlockKey key = entry.getKey();
            PathBlock pathBlock = entry.getValue();
            World world = Bukkit.getWorld(key.worldId);
            iterator.remove();

            if (world == null) {
                walkedBlocks.remove(key);
                continue;
            }

            long now = world.getFullTime();

            if (now - pathBlock.lastWalkedAt < pathBlock.restoreIdleTicks) {
                deferredPaths.put(key, pathBlock);
                continue;
            }

            Block block = world.getBlockAt(key.x, key.y, key.z);

            if (block.getType() != pathBlock.material) {
                walkedBlocks.remove(key);
                continue;
            }

            Material previousMaterial = getPreviousMaterial(pathBlock.material);

            if (previousMaterial == null) {
                walkedBlocks.remove(key);
                continue;
            }

            block.setType(previousMaterial, true);
            walkedBlocks.remove(key);

            if (previousMaterial != Material.GRASS_BLOCK) {
                deferredPaths.put(key, new PathBlock(previousMaterial, now, randomRestoreTicks()));
            }
        }

        pluginPaths.putAll(deferredPaths);
    }

    private int getRequiredSteps(Material material) {
        switch (material) {
            case GRASS_BLOCK:
                return GRASS_TO_DIRT_STEPS;
            case DIRT:
                return DIRT_TO_COARSE_DIRT_STEPS;
            case COARSE_DIRT:
                return COARSE_DIRT_TO_PATH_STEPS;
            default:
                return 0;
        }
    }

    private Material getNextMaterial(Material material) {
        switch (material) {
            case GRASS_BLOCK:
                return Material.DIRT;
            case DIRT:
                return Material.COARSE_DIRT;
            case COARSE_DIRT:
                return Material.DIRT_PATH;
            default:
                return null;
        }
    }

    private Material getPreviousMaterial(Material material) {
        switch (material) {
            case DIRT_PATH:
                return Material.COARSE_DIRT;
            case COARSE_DIRT:
                return Material.DIRT;
            case DIRT:
                return Material.GRASS_BLOCK;
            default:
                return null;
        }
    }

    private long randomRestoreTicks() {
        long range = MAX_RESTORE_IDLE_TICKS - MIN_RESTORE_IDLE_TICKS + 1L;
        return MIN_RESTORE_IDLE_TICKS + nextLong(range);
    }

    private long nextLong(long bound) {
        long bits;
        long value;

        do {
            bits = random.nextLong() & Long.MAX_VALUE;
            value = bits % bound;
        } while (bits - value + (bound - 1L) < 0L);

        return value;
    }

    private static final class PathBlock {

        private final Material material;
        private final long lastWalkedAt;
        private final long restoreIdleTicks;

        private PathBlock(Material material, long lastWalkedAt, long restoreIdleTicks) {
            this.material = material;
            this.lastWalkedAt = lastWalkedAt;
            this.restoreIdleTicks = restoreIdleTicks;
        }

        private PathBlock touch(long lastWalkedAt, long restoreIdleTicks) {
            return new PathBlock(material, lastWalkedAt, restoreIdleTicks);
        }
    }

    private static final class BlockKey {

        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;
        private final int hashCode;

        private BlockKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hashCode = calculateHashCode(worldId, x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BlockKey)) {
                return false;
            }
            BlockKey blockKey = (BlockKey) o;
            return x == blockKey.x && y == blockKey.y && z == blockKey.z && worldId.equals(blockKey.worldId);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int calculateHashCode(UUID worldId, int x, int y, int z) {
            int result = worldId.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
