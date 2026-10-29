package net.rose.elementSMPRefined.ability.main.mythic.life;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.particles.element.LifeParticles;
import net.rose.elementSMPRefined.util.scan.GroundScan;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class LifeTotemAbility extends BaseAbility {

    private static final NamespacedKey MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:life_totem"));

    private static final double LAUNCH_SPEED = 1.1;      // blocks per tick
    private static final double LAUNCH_LIFT = 0.12;      // extra upward push so the throw arcs
    private static final double GRAVITY = 0.05;          // blocks per tick^2
    private static final int MAX_FLIGHT_TICKS = 60;
    private static final float SPIN_PER_TICK = 0.5f;     // radians, end over end
    private static final float FLIGHT_SCALE = 1.2f;

    private static final float PLANTED_SCALE = 1.6f;     // model is ~1.5 blocks tall at scale 1 (~2.7 planted)
    private static final double EMBED = 0.05;            // sink the base slightly so there is no gap
    private static final int GROW_TICKS = 4;
    private static final int SHRINK_TICKS = 8;
    private static final int DURATION_TICKS = 200;       // 10 seconds standing
    private static final float COLLAPSED = 0.05f;        // never scale to exactly zero

    private static final int HITS_TO_BREAK = 3;          // critical hits needed to destroy it
    private static final float HITBOX_WIDTH = PLANTED_SCALE * 1.0f;
    private static final float HITBOX_HEIGHT = PLANTED_SCALE * (24.4f / 16f);

    // Health bar (flat coloured blocks that always face the viewer)
    private static final double BAR_OFFSET = 0.3;        // gap between the totem's top and its health bar
    private static final float BAR_WIDTH = 1.3f;
    private static final float BAR_HEIGHT = 0.11f;
    private static final float BAR_BORDER = 0.035f;
    private static final float BAR_GAP = 0.03f;          // between segments
    private static final float BAR_DEPTH = 0.08f;        // segments are thicker than the backing so they always sit in front
    private static final BlockData BAR_BACK = Material.BLACK_CONCRETE.createBlockData();
    private static final BlockData BAR_EMPTY = Material.GRAY_CONCRETE.createBlockData();
    private static final BlockData BAR_GREEN = Material.LIME_CONCRETE.createBlockData(); // brighter than lime concrete; swap here to retint
    private static final BlockData BAR_YELLOW = Material.YELLOW_CONCRETE.createBlockData();
    private static final BlockData BAR_RED = Material.RED_CONCRETE.createBlockData();

    private static final double ATTACK_REACH = 3.0;      // vanilla melee reach, used to pick which occupant was swung at
    private static final double OCCUPANT_AIM_MARGIN = 0.1; // forgiveness around an occupant's body, like vanilla's pick radius
    private static final java.util.Set<Material> EATABLE_INSIDE =
            java.util.EnumSet.of(Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE);

    private static final double RADIUS = Constants.Distance.LIFE_REGEN_RADIUS;
    private static final int PULSE_EVERY_TICKS = 20;
    private static final int PULSE_FIRST_TICK = 3;       // just after it finished planting
    private static final int REGEN_TICKS = 60;           // each pulse refreshes Regeneration II for 3s
    private static final int REGEN_AMPLIFIER = 1;
    private static final int RING_TICKS = 8;             // how long a pulse ring takes to expand

    private static final List<Totem> ACTIVE = new ArrayList<>();

    private static final Map<UUID, Totem> BY_HITBOX = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HIT_TICK = new HashMap<>();

    private final ElementSMPRefined plugin;

    public LifeTotemAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("life_totem", ElementType.LIFE, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        World world = player.getWorld();

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector start = eye.toVector().add(dir.clone().multiply(0.8)).add(new Vector(0, -0.3, 0));
        Vector velocity = dir.clone().multiply(LAUNCH_SPEED).add(new Vector(0, LAUNCH_LIFT, 0));

        ItemStack modelItem = ModelItems.of(MODEL);
        ItemDisplay display = world.spawn(start.toLocation(world), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(flightPose(0f));
            d.setPersistent(false);
            d.setInvulnerable(true);
        });

        Totem totem = new Totem(world, display, start, velocity, player.getUniqueId(),
                context.getTrustManager(), eye.getYaw());
        ACTIVE.add(totem);
        totem.runTaskTimer(plugin, 1L, 1L);

        world.playSound(eye, Sound.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.5f, 1.4f);
        return true;
    }

    /**
     * Routes a player's attack at an entity to the totem it belongs to, if any.
     * Called from {@code LifeTotemListener} for every attack.
     *
     * @return true if {@code target} is a totem's hitbox, meaning the attack event must be cancelled
     */
    public static boolean handleAttack(Player attacker, Entity target) {
        Totem totem = BY_HITBOX.get(target.getUniqueId());
        if (totem == null) return false;

        int tick = Bukkit.getCurrentTick();
        Integer last = LAST_HIT_TICK.put(attacker.getUniqueId(), tick);
        if (last != null && last == tick) return true; // same swing, already handled

        // Someone (player or mob) is inside the totem and the attacker is actually aiming at them: the (much
        // bigger) hitbox swallowed the swing, so hand it to them instead of the totem.
        LivingEntity occupant = totem.occupantInSights(attacker);
        if (occupant != null) {
            attacker.attack(occupant);
            return true;
        }

        totem.onHit(attacker, isCrit(attacker));
        return true;
    }

    public static void handleUse(Player player, Entity target, EquipmentSlot hand) {
        Totem totem = BY_HITBOX.get(target.getUniqueId());
        if (totem == null || !totem.contains(player)) return;
        if (player.isHandRaised()) return; // already eating; the held button re-sends the click every tick

        EquipmentSlot slot = hand;
        if (!EATABLE_INSIDE.contains(player.getInventory().getItem(slot).getType())) {
            // The client only tries the main hand when it consumed the click, so an off-hand gap is skipped too.
            if (hand != EquipmentSlot.HAND || !player.getInventory().getItemInMainHand().getType().isAir()) return;
            slot = EquipmentSlot.OFF_HAND;
            if (!EATABLE_INSIDE.contains(player.getInventory().getItem(slot).getType())) return;
        }
        player.startUsingItem(slot);
    }

    private static boolean isCrit(Player p) {
        return p.getFallDistance() > 0f
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isInWater()
                && !p.isSprinting()
                && !p.isInsideVehicle()
                && !p.hasPotionEffect(PotionEffectType.BLINDNESS)
                && p.getAttackCooldown() >= 0.9f;
    }

    public static void removeAll() {
        for (Totem t : new ArrayList<>(ACTIVE)) {
            t.cancelAndRemove();
        }
        ACTIVE.clear();
        BY_HITBOX.clear();
        LAST_HIT_TICK.clear();
    }

    private static Transformation flightPose(float spin) {
        return new Transformation(
                new Vector3f(),
                new Quaternionf().rotateX(spin),
                new Vector3f(FLIGHT_SCALE),
                new Quaternionf());
    }

    /**
     * Planted pose at a given growth. Scaling happens around the model's centre, so the display is dropped
     * by however much the totem shrank to keep its base on the ground.
     *
     * @param growth 1.0 = fully risen, {@link #COLLAPSED} = sunk into the ground
     */
    private static Transformation plantedPose(float yaw, float growth) {
        float s = PLANTED_SCALE * growth;
        float lift = -0.5f * PLANTED_SCALE * (1.0f - growth);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf().rotateY(yaw),
                new Vector3f(s, s, s),
                new Quaternionf());
    }

    private final class Totem extends BukkitRunnable {
        private final World world;
        private final ItemDisplay display;
        private final Vector pos;
        private final Vector vel;
        private final UUID ownerId;
        private final TrustManager trust;
        private final float yaw;

        private boolean planted = false;
        private int flightTicks = 0;
        private float spin = 0f;

        private Location base;       // where the totem's base touches the ground
        private int plantedAge = 0;  // ticks since it stuck into the ground
        private int ringStart = -100;

        private BlockDisplay barBack;                              // dark backing of the health bar
        private final List<BlockDisplay> barSegments = new ArrayList<>(); // one per hit left
        private Interaction hitbox;  // invisible entity players actually hit
        private int hits = 0;        // critical hits taken so far

        Totem(World world, ItemDisplay display, Vector pos, Vector vel, UUID ownerId, TrustManager trust, float yawDegrees) {
            this.world = world;
            this.display = display;
            this.pos = pos.clone();
            this.vel = vel.clone();
            this.ownerId = ownerId;
            this.trust = trust;
            this.yaw = (float) Math.toRadians(-yawDegrees + 180.0);
        }

        @Override
        public void run() {
            if (!display.isValid()) {
                finish();
                return;
            }
            if (planted) {
                tickPlanted();
            } else {
                tickFlight();
            }
        }

        private void tickFlight() {
            if (++flightTicks > MAX_FLIGHT_TICKS || pos.getY() < world.getMinHeight()) {
                fizzle();
                return;
            }

            vel.setY(vel.getY() - GRAVITY);
            double step = vel.length();
            RayTraceResult hit = world.rayTraceBlocks(pos.toLocation(world), vel.clone().normalize(), step,
                    FluidCollisionMode.NEVER, true);

            if (hit != null && hit.getHitPosition() != null) {
                plant(hit);
                return;
            }

            pos.add(vel);
            Location loc = pos.toLocation(world);
            display.teleport(loc);

            spin += SPIN_PER_TICK;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(flightPose(spin));

            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.15, 0.15, 0.15, 0.0, null, true);
        }

        private void plant(RayTraceResult hit) {
            Vector hp = hit.getHitPosition();
            BlockFace face = hit.getHitBlockFace();

            Location spot;
            if (face == BlockFace.UP) {
                spot = new Location(world, hp.getX(), hp.getY(), hp.getZ());
            } else {
                // Hit a wall or ceiling: slide out from it and drop to the floor below.
                double nx = hp.getX() + (face != null ? face.getModX() * 0.4 : 0);
                double nz = hp.getZ() + (face != null ? face.getModZ() * 0.4 : 0);
                Block ground = GroundScan.findGround(world, (int) Math.floor(nx), (int) Math.floor(nz),
                        (int) Math.floor(hp.getY()) + 1, 1, 12);
                if (ground == null) {
                    fizzle();
                    return;
                }
                spot = new Location(world, nx, ground.getY() + 1.0, nz);
            }

            base = spot;
            planted = true;
            plantedAge = 0;

            // Snap upright at the ground, collapsed, ready to rise next tick.
            display.setTeleportDuration(0);
            display.teleport(new Location(world, spot.getX(),
                    spot.getY() + 0.5 * PLANTED_SCALE - EMBED, spot.getZ()));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTransformation(plantedPose(yaw, COLLAPSED));

            hitbox = world.spawn(spot, Interaction.class, i -> {
                i.setInteractionWidth(HITBOX_WIDTH);
                i.setInteractionHeight(HITBOX_HEIGHT);
                i.setResponsive(true);
                i.setPersistent(false);
                i.setInvulnerable(true);
            });
            BY_HITBOX.put(hitbox.getUniqueId(), this);

            spawnBar(spot.clone().add(0, HITBOX_HEIGHT + BAR_OFFSET, 0));

            Block under = world.getBlockAt(spot.getBlockX(), (int) Math.floor(spot.getY() - 0.1), spot.getBlockZ());
            world.spawnParticle(Particle.BLOCK, spot.clone().add(0, 0.1, 0), 18, 0.35, 0.1, 0.35, 0.0,
                    under.getBlockData(), true);
            world.playSound(spot, Sound.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.2f, 0.7f);
            world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
        }

        private void tickPlanted() {
            plantedAge++;
            int t = plantedAge;

            if (t == 1) {
                // Interpolation only animates if the transformation changes after the entity was sent.
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(GROW_TICKS);
                display.setTransformation(plantedPose(yaw, 1.0f));
            }

            if (t >= PULSE_FIRST_TICK && (t - PULSE_FIRST_TICK) % PULSE_EVERY_TICKS == 0 && t < DURATION_TICKS) {
                pulse();
                ringStart = t;
            }

            if (t - ringStart < RING_TICKS) {
                drawRing(RADIUS * (t - ringStart + 1) / RING_TICKS);
            }

            if (t % 5 == 0 && t < DURATION_TICKS) {
                ambient();
            }

            if (hitbox != null && !hitbox.isValid()) { // hitbox got removed out from under us
                finish();
                return;
            }

            if (t == DURATION_TICKS) {
                removeHitbox(); // expiring: no longer breakable while it sinks
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(SHRINK_TICKS);
                display.setTransformation(plantedPose(yaw, COLLAPSED));
                world.playSound(base, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);
            } else if (t >= DURATION_TICKS + SHRINK_TICKS + 2) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 0.6, 0), 10, 0.4, 0.5, 0.4, 0.0, null, true);
                finish();
            }
        }

        private void pulse() {
            for (Player other : world.getNearbyPlayers(base, RADIUS)) {
                boolean owner = other.getUniqueId().equals(ownerId);
                boolean trusted = trust != null && trust.isTrusted(ownerId, other.getUniqueId());
                if (!owner && !trusted) continue;

                other.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGEN_TICKS,
                        REGEN_AMPLIFIER, true, true, true));
            }
            world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0f, 0.8f);
        }

        private void drawRing(double radius) {
            int points = 28;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2 * i) / points;
                Location p = base.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius);
                LifeParticles.totemRingPoint(world, p);
            }
        }

        private void ambient() {
            world.spawnParticle(Particle.HAPPY_VILLAGER, base.clone().add(0, 1.8, 0), 2, 0.3, 0.3, 0.3, 0.0, null, true);
            double a = world.getGameTime() * 0.3;
            LifeParticles.totemRingPoint(world, base.clone().add(Math.cos(a) * RADIUS, 0.15, Math.sin(a) * RADIUS));
        }

        boolean contains(Entity p) {
            return hitbox != null && hitbox.isValid() && hitbox.getBoundingBox().overlaps(p.getBoundingBox());
        }

        LivingEntity occupantInSights(Player attacker) {
            if (hitbox == null || !hitbox.isValid()) return null;

            Location eye = attacker.getEyeLocation();
            Vector origin = eye.toVector();
            Vector direction = eye.getDirection();

            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity e : hitbox.getNearbyEntities(HITBOX_WIDTH, HITBOX_HEIGHT, HITBOX_WIDTH)) {
                if (!(e instanceof LivingEntity p) || e instanceof ArmorStand || p.equals(attacker)
                        || !p.isValid() || p.isDead()) continue;
                if (p instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) continue;
                if (!contains(p)) continue;

                BoundingBox body = p.getBoundingBox().clone().expand(OCCUPANT_AIM_MARGIN);
                RayTraceResult ray = body.rayTrace(origin, direction, ATTACK_REACH);
                if (ray == null || ray.getHitPosition() == null) continue;

                double distance = ray.getHitPosition().distance(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = p;
                }
            }
            return best;
        }

        void onHit(Player attacker, boolean crit) {
            UUID id = attacker.getUniqueId();
            if (id.equals(ownerId) || (trust != null && trust.isTrusted(ownerId, id))) return;

            Location centre = base.clone().add(0, HITBOX_HEIGHT * 0.5, 0);

            if (!crit) {
                world.playSound(centre, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.4f);
                return;
            }

            hits++;
            updateBar();
            world.spawnParticle(Particle.CRIT, centre, 20, 0.4, 0.6, 0.4, 0.3, null, true);
            world.playSound(centre, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.9f);

            if (hits >= HITS_TO_BREAK) {
                breakTotem(attacker);
            } else {
                world.playSound(centre, Sound.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
        }

        private void breakTotem(Player breaker) {
            Location centre = base.clone().add(0, HITBOX_HEIGHT * 0.5, 0);
            Block under = world.getBlockAt(base.getBlockX(), (int) Math.floor(base.getY() - 0.1), base.getBlockZ());
            world.spawnParticle(Particle.BLOCK, centre, 40, 0.5, 0.9, 0.5, 0.0, under.getBlockData(), true);
            world.spawnParticle(Particle.HAPPY_VILLAGER, centre, 15, 0.5, 0.8, 0.5, 0.0, null, true);
            world.playSound(centre, Sound.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.5f, 0.6f);
            world.playSound(centre, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.2f);

            finish();
        }

        private void spawnBar(Location at) {
            float segW = (BAR_WIDTH - BAR_GAP * (HITS_TO_BREAK - 1)) / HITS_TO_BREAK;

            barBack = spawnBarPiece(at, BAR_BACK,
                    BAR_WIDTH + BAR_BORDER * 2, BAR_HEIGHT + BAR_BORDER * 2, 0.01f, 0f);

            for (int i = 0; i < HITS_TO_BREAK; i++) {
                float centreX = -BAR_WIDTH / 2f + segW / 2f + i * (segW + BAR_GAP);
                barSegments.add(spawnBarPiece(at, BAR_GREEN, segW, BAR_HEIGHT, BAR_DEPTH, centreX));
            }
        }

        private BlockDisplay spawnBarPiece(Location at, BlockData data, float w, float h, float d, float centreX) {
            return world.spawn(at, BlockDisplay.class, e -> {
                e.setBlock(data);
                e.setBillboard(Display.Billboard.CENTER);
                e.setBrightness(new Display.Brightness(15, 15)); // flat colour, no shading
                e.setTransformation(new Transformation(
                        new Vector3f(centreX - w / 2f, -h / 2f, -d / 2f),
                        new Quaternionf(),
                        new Vector3f(w, h, d),
                        new Quaternionf()));
                e.setPersistent(false);
                e.setInvulnerable(true);
            });
        }

        private void updateBar() {
            int left = Math.max(0, HITS_TO_BREAK - hits);
            double ratio = (double) left / HITS_TO_BREAK;
            BlockData fill = ratio > 0.6 ? BAR_GREEN : ratio > 0.3 ? BAR_YELLOW : BAR_RED;

            for (int i = 0; i < barSegments.size(); i++) {
                BlockDisplay seg = barSegments.get(i);
                if (seg.isValid()) seg.setBlock(i < left ? fill : BAR_EMPTY);
            }
        }

        private void removeBar() {
            if (barBack != null) {
                if (barBack.isValid()) barBack.remove();
                barBack = null;
            }
            for (BlockDisplay seg : barSegments) {
                if (seg.isValid()) seg.remove();
            }
            barSegments.clear();
        }

        private void removeHitbox() {
            removeBar();
            if (hitbox == null) return;
            BY_HITBOX.remove(hitbox.getUniqueId());
            if (hitbox.isValid()) hitbox.remove();
            hitbox = null;
        }

        private void fizzle() {
            world.spawnParticle(Particle.SMOKE, pos.toLocation(world), 8, 0.2, 0.2, 0.2, 0.02, null, true);
            finish();
        }

        private void finish() {
            cancelAndRemove();
            ACTIVE.remove(this);
        }

        void cancelAndRemove() {
            if (!isCancelled()) cancel();
            removeHitbox();
            if (display.isValid()) display.remove();
        }
    }

    public String getName() {
        return ChatColor.GREEN + "Totem of Life";
    }

    public String getDescription() {
        return "Launch a totem that plants in the ground and regenerates you and trusted players nearby.";
    }
}