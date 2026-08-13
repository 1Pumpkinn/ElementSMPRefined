package hs.elementSMPRefined.elements.abilities.impl.earth;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.abilities.BaseAbility;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
 */
public class GraspAbility extends BaseAbility implements Listener {

    private static final int HOLD_TICKS = 60; // 3 s
    private static final double DAMAGE_PER_TICK = 1.0; // 20 damage over 3 s
    private static final double HOLD_DISTANCE = 1.6;
    private static final double GRAB_RANGE = 15.0;

    private final ElementSMPRefined plugin;

    /** Caster UUID -> the session for whoever they're currently grasping. */
    private final Map<UUID, GraspSession> activeGrasps = new HashMap<>();
    /** Grasped target UUID -> the session holding them, for the move-lock handler and quit cleanup. */
    private final Map<UUID, GraspSession> grasped = new HashMap<>();

    public GraspAbility(ElementSMPRefined plugin) {
        super("earth_grasp", 60, 12, 2);
        this.plugin = plugin;
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

        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
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

        world.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.8f);
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

        GraspSession(UUID casterId, float frozenYaw, float frozenPitch) {
            this.casterId = casterId;
            this.frozenYaw = frozenYaw;
            this.frozenPitch = frozenPitch;
        }
    }
}