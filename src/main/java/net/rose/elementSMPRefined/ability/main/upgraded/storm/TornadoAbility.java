package net.rose.elementSMPRefined.ability.main.upgraded.storm;

import net.rose.elementSMPRefined.core.Constants;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.TornadoVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TornadoAbility extends BaseAbility {

    private static final int DURATION_TICKS = 6 * 20;
    private static final int GROW_TICKS = 15;
    private static final int FADE_TICKS = 15;
    private static final double CAST_RANGE = 22.0;
    private static final double CAST_GROUND_SEARCH = 40.0;

    private static final double SPEED = 0.17;
    private static final double WANDER_JITTER = Math.toRadians(3.5);
    private static final double TURN_RATE = Math.toRadians(2.5);
    private static final double HOMING_RANGE = 18.0;
    private static final double LEASH = 40.0;
    private static final double MAX_STEP_UP = 1.2;
    private static final double MAX_DROP_PER_TICK = 0.4;
    private static final double GROUND_SEARCH = 9.0;
    private static final int RETARGET_INTERVAL = 5;

    private static final double PULL_RADIUS = 9.0;
    private static final double PULL_SPEED_FAR = 0.20;    // blocks/tick at the edge of the pull...
    private static final double PULL_SPEED_NEAR = 0.50;   // ...and right next to the funnel
    private static final double PULL_SWIRL = 0.22;        // sideways drift while being pulled in (reads as a spiral)
    private static final double MAX_SPEED = 0.75;

    private static final double INSIDE_MARGIN = 0.8;
    private static final double ORBIT_SPEED = 0.45;       // how fast they're whipped round the axis
    private static final double LIFT_SPEED = 0.42;
    private static final double LIFT_MAX = 5.5;
    private static final double DAMAGE = 2.0;             // true damage per hit (1 heart)
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    private static final Sound WIND_SOUND = Sound.ITEM_ELYTRA_FLYING;
    private static final SoundCategory WIND_CATEGORY = SoundCategory.AMBIENT;
    private static final int WIND_INTERVAL_TICKS = 10;
    private static final double WIND_VOLUME = 0.4;
    private static final double WIND_LISTEN_RANGE = 24.0;
    private static final int WIND_FADE_STEPS = 6;
    private static final int WIND_FADE_STEP_TICKS = 5;

    private final ElementSMPRefined plugin;
    private final Map<UUID, Tornado> tornadoes = new HashMap<>();

    private static final class Tornado {
        final World world;
        final Location pos;
        final TornadoVisual visual;
        final TrustManager trust;
        double heading;
        int ticks = 0;
        Player chasing;
        final Map<UUID, Integer> nextHit = new HashMap<>();
        BukkitRunnable task;
        final Set<UUID> windListeners = new HashSet<>();

        Tornado(World world, Location pos, TornadoVisual visual, TrustManager trust, double heading) {
            this.world = world;
            this.pos = pos;
            this.visual = visual;
            this.trust = trust;
            this.heading = heading;
        }
    }

    public TornadoAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("storm_tornado", ElementType.STORM, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player caster = context.getPlayer();
        if (caster == null) return false;

        end(caster.getUniqueId(), false); // one tornado per caster

        Location start = findStart(caster);
        if (start == null) {
            caster.sendMessage(Component.text("There's no ground there to summon a tornado on.", NamedTextColor.GRAY));
            return false;
        }

        // It sets off in the direction the caster is facing.
        Location facing = caster.getLocation();
        facing.setPitch(0f);
        Vector look = facing.getDirection();
        double heading = Math.atan2(look.getZ(), look.getX());

        Tornado tornado = new Tornado(start.getWorld(), start, new TornadoVisual(start, caster),
                context.getTrustManager(), heading);
        tornadoes.put(caster.getUniqueId(), tornado);
        setActive(caster, true);

        World world = tornado.world;
        world.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.6f);
        world.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 0.7f);

        tornado.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline() || tornado.ticks >= DURATION_TICKS) {
                    end(caster.getUniqueId(), tornado.ticks >= DURATION_TICKS);
                    return;
                }
                tick(tornado, caster);
                tornado.ticks++;
            }
        };
        tornado.task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private Location findStart(Player caster) {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, CAST_RANGE, FluidCollisionMode.NEVER, true);
        Vector point;
        double searchFrom;
        if (hit != null && hit.getHitPosition() != null) {
            point = hit.getHitPosition().clone();
            BlockFace face = hit.getHitBlockFace();
            if (face != null) {
                point.add(face.getDirection().multiply(0.6)); // back off the surface so we start in open air
            }
            searchFrom = point.getY() + (face == BlockFace.DOWN ? -0.2 : 0.5);
        } else {
            point = eye.toVector().add(direction.clone().multiply(CAST_RANGE));
            searchFrom = point.getY();
        }

        Double ground = groundY(world, point.getX(), searchFrom, point.getZ(), CAST_GROUND_SEARCH);
        if (ground == null) return null;
        return new Location(world, point.getX(), ground, point.getZ());
    }

    private void tick(Tornado t, Player caster) {
        double growth = growth(t.ticks);

        steer(t, caster);
        move(t, growth);

        t.visual.moveTo(t.pos);
        t.visual.update(t.ticks, growth);

        suck(t, caster, growth);

        // Wind loop, handled per player so it can be cut off the moment someone leaves range.
        if (t.ticks % WIND_INTERVAL_TICKS == 0) {
            updateWind(t, growth);
        }
    }

    private void updateWind(Tornado t, double growth) {
        boolean fading = t.ticks >= DURATION_TICKS - FADE_TICKS;
        double rangeSq = WIND_LISTEN_RANGE * WIND_LISTEN_RANGE;

        // Cut off anyone who is no longer in range.
        Iterator<UUID> it = t.windListeners.iterator();
        while (it.hasNext()) {
            Player listener = plugin.getServer().getPlayer(it.next());
            if (listener == null || !listener.isOnline()) {
                it.remove();
                continue;
            }
            boolean inRange = listener.getWorld().equals(t.world)
                    && listener.getLocation().distanceSquared(t.pos) <= rangeSq;
            if (!inRange) {
                listener.stopSound(WIND_SOUND, WIND_CATEGORY);
                it.remove();
            }
        }

        if (fading) return;

        // Start / keep the loop going for everyone in range.
        for (Player listener : t.world.getNearbyPlayers(t.pos, WIND_LISTEN_RANGE)) {
            listener.playSound(t.pos, WIND_SOUND, WIND_CATEGORY, (float) (WIND_VOLUME * growth), 0.5f);
            t.windListeners.add(listener.getUniqueId());
        }
    }

    private void fadeWind(Tornado t) {
        if (t.windListeners.isEmpty()) return;

        Location at = t.pos.clone();
        Set<UUID> listeners = new HashSet<>(t.windListeners);
        t.windListeners.clear();
        double rangeSq = WIND_LISTEN_RANGE * WIND_LISTEN_RANGE;

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                step++;
                boolean last = step >= WIND_FADE_STEPS;
                float volume = (float) (WIND_VOLUME * (1.0 - step / (double) WIND_FADE_STEPS));

                Iterator<UUID> it = listeners.iterator();
                while (it.hasNext()) {
                    Player listener = plugin.getServer().getPlayer(it.next());
                    if (listener == null || !listener.isOnline()) {
                        it.remove();
                        continue;
                    }
                    listener.stopSound(WIND_SOUND, WIND_CATEGORY);
                    boolean inRange = listener.getWorld().equals(at.getWorld())
                            && listener.getLocation().distanceSquared(at) <= rangeSq;
                    if (last || !inRange) {
                        it.remove();
                        continue;
                    }
                    listener.playSound(at, WIND_SOUND, WIND_CATEGORY, volume, 0.5f);
                }

                if (last || listeners.isEmpty()) cancel();
            }
        }.runTaskTimer(plugin, 0L, WIND_FADE_STEP_TICKS);
    }

    private void stopWind(Tornado t) {
        for (UUID id : t.windListeners) {
            Player listener = plugin.getServer().getPlayer(id);
            if (listener != null && listener.isOnline()) {
                listener.stopSound(WIND_SOUND, WIND_CATEGORY);
            }
        }
        t.windListeners.clear();
    }

    private static double growth(int ticks) {
        double in = Math.min(1.0, ticks / (double) GROW_TICKS);
        double out = Math.min(1.0, (DURATION_TICKS - ticks) / (double) FADE_TICKS);
        return Math.max(0.02, Math.min(in, out));
    }

    private void steer(Tornado t, Player caster) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (caster.getWorld().equals(t.world)) {
            double cx = caster.getLocation().getX() - t.pos.getX();
            double cz = caster.getLocation().getZ() - t.pos.getZ();
            if (cx * cx + cz * cz > LEASH * LEASH) {
                turnToward(t, Math.atan2(cz, cx));
                return;
            }
        }

        if (HOMING_RANGE > 0 && t.ticks % RETARGET_INTERVAL == 0) {
            t.chasing = nearestEnemy(t, caster);
        }
        Player chasing = t.chasing;
        if (chasing != null && chasing.isOnline() && chasing.getWorld().equals(t.world)) {
            double dx = chasing.getLocation().getX() - t.pos.getX();
            double dz = chasing.getLocation().getZ() - t.pos.getZ();
            turnToward(t, Math.atan2(dz, dx));
            return;
        }

        t.heading += (random.nextDouble() - 0.5) * 2.0 * WANDER_JITTER;
    }

    private Player nearestEnemy(Tornado t, Player caster) {
        Player best = null;
        double bestDistance = HOMING_RANGE * HOMING_RANGE;
        for (Player candidate : t.world.getNearbyPlayers(t.pos, HOMING_RANGE)) {
            if (!canAffect(t, caster, candidate)) continue;
            double dx = candidate.getLocation().getX() - t.pos.getX();
            double dz = candidate.getLocation().getZ() - t.pos.getZ();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static void turnToward(Tornado t, double wanted) {
        double diff = wanted - t.heading;
        while (diff > Math.PI) diff -= Math.PI * 2;
        while (diff < -Math.PI) diff += Math.PI * 2;
        t.heading += Math.max(-TURN_RATE, Math.min(TURN_RATE, diff));
    }

    private void move(Tornado t, double growth) {
        double speed = SPEED * Math.max(0.35, growth); // slow while still forming / fading
        double dx = Math.cos(t.heading) * speed;
        double dz = Math.sin(t.heading) * speed;
        double nx = t.pos.getX() + dx;
        double nz = t.pos.getZ() + dz;

        if (!t.world.isChunkLoaded(((int) Math.floor(nx)) >> 4, ((int) Math.floor(nz)) >> 4)) {
            bounce(t);
            return;
        }

        // Wall check above step height (so 1-block ledges are climbed, not bounced off), a little
        // further than the step so it turns before it clips in.
        Vector ahead = new Vector(dx, 0, dz).normalize();
        Location chest = t.pos.clone().add(0, 1.5, 0);
        if (t.world.rayTraceBlocks(chest, ahead, speed + 0.9, FluidCollisionMode.NEVER, true) != null) {
            bounce(t);
            return;
        }

        Double ground = groundY(t.world, nx, t.pos.getY(), nz, GROUND_SEARCH + 2.0);
        if (ground == null || ground - t.pos.getY() > MAX_STEP_UP) {
            bounce(t); // void/pit with no floor, or a ledge too tall to climb
            return;
        }

        double dy = Math.max(-MAX_DROP_PER_TICK, Math.min(MAX_DROP_PER_TICK, ground - t.pos.getY()));
        t.pos.add(dx, dy, dz);
    }

    private static void bounce(Tornado t) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double turn = Math.PI / 2 + random.nextDouble() * 0.8;
        t.heading += random.nextBoolean() ? turn : -turn;
    }

    private static Double groundY(World world, double x, double fromY, double z, double depth) {
        Location from = new Location(world, x, fromY + 2.0, z);
        RayTraceResult hit = world.rayTraceBlocks(
                from, new Vector(0, -1, 0), depth + 2.0, FluidCollisionMode.ALWAYS, true);
        if (hit == null || hit.getHitPosition() == null) return null;
        return hit.getHitPosition().getY();
    }

    private void suck(Tornado t, Player caster, double growth) {
        Location middle = t.pos.clone().add(0, TornadoVisual.HEIGHT / 2.0, 0);
        double reach = TornadoVisual.HEIGHT / 2.0 + 2.0;

        for (LivingEntity target : t.world.getNearbyLivingEntities(middle, PULL_RADIUS, reach, PULL_RADIUS)) {
            if (!canAffect(t, caster, target)) continue;

            Location at = target.getLocation();
            double dx = t.pos.getX() - at.getX();
            double dz = t.pos.getZ() - at.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > PULL_RADIUS) continue;

            double height = at.getY() - t.pos.getY(); // how far above the tornado's base they are
            if (height < -1.5 || height > TornadoVisual.HEIGHT * growth + 2.0) continue;

            // Unit vector toward the axis, and the one at 90 degrees to it (the direction to swirl in).
            Vector inward = distance > 1.0E-4 ? new Vector(dx / distance, 0, dz / distance) : new Vector();
            Vector swirl = new Vector(-inward.getZ(), 0, inward.getX());

            double funnel = TornadoVisual.radiusAt(Math.max(0.0, height) / TornadoVisual.HEIGHT) * growth;
            boolean inside = distance <= funnel + INSIDE_MARGIN
                    && height <= TornadoVisual.HEIGHT * growth;

            if (inside) {
                caught(t, caster, target, inward, swirl, distance, funnel, height);
            } else {
                pull(target, inward, swirl, distance, growth);
            }
        }
    }

    private void pull(LivingEntity target, Vector inward, Vector swirl, double distance, double growth) {
        double closeness = 1.0 - Math.min(1.0, distance / PULL_RADIUS);
        double speed = (PULL_SPEED_FAR + (PULL_SPEED_NEAR - PULL_SPEED_FAR) * closeness) * growth;

        // Keep a little of their own momentum so it feels like a current, not a rail.
        Vector velocity = target.getVelocity().multiply(0.5)
                .add(inward.clone().multiply(speed))
                .add(swirl.clone().multiply(PULL_SWIRL * closeness * growth));
        if (velocity.length() > MAX_SPEED) {
            velocity.normalize().multiply(MAX_SPEED);
        }
        // Lift off the floor, otherwise ground friction stalls the pull.
        if (target.isOnGround() && velocity.getY() < 0.12) {
            velocity.setY(0.12);
        }
        target.setVelocity(velocity);
    }

    private void caught(Tornado t, Player caster, LivingEntity target, Vector inward, Vector swirl,
                        double distance, double funnel, double height) {
        UUID id = target.getUniqueId();

        if (t.ticks >= t.nextHit.getOrDefault(id, 0)) {
            t.nextHit.put(id, t.ticks + DAMAGE_INTERVAL_TICKS);
            TrueDamage.of(DAMAGE).attacker(caster).ability(getName()).ignoreIFrames(true).apply(target);

            Location chest = target.getLocation().add(0, 1.0, 0);
            t.world.playSound(chest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.2f);
        }

        // Orbit around the axis, drifting toward a ring about 60% of the way out to the funnel wall
        // (pushes them back out if they sit right on the axis, so they circle instead of stacking up).
        double ringRadius = Math.max(0.6, funnel * 0.6);
        double radial = (distance - ringRadius) * 0.3;
        Vector horizontal = swirl.clone().multiply(ORBIT_SPEED).add(inward.clone().multiply(radial));

        // Up to LIFT_MAX, then hover. The hover bobs up AND down on purpose: vanilla kicks survival
        // players for "flying" after ~4s of never moving downward, and the dips reset that timer.
        double vertical;
        if (height < LIFT_MAX - 0.5) {
            vertical = LIFT_SPEED;
        } else {
            double bob = Math.sin(t.ticks * 0.2 + (id.hashCode() & 0xFF)) * 0.12;
            vertical = bob + (LIFT_MAX - height) * 0.1;
        }

        // Set after the damage: its knockback would otherwise shove them out of the funnel.
        target.setVelocity(new Vector(horizontal.getX(), vertical, horizontal.getZ()));
        target.setFallDistance(0f); // being carried up isn't falling
    }

    private boolean canAffect(Tornado t, Player caster, LivingEntity target) {
        // The caster is never touched, matched by UUID so a stale/re-created Player object can't slip through.
        if (target.getUniqueId().equals(caster.getUniqueId())) return false;
        if (!target.isValid() || target.isDead()) return false;
        if (target instanceof ArmorStand) return false;

        // Anything the caster is riding or carrying (horse, pig, a mob on their shoulders...) would drag
        // the caster along with it, so it is off limits too.
        if (isCasterAttached(caster, target)) return false;

        // Caster-owned projectiles (arrows, wind charges, tridents...) are not living entities and are
        // never scanned, but never treat one as a target even if one is ever passed in.
        if (target instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && (shooter.getUniqueId().equals(caster.getUniqueId()) || isTrusted(t, caster, shooter.getUniqueId()))) {
            return false;
        }

        if (target instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
            return !isTrusted(t, caster, player.getUniqueId());
        }

        // Mobs are fair game, but not mounts carrying the caster / a trusted player, and not the
        // caster's (or their trusted friends') tamed pets.
        for (Entity passenger : target.getPassengers()) {
            if (passenger instanceof Player rider
                    && (rider.getUniqueId().equals(caster.getUniqueId()) || isTrusted(t, caster, rider.getUniqueId()))) {
                return false;
            }
        }
        if (target instanceof Tameable pet && pet.getOwnerUniqueId() != null) {
            UUID owner = pet.getOwnerUniqueId();
            return !owner.equals(caster.getUniqueId()) && !isTrusted(t, caster, owner);
        }
        return true;
    }

    private static boolean isCasterAttached(Player caster, LivingEntity target) {
        Entity vehicle = caster.getVehicle();
        if (vehicle != null && vehicle.getUniqueId().equals(target.getUniqueId())) return true;
        for (Entity passenger : caster.getPassengers()) {
            if (passenger.getUniqueId().equals(target.getUniqueId())) return true;
        }
        Entity targetVehicle = target.getVehicle();
        return targetVehicle != null && targetVehicle.getUniqueId().equals(caster.getUniqueId());
    }

    private boolean isTrusted(Tornado t, Player caster, UUID other) {
        return t.trust != null && t.trust.isTrusted(caster.getUniqueId(), other);
    }

    private void end(UUID casterId, boolean collapsed) {
        Tornado tornado = tornadoes.remove(casterId);
        if (tornado == null) return;

        if (tornado.task != null) tornado.task.cancel();
        tornado.visual.remove();

        // Natural collapse: fade the wind out. Anything else (recast, death, element change, quit):
        // cut it off straight away so it can't bleed into a new tornado or linger.
        if (collapsed && plugin.isEnabled()) {
            fadeWind(tornado);
        } else {
            stopWind(tornado);
        }

        Player caster = plugin.getServer().getPlayer(casterId);
        if (caster != null) super.setActive(caster, false);

        if (collapsed) {
            World world = tornado.world;
            Location at = tornado.pos.clone().add(0, 1.0, 0);
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 0.7f);
        }
    }

    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player.getUniqueId(), false);
        }
    }

    @Override
    public String getName() {
        return ChatColor.YELLOW + "Tornado";
    }

    @Override
    public String getDescription() {
        return "Summons a tornado that pulls in nearby players and mobs, lifts them and damages them.";
    }
}