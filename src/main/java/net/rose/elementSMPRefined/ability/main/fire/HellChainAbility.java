package net.rose.elementSMPRefined.ability.main.fire;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Fire ability1: Hell Chain.
 * <p>
 * A fire-themed grapple hook - fires a chain at whatever's directly ahead,
 * block or entity, and reels the caster in toward it at speed, trailing
 * flame the whole way. Unlike {@code MetalChainAbility} (which pulls the
 * target to the caster), the caster is always the one who moves here. If the
 * chain latches onto an enemy instead of terrain, they take a scorch of
 * damage the moment the caster arrives instead of getting dragged themselves.
 * <p>
 * Replaces the old Fire ability1, {@code FireGeyserAbility} (now deprecated).
 */
public class HellChainAbility extends BaseAbility {
    private static final double RANGE = 30.0;
    private static final double RAY_SIZE = 0.4;
    private static final double PULL_STRENGTH = 1.4;
    private static final double MAX_SPEED = 2.2;
    private static final double STOP_DISTANCE = 2.0;
    private static final int MAX_TICKS = 40; // 2s safety cap so a chain can never get "stuck" running forever
    private static final double ENTITY_HIT_DAMAGE = 4.0; // 2 hearts, dealt on arrival, not on hook

    private final ElementSMPRefined plugin;

    public HellChainAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("fire_hell_chain", ElementType.FIRE, 1, 30, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        RayTraceResult result = player.getWorld().rayTrace(
                eyeLoc, direction, RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );

        if (result == null) {
            player.sendMessage(Lang.FIRE_HELL_CHAIN_NO_VALID_HOOK_POINT);
            return false;
        }

        LivingEntity hookedEntity = result.getHitEntity() instanceof LivingEntity le ? le : null;

        // Don't let this be used to yank yourself onto a trusted player
        if (hookedEntity instanceof Player hookedPlayer
                && context.getTrustManager().isTrusted(player.getUniqueId(), hookedPlayer.getUniqueId())) {
            player.sendMessage(Lang.FIRE_HELL_CHAIN_NO_VALID_HOOK_POINT);
            return false;
        }

        // Snapshot the hook point in space - if it's a moving entity we still pull
        // toward its live location each tick further down, this is just the initial hit.
        Location hookLocation = result.getHitPosition().toLocation(player.getWorld());

        SoundUtils.playTo(player, SoundUtils.Element.FIRE);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= MAX_TICKS) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getEyeLocation();
                Location targetLoc = (hookedEntity != null && hookedEntity.isValid())
                        ? hookedEntity.getLocation()
                        : hookLocation;
                double distance = currentLoc.distance(targetLoc);

                if (distance < STOP_DISTANCE) {
                    if (hookedEntity != null && hookedEntity.isValid()) {
                        hookedEntity.damage(ENTITY_HIT_DAMAGE, player);
                        player.getWorld().spawnParticle(Particle.LAVA, hookedEntity.getLocation(),
                                15, 0.3, 0.3, 0.3, 0.05);
                    }
                    // Kill most of the built-up momentum on arrival rather than launching
                    // the caster straight through/past whatever they just hooked onto.
                    player.setVelocity(player.getVelocity().multiply(0.2));
                    player.getWorld().playSound(targetLoc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    cancel();
                    return;
                }

                // Draw the fire chain from the player to the hook point
                Vector toTarget = targetLoc.toVector().subtract(currentLoc.toVector()).normalize();
                double spacing = 0.4;
                int particleCount = (int) (distance / spacing);
                for (int i = 0; i <= particleCount; i++) {
                    Location particleLoc = currentLoc.clone().add(toTarget.clone().multiply(i * spacing));
                    player.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0.02, 0.02, 0.02, 0.0);
                    if (i % 4 == 0) {
                        player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }

                // Reel the caster in - this pulls the player, never the target
                Vector pull = toTarget.clone().multiply(PULL_STRENGTH);
                Vector newVelocity = player.getVelocity().add(pull);
                if (newVelocity.length() > MAX_SPEED) {
                    newVelocity = newVelocity.normalize().multiply(MAX_SPEED);
                }
                player.setVelocity(newVelocity);
                player.setFallDistance(0f);

                if (ticks % 4 == 0) {
                    player.getWorld().playSound(currentLoc, Sound.BLOCK_CHAIN_STEP, 0.6f, 0.7f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Hell's Chain";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Launch a fiery chain at whatever you're looking at and reel yourself in - "
                + "scorches enemies you hook onto instead of dragging them.";
    }
}