package net.rose.elementSMPRefined.altar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.structure.Palette;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Owns the Reforge Altar: the 6-12 hour spawn timer, picking and validating a
 * site, placing the structure (after snapshotting the terrain it overwrites),
 * the floating recipe text above the lodestone, and removing the structure
 * again - restoring the snapshot - once someone turns the ingredients in.
 * <p>
 * Only one altar exists at a time. State (next spawn time, the active altar,
 * its recipe, and the original blocks it replaced) is persisted to
 * {@code reforge_altar.yml}, so it survives restarts.
 * The world is stored by name and resolved lazily because this plugin loads at
 * STARTUP, before any world exists.
 */
public final class ReforgeAltarManager {

    /** Structure footprint, from the bundled reforge_altar.nbt (13 x 6 x 13). */
    public static final int SIZE_X = 13;
    public static final int SIZE_Y = 6;
    public static final int SIZE_Z = 13;
    /** Lodestone position inside the structure, relative to its origin corner. */
    public static final int LODESTONE_X = 6;
    public static final int LODESTONE_Y = 1;
    public static final int LODESTONE_Z = 6;

    private static final String STRUCTURE_RESOURCE = "structures/reforge_altar.nbt";
    private static final long RETRY_AFTER_FAILED_SPAWN_MS = 5 * 60 * 1000L;
    private static final long HOUR_MS = 60 * 60 * 1000L;
    private static final double DISPLAY_HEIGHT = 1.9;

    /** The altar currently in the world. Immutable; replaced, never edited. */
    public record Altar(String worldName, int originX, int originY, int originZ, ForgeRecipe recipe) {
        public int lodestoneX() { return originX + LODESTONE_X; }
        public int lodestoneY() { return originY + LODESTONE_Y; }
        public int lodestoneZ() { return originZ + LODESTONE_Z; }
    }

    private final JavaPlugin plugin;
    private final File stateFile;
    private final Random random = new Random();

    /** One non-air block of the structure, relative to its origin corner. */
    private record Piece(int dx, int dy, int dz, BlockData data) {}

    private Structure altarStructure;
    private final List<Piece> pieces = new ArrayList<>();
    /** Columns (dx * SIZE_Z + dz) covered by the floor disc - plants above these get cleared. */
    private final Set<Integer> floorColumns = new HashSet<>();
    /** Original blocks the active altar replaced: "dx,dy,dz|blockdata" - persisted in reforge_altar.yml. */
    private List<String> snapshot = new ArrayList<>();
    private volatile Altar active;
    private boolean spawning;
    private long nextSpawnAtMillis;
    private UUID displayId;
    private BukkitTask tickTask;

    public ReforgeAltarManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "reforge_altar.yml");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void start() {
        try (InputStream in = plugin.getResource(STRUCTURE_RESOURCE)) {
            if (in == null) {
                plugin.getLogger().severe("Missing " + STRUCTURE_RESOURCE + " in the plugin jar - Reforge Altar disabled.");
                return;
            }
            altarStructure = Bukkit.getStructureManager().loadStructure(in);
            readPieces();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load the Reforge Altar structure - altar disabled.", e);
            return;
        }

        loadState();
        // Checked every 2 seconds: cheap (a couple of comparisons), and keeps the
        // text display alive across chunk unloads without any chunk listeners.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    /** Reads the structure's non-air blocks once, so placing can skip air and never carve terrain. */
    private void readPieces() {
        pieces.clear();
        floorColumns.clear();
        List<Palette> palettes = altarStructure.getPalettes();
        if (palettes.isEmpty()) return;
        for (BlockState state : palettes.get(0).getBlocks()) {
            BlockData data = state.getBlockData();
            if (data.getMaterial().isAir()) continue;
            pieces.add(new Piece(state.getX(), state.getY(), state.getZ(), data));
            if (state.getY() == 0) floorColumns.add(state.getX() * SIZE_Z + state.getZ());
        }
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        removeDisplay();
    }

    private boolean isEnabled() {
        return altarStructure != null && cfg().getBoolean("reforge_altar.enabled", true);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public boolean hasActiveAltar() { return active != null; }

    public Altar getActiveAltar() { return active; }

    public long getNextSpawnAtMillis() { return nextSpawnAtMillis; }

    /** True if {@code block} is the lodestone of the active altar. Hot path for the protection listeners. */
    public boolean isAltarLodestone(Block block) {
        Altar a = active;
        if (a == null || block.getType() != Material.LODESTONE) return false;
        return block.getX() == a.lodestoneX()
                && block.getY() == a.lodestoneY()
                && block.getZ() == a.lodestoneZ()
                && block.getWorld().getName().equals(a.worldName());
    }

    public Location getLodestoneLocation() {
        Altar a = active;
        if (a == null) return null;
        World world = Bukkit.getWorld(a.worldName());
        if (world == null) return null;
        return new Location(world, a.lodestoneX(), a.lodestoneY(), a.lodestoneZ());
    }

    // ------------------------------------------------------------------
    // Timer
    // ------------------------------------------------------------------

    private void tick() {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        if (nextSpawnAtMillis <= 0) {
            scheduleNextSpawn();
        } else if (now >= nextSpawnAtMillis) {
            if (active == null && !spawning) {
                spawnRandom();
            } else if (!spawning) {
                // The previous altar was never claimed - don't stack a second one.
                scheduleNextSpawn();
            }
        }

        maintainDisplay();
    }

    private void scheduleNextSpawn() {
        double minH = Math.max(0.0, cfg().getDouble("reforge_altar.min_interval_hours", 6.0));
        double maxH = Math.max(minH, cfg().getDouble("reforge_altar.max_interval_hours", 12.0));
        double hours = minH + random.nextDouble() * (maxH - minH);
        nextSpawnAtMillis = System.currentTimeMillis() + (long) (hours * HOUR_MS);
        saveState();
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    /** Spawns an altar at a random valid site now. Returns false if one exists / is being placed. */
    public boolean spawnRandom() {
        if (altarStructure == null || active != null || spawning) return false;
        World world = resolveWorld();
        if (world == null) {
            plugin.getLogger().warning("Reforge Altar: no usable overworld found, retrying later.");
            nextSpawnAtMillis = System.currentTimeMillis() + RETRY_AFTER_FAILED_SPAWN_MS;
            saveState();
            return false;
        }
        spawning = true;
        tryRandomSite(world, 0);
        return true;
    }

    /**
     * Spawns an altar whose origin corner is chosen so its lodestone sits at
     * {@code lodestoneX/Z}, with the floor layer replacing the block at
     * {@code floorY}. No site validation - for admin testing.
     */
    public boolean spawnAt(World world, int lodestoneX, int floorY, int lodestoneZ) {
        if (altarStructure == null || active != null || spawning) return false;
        spawning = true;
        int ox = lodestoneX - LODESTONE_X;
        int oz = lodestoneZ - LODESTONE_Z;
        loadFootprint(world, ox, oz).whenComplete((v, err) -> onMain(() -> {
            if (err != null || !place(world, ox, floorY, oz, randomRecipe())) {
                spawning = false;
            }
        }));
        return true;
    }

    private void tryRandomSite(World world, int attempt) {
        int maxAttempts = Math.max(1, cfg().getInt("reforge_altar.spawn_attempts", 25));
        if (attempt >= maxAttempts) {
            spawning = false;
            plugin.getLogger().warning("Reforge Altar: found no valid site after " + maxAttempts + " attempts, retrying in 5 minutes.");
            nextSpawnAtMillis = System.currentTimeMillis() + RETRY_AFTER_FAILED_SPAWN_MS;
            saveState();
            return;
        }

        double minR = Math.max(0, cfg().getDouble("reforge_altar.min_radius", 300));
        double maxR = Math.max(minR + 1, cfg().getDouble("reforge_altar.max_radius", 3000));
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = minR + random.nextDouble() * (maxR - minR);
        Location spawn = world.getSpawnLocation();
        int cx = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int cz = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        int ox = cx - LODESTONE_X;
        int oz = cz - LODESTONE_Z;

        if (!footprintInsideBorder(world, ox, oz)) {
            tryRandomSite(world, attempt + 1);
            return;
        }

        loadFootprint(world, ox, oz).whenComplete((v, err) -> onMain(() -> {
            if (err != null) {
                tryRandomSite(world, attempt + 1);
                return;
            }
            Integer floorY = evaluateSite(world, ox, oz);
            if (floorY == null) {
                tryRandomSite(world, attempt + 1);
                return;
            }
            if (!place(world, ox, floorY, oz, randomRecipe())) {
                spawning = false;
                nextSpawnAtMillis = System.currentTimeMillis() + RETRY_AFTER_FAILED_SPAWN_MS;
                saveState();
            }
        }));
    }

    private World resolveWorld() {
        String configured = cfg().getString("reforge_altar.world", "");
        if (configured != null && !configured.isBlank()) {
            World w = Bukkit.getWorld(configured.trim());
            if (w != null && w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) return w;
        }
        return null;
    }

    private boolean footprintInsideBorder(World world, int ox, int oz) {
        var border = world.getWorldBorder();
        return border.isInside(new Location(world, ox, 64, oz))
                && border.isInside(new Location(world, ox + SIZE_X, 64, oz + SIZE_Z));
    }

    /**
     * Checks every column under the floor disc (not a coarse sample) and returns
     * the Y the floor should sit at - one block ABOVE the highest ground under it,
     * so it rests on the ground instead of digging in. Returns null if any part of
     * the floor would hang more than {@code max_floor_gap} blocks above the ground
     * (so it can never end up floating over a hole, held up by a single point), if
     * the ground is liquid, or if there are trees / liquid inside the volume.
     */
    private Integer evaluateSite(World world, int ox, int oz) {
        int maxGap = Math.max(0, cfg().getInt("reforge_altar.max_floor_gap", 1));

        int[] heights = new int[floorColumns.size()];
        int max = Integer.MIN_VALUE;
        int i = 0;
        for (int col : floorColumns) {
            int x = ox + col / SIZE_Z;
            int z = oz + col % SIZE_Z;
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (world.getBlockAt(x, y, z).isLiquid()) return null;
            heights[i++] = y;
            max = Math.max(max, y);
        }
        if (heights.length == 0) return null;

        int floorY = max + 1;
        for (int h : heights) {
            // Floor block sits at floorY; the ground's top block is h, so the air gap is floorY - h - 1.
            if (floorY - h - 1 > maxGap) return null;
        }
        if (floorY < world.getMinHeight() || floorY + SIZE_Y > world.getMaxHeight()) return null;

        // No trees (logs/leaves) or liquid anywhere inside the structure's volume.
        for (int dx = 0; dx < SIZE_X; dx++) {
            for (int dz = 0; dz < SIZE_Z; dz++) {
                for (int dy = 0; dy < SIZE_Y; dy++) {
                    Block b = world.getBlockAt(ox + dx, floorY + dy, oz + dz);
                    Material m = b.getType();
                    if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || b.isLiquid()) return null;
                }
            }
        }
        return floorY;
    }

    /**
     * Places the altar's non-air blocks (air is skipped, so nothing around it
     * is carved away), recording every block it replaces so completeAltar()
     * can put it all back exactly. Small plants inside the disc are cleared
     * too (and restored later) so grass doesn't poke through the floor.
     */
    private boolean place(World world, int ox, int oy, int oz, ForgeRecipe recipe) {
        if (pieces.isEmpty()) {
            plugin.getLogger().severe("Reforge Altar: structure has no blocks, not spawning.");
            return false;
        }

        List<String> saved = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();

        for (Piece p : pieces) {
            Block block = world.getBlockAt(ox + p.dx(), oy + p.dy(), oz + p.dz());
            saved.add(entry(p.dx(), p.dy(), p.dz(), block.getBlockData()));
            placed.add(index(p.dx(), p.dy(), p.dz()));
            block.setBlockData(p.data(), false);
        }

        // Clear grass/flowers/snow layers above the floor disc so they don't clip through it.
        for (int col : floorColumns) {
            int dx = col / SIZE_Z;
            int dz = col % SIZE_Z;
            for (int dy = 1; dy < SIZE_Y; dy++) {
                if (placed.contains(index(dx, dy, dz))) continue;
                Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                Material m = block.getType();
                if (!m.isAir() && !m.isSolid() && !block.isLiquid()) {
                    saved.add(entry(dx, dy, dz, block.getBlockData()));
                    block.setType(Material.AIR, false);
                }
            }
        }

        snapshot = saved;
        active = new Altar(world.getName(), ox, oy, oz, recipe);
        spawning = false;
        scheduleNextSpawn(); // also saves state (incl. the new active altar + snapshot)
        spawnDisplay();

        plugin.getLogger().info("Reforge Altar spawned at " + active.lodestoneX() + ", " + active.lodestoneY()
                + ", " + active.lodestoneZ() + " in " + world.getName() + " (" + recipe.name() + ").");
        announce();
        return true;
    }

    private static int index(int dx, int dy, int dz) {
        return (dy * SIZE_X + dx) * SIZE_Z + dz;
    }

    private static String entry(int dx, int dy, int dz, BlockData data) {
        return dx + "," + dy + "," + dz + "|" + data.getAsString();
    }

    private void announce() {
        if (!cfg().getBoolean("reforge_altar.announce", true)) return;
        Altar a = active;
        if (a == null) return;
        Component msg = Component.text("A Reforge Altar has appeared", NamedTextColor.GOLD);
        if (cfg().getBoolean("reforge_altar.announce_coordinates", true)) {
            msg = msg.append(Component.text(" near ", NamedTextColor.GOLD))
                    .append(Component.text(a.lodestoneX() + ", " + a.lodestoneY() + ", " + a.lodestoneZ(), NamedTextColor.YELLOW));
        }
        Bukkit.broadcast(msg.append(Component.text("!", NamedTextColor.GOLD)));
    }

    private ForgeRecipe randomRecipe() {
        ForgeRecipe[] all = ForgeRecipe.values();
        return all[random.nextInt(all.length)];
    }

    // ------------------------------------------------------------------
    // Completion / removal
    // ------------------------------------------------------------------

    /**
     * Ends the active altar: removes the text display and puts the terrain
     * back exactly as it was before the altar spawned (which also removes the
     * structure and repairs anything it overwrote). The caller is responsible
     * for having checked/consumed the ingredients and given the reward.
     * Returns false if there is no altar.
     */
    public boolean completeAltar() {
        Altar a = active;
        if (a == null) return false;

        List<String> toRestore = snapshot;
        // Clear first so a second click can't double-claim while the restore is pending.
        active = null;
        snapshot = new ArrayList<>();
        saveState();
        removeDisplay();

        World world = Bukkit.getWorld(a.worldName());
        if (world == null) return true;
        loadFootprint(world, a.originX(), a.originZ())
                .whenComplete((v, err) -> onMain(() -> restoreTerrain(world, a, toRestore)));
        return true;
    }

    private void restoreTerrain(World world, Altar a, List<String> entries) {
        if (entries.isEmpty()) {
            plugin.getLogger().warning("Reforge Altar: no saved terrain to restore, only removing the lodestone.");
            world.getBlockAt(a.lodestoneX(), a.lodestoneY(), a.lodestoneZ()).setType(Material.AIR, false);
            return;
        }
        for (String line : entries) {
            try {
                int bar = line.indexOf('|');
                String[] pos = line.substring(0, bar).split(",");
                BlockData data = Bukkit.createBlockData(line.substring(bar + 1));
                world.getBlockAt(a.originX() + Integer.parseInt(pos[0]),
                        a.originY() + Integer.parseInt(pos[1]),
                        a.originZ() + Integer.parseInt(pos[2])).setBlockData(data, false);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Reforge Altar: bad snapshot entry '" + line + "'", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // Text display
    // ------------------------------------------------------------------

    private Component buildDisplayText(ForgeRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Forged Upgrader", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        for (ForgeRecipe.Ingredient ing : recipe.ingredients()) {
            lines.add(Component.text(ing.label(), NamedTextColor.WHITE));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }

    private void spawnDisplay() {
        Altar a = active;
        if (a == null) return;
        World world = Bukkit.getWorld(a.worldName());
        if (world == null) return;

        Location loc = new Location(world, a.lodestoneX() + 0.5, a.lodestoneY() + DISPLAY_HEIGHT, a.lodestoneZ() + 0.5);
        Component text = buildDisplayText(a.recipe());
        TextDisplay display = world.spawn(loc, TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            // Not persistent: it's re-created by maintainDisplay() whenever the
            // chunk is loaded without one, so it can never be orphaned or duplicated.
            td.setPersistent(false);
        });
        displayId = display.getUniqueId();
    }

    private void removeDisplay() {
        if (displayId != null) {
            Entity e = Bukkit.getEntity(displayId);
            if (e != null) e.remove();
            displayId = null;
        }
    }

    /** Re-creates the floating text if the altar's chunk is loaded and the display is missing. */
    private void maintainDisplay() {
        Altar a = active;
        if (a == null) return;
        World world = Bukkit.getWorld(a.worldName());
        if (world == null) return;

        int cx = a.lodestoneX() >> 4;
        int cz = a.lodestoneZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) return;

        if (displayId != null) {
            Entity e = Bukkit.getEntity(displayId);
            if (e != null && e.isValid()) return;
        }
        spawnDisplay();
    }

    // ------------------------------------------------------------------
    // Chunk helpers
    // ------------------------------------------------------------------

    private CompletableFuture<Void> loadFootprint(World world, int ox, int oz) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int cx = ox >> 4; cx <= (ox + SIZE_X - 1) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + SIZE_Z - 1) >> 4; cz++) {
                futures.add(world.getChunkAtAsync(cx, cz));
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private void loadState() {
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        nextSpawnAtMillis = yaml.getLong("next_spawn_at", 0L);

        String worldName = yaml.getString("altar.world");
        String recipeName = yaml.getString("altar.recipe");
        if (worldName == null || recipeName == null) return;
        try {
            active = new Altar(worldName,
                    yaml.getInt("altar.x"), yaml.getInt("altar.y"), yaml.getInt("altar.z"),
                    ForgeRecipe.valueOf(recipeName));
            snapshot = new ArrayList<>(yaml.getStringList("altar.snapshot"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Reforge Altar: unknown recipe '" + recipeName + "' in saved state, dropping the altar record.");
        }
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("next_spawn_at", nextSpawnAtMillis);
        Altar a = active;
        if (a != null) {
            yaml.set("altar.world", a.worldName());
            yaml.set("altar.x", a.originX());
            yaml.set("altar.y", a.originY());
            yaml.set("altar.z", a.originZ());
            yaml.set("altar.recipe", a.recipe().name());
            yaml.set("altar.snapshot", snapshot);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save reforge_altar.yml", e);
        }
    }
}
