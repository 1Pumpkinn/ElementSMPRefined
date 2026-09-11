package hs.elementSMPRefined.ability.main.earth;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Grasp - Grab an entity and squeeze them, dealing suffocation damage
 * while carrying them in front of you for a short duration.
 *
 * <p>The target is held in front of the caster's eyes and teleported there
 * every tick, following as the caster walks and turns. Players additionally
 * have their look direction frozen at the moment of the grab (via a
 * {@link PlayerMoveEvent} lock) so they can't fight the hold with camera
 * movement; mobs have their AI disabled for the same reason. Either way the
 * target takes suffocation damage every tick for the duration.</p>
 *
 * <p>While held, the target is visually gripped by a chunky stone fist built
 * from {@link #FIST_PIECES} block displays — the same technique used by
 * ElementSfive's Stone Hand ability: pieces positioned relative to the
 * caster's live forward/right facing (so the fist turns as they turn),
 * overlapping enough with a random tumble rotation per piece to read as one
 * clustered mass of rock instead of separate floating cubes.</p>
 */
public class GraspAbility extends BaseAbility implements Listener {

    private static final int HOLD_TICKS = 60; // 3 s
    private static final double DAMAGE_PER_TICK = 1.0; // 20 damage over 3 s
    private static final double HOLD_DISTANCE = 1.6;
    private static final double GRAB_RANGE = 15.0;

    /** How smoothly the fist pieces glide to their new spot each tick. */
    private static final int ENCASE_TELEPORT_DURATION = 2;

    /**
     * Chunky stone pieces that make up the gripping fist around the grasped
     * target, expressed relative to the caster's live forward/right facing so
     * the fist turns with them. {@code f} is forward distance (negative =
     * toward the caster, i.e. wrapping over the front), {@code r} is sideways
     * distance, {@code u} is a fraction of the target's height. Deliberately
     * leaves the front-centre open so the grasped target stays visible.
     *
     * <p>Offsets are tight enough that neighbouring pieces overlap — combined
     * with a random tumble rotation given to each piece at spawn time (see
     * {@link #randomTumble()}), that keeps it reading as one clustered mass
     * of rock instead of separate floating cubes. Ported as-is from
     * ElementSfive's StoneHand ability.</p>
     */
    private static final List<FistPiece> FIST_PIECES = List.of(
            new FistPiece(0.20, -0.05, 0.0, 0.85f, Material.STONE),          // back of hand
            new FistPiece(-0.12, 0.20, 0.0, 0.6f, Material.COBBLESTONE),     // top finger, curling over
            new FistPiece(-0.06, 0.02, -0.26, 0.6f, Material.ANDESITE),      // left finger
            new FistPiece(-0.06, 0.02, 0.26, 0.6f, Material.COBBLESTONE),    // right finger
            new FistPiece(-0.04, -0.20, -0.12, 0.5f, Material.STONE));       // thumb

    private final ElementSMPRefined plugin;

    /** Caster UUID -> the session for whoever they're currently grasping. */
    private final Map<UUID, GraspSession> activeGrasps = new HashMap<>();
    /** Grasped target UUID -> the session holding them, for the move-lock handler and quit cleanup. */
    private final Map<UUID, GraspSession> grasped = new HashMap<>();

    public GraspAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("earth_grasp", ElementType.EARTH, 2, 12, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        ManaManager mana = context.getManaManager();
        TrustManager trust = context.getTrustManager();
        int cost = getManaCost();

        if (!mana.hasMana(player, cost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana (" + cost + ")");
            return false;
        }

        if (activeGrasps.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already grasping an entity!");
            return false;
        }

        LivingEntity target = getTargetEntity(player, trust);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No valid target in range!");
            return false;
        }

        if (grasped.containsKey(target.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "That target is already grasped!");
            return false;
        }

        startGrasp(player, target);
        return true;
    }

    private LivingEntity getTargetEntity(Player player, TrustManager trust) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        LivingEntity closestTarget = null;
        double closestDistance = GRAB_RANGE;

        // Bounded to GRAB_RANGE via Paper's spatial-index nearby query instead of
        // scanning every living entity in the loaded world (getWorld().getLivingEntities()).
        for (LivingEntity entity : eyeLoc.getNearbyLivingEntities(GRAB_RANGE)) {
            if (entity.equals(player)) continue;
            if (entity instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

            Vector toEntity = entity.getLocation().toVector().subtract(eyeLoc.toVector());
            double distance = toEntity.length();
            if (distance > GRAB_RANGE) continue;

            toEntity.normalize();
            double dotProduct = direction.dot(toEntity);

            if (dotProduct > 0.8 && distance < closestDistance) {
                closestDistance = distance;
                closestTarget = entity;
            }
        }

        return closestTarget;
    }

    private void startGrasp(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        World world = player.getWorld();

        world.playSound(target.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.8f, 1.2f);

        GraspSession session = new GraspSession(playerId, target.getLocation().getYaw(), target.getLocation().getPitch());
        grasped.put(target.getUniqueId(), session);
        activeGrasps.put(playerId, session);

        target.setVelocity(new Vector(0, 0, 0));
        target.setFallDistance(0f);

        // Mobs will otherwise keep trying to path/attack while held.
        if (target instanceof Mob mob) {
            session.wasAiEnabled = mob.hasAI();
            mob.setAI(false);
        }

        spawnEncasement(session, player, target);

        // Carry the target in front of the caster every tick, wherever they walk/turn.
        session.carryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player caster = Bukkit.getPlayer(session.casterId);
            if (caster == null || !caster.isOnline() || !target.isValid() || target.isDead()
                    || session.tick >= HOLD_TICKS) {
                endGrasp(caster, target);
                return;
            }

            Location hold = computeHoldLocation(caster, target);
            hold.setYaw(session.frozenYaw);
            hold.setPitch(session.frozenPitch);
            session.currentHold = hold;

            target.teleport(hold);
            target.setVelocity(new Vector(0, 0, 0));
            target.setFallDistance(0f);
            target.damage(DAMAGE_PER_TICK, caster);

            double height = target.getBoundingBox().getHeight();
            Location center = hold.clone().add(0, height / 2.0, 0);
            Vector[] basis = casterHorizontalBasis(caster);
            for (int i = 0; i < session.encasement.size(); i++) {
                session.encasement.get(i).teleport(fistPiecePosition(center, basis, FIST_PIECES.get(i), height));
            }

            spawnGraspVisuals(world, hold.clone().add(0, 1, 0), session.tick);

            if (session.tick % 10 == 0) {
                world.playSound(hold, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.5f);
            }

            session.tick++;
        }, 0L, 1L);
    }

    /** The world position the grasped target should be held at, in front of the caster's eyes. */
    private Location computeHoldLocation(Player caster, LivingEntity target) {
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Location hold = eye.clone().add(forward.multiply(HOLD_DISTANCE));

        double height = target.getBoundingBox().getHeight();
        hold.subtract(0, height / 2.0, 0);
        return hold;
    }

    private void spawnGraspVisuals(World world, Location center, int tick) {
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI * i) / 8;
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            double y = Math.sin(tick * 0.2) * 0.5;

            Location particleLoc = center.clone().add(x, y, z);
            world.spawnParticle(Particle.BLOCK, particleLoc, 2, 0.1, 0.1, 0.1, 0.0, Material.DIRT.createBlockData(), true);
            world.spawnParticle(Particle.BLOCK, particleLoc, 1, 0.1, 0.1, 0.1, 0.0, Material.COBBLESTONE.createBlockData(), true);
        }

        for (int i = 0; i < 4; i++) {
            double angle = (2 * Math.PI * i) / 4 + (tick * 0.1);
            double x = Math.cos(angle) * 0.8;
            double z = Math.sin(angle) * 0.8;
            Location particleLoc = center.clone().add(x, 0, z);
            world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns the stone fist (BlockDisplays) gripping the target's body, laid
     * out relative to the caster's current facing. Each piece gets a random
     * tumble rotation, picked once here and left alone for the rest of the
     * grasp, so the cluster doesn't read as neatly grid-aligned cubes.
     */
    private void spawnEncasement(GraspSession session, Player caster, LivingEntity target) {
        double height = target.getBoundingBox().getHeight();
        Location center = target.getLocation().add(0, height / 2.0, 0);
        Vector[] basis = casterHorizontalBasis(caster);

        for (FistPiece piece : FIST_PIECES) {
            Location pos = fistPiecePosition(center, basis, piece, height);
            Transformation transform = centeredTransform(piece.scale(), randomTumble());
            BlockDisplay shard = target.getWorld().spawn(pos, BlockDisplay.class, d -> {
                d.setBlock(piece.material().createBlockData());
                d.setTeleportDuration(ENCASE_TELEPORT_DURATION);
                d.setTransformation(transform);
            });
            session.encasement.add(shard);
        }
    }

    /** A random small-to-moderate rotation around a random axis, for an irregular tumbled-rock look. */
    private static AxisAngle4f randomTumble() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float angle = (float) Math.toRadians(15 + r.nextDouble() * 40);
        float theta = (float) (r.nextDouble() * Math.PI * 2);
        float z = (float) (r.nextDouble() * 2 - 1);
        float s = (float) Math.sqrt(Math.max(0, 1 - z * z));
        return new AxisAngle4f(angle, (float) (s * Math.cos(theta)), (float) (s * Math.sin(theta)), z);
    }

    /** Caster's live horizontal forward/right vectors, used to orient the fist as they turn. */
    private Vector[] casterHorizontalBasis(Player caster) {
        Vector forward = caster.getEyeLocation().getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() < 1.0e-4) forward = new Vector(0, 0, 1);
        else forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        return new Vector[]{forward, right};
    }

    /** World position of a fist piece, given the body centre and the caster's current facing. */
    private Location fistPiecePosition(Location center, Vector[] basis, FistPiece piece, double height) {
        return center.clone()
                .add(basis[0].clone().multiply(piece.f()))
                .add(basis[1].clone().multiply(piece.r()))
                .add(0, piece.u() * height, 0);
    }

    private static Transformation centeredTransform(float scale, AxisAngle4f rotation) {
        float half = scale / 2f;
        return new Transformation(
                new Vector3f(-half, -half, -half),
                rotation,
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1));
    }

    /** Removes the stone fist pieces when a grasp ends, however it ends. */
    private void removeEncasement(List<BlockDisplay> encasement) {
        if (encasement == null) return;
        for (BlockDisplay display : encasement) {
            if (display != null && display.isValid()) display.remove();
        }
        encasement.clear();
    }

    /**
     * Camera lock for players — snaps every attempted look/move (since
     * {@link PlayerMoveEvent} fires on rotation-only updates too) back to the
     * current hold position. The per-tick carry task is what actually moves
     * them along with the caster; this just rejects the player's own input.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onMove(PlayerMoveEvent event) {
        GraspSession session = grasped.get(event.getPlayer().getUniqueId());
        if (session == null || session.currentHold == null) return;
        event.setTo(session.currentHold.clone());
    }

    /** Ends a grasp: cancels the carry task, restores mob AI, plays a release effect, and clears tracking. */
    private void endGrasp(Player caster, LivingEntity target) {
        GraspSession session = grasped.remove(target.getUniqueId());
        if (session == null) return;

        if (session.carryTask != null) session.carryTask.cancel();
        activeGrasps.remove(session.casterId);
        removeEncasement(session.encasement);

        if (target instanceof Mob mob && target.isValid()) {
            mob.setAI(session.wasAiEnabled);
        }

        if (target.isValid()) {
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GRAVEL_FALL, 0.8f, 1.0f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        // Caster disconnected — end the grasp they were holding.
        GraspSession asCaster = activeGrasps.get(id);
        if (asCaster != null) {
            // Find and release whichever target this caster's session belongs to.
            grasped.entrySet().removeIf(entry -> {
                if (entry.getValue() != asCaster) return false;
                if (asCaster.carryTask != null) asCaster.carryTask.cancel();
                removeEncasement(asCaster.encasement);
                Object targetEntity = Bukkit.getEntity(entry.getKey());
                if (targetEntity instanceof Mob mob && mob.isValid()) {
                    mob.setAI(asCaster.wasAiEnabled);
                }
                return true;
            });
            activeGrasps.remove(id);
        }

        // Grasped target disconnected — end the session from the caster's side too.
        GraspSession asTarget = grasped.remove(id);
        if (asTarget != null) {
            if (asTarget.carryTask != null) asTarget.carryTask.cancel();
            removeEncasement(asTarget.encasement);
            activeGrasps.remove(asTarget.casterId);
        }
    }

    @Override
    public String getName() {
        return ChatColor.YELLOW + "Grasp";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Grab an entity with earthen hands, carrying them before you and squeezing them for suffocation damage.";
    }

    /** Per-grasp state. */
    private static final class GraspSession {
        final UUID casterId;
        final float frozenYaw;
        final float frozenPitch;
        /** Updated every tick by the carry task; used by the move-lock handler. */
        Location currentHold;
        BukkitTask carryTask;
        boolean wasAiEnabled = true;
        int tick = 0;
        /** Stone fist pieces gripping the target; kept glued to them every tick. */
        final List<BlockDisplay> encasement = new ArrayList<>();

        GraspSession(UUID casterId, float frozenYaw, float frozenPitch) {
            this.casterId = casterId;
            this.frozenYaw = frozenYaw;
            this.frozenPitch = frozenPitch;
        }
    }

    /**
     * One chunky stone piece of the gripping fist. {@code f}/{@code r} are
     * forward/right distances from the body centre using the caster's live
     * facing (negative f = toward the caster, wrapping over the front);
     * {@code u} is a fraction of the target's height.
     */
    private record FistPiece(double f, double u, double r, float scale, Material material) {
    }
}