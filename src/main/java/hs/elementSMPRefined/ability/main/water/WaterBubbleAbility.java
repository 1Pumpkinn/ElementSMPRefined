package hs.elementSMPRefined.ability.main.water;

import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water element's defensive ability: encases the caster in a bubble that blocks all
 * incoming damage. The bubble pops - and protection ends - the moment it has absorbed
 * 3 critical hits from a player, or after 10 seconds, whichever comes first.
 * <p>
 * Implements {@link Listener} and self-registers (see {@code MetalDashAbility} for the
 * same pattern) since it needs to intercept damage events directed at the shielded player.
 */
public class WaterBubbleAbility extends BaseAbility implements Listener {

    private static final int MAX_DURATION_TICKS = 200; // 10 seconds
    private static final int MAX_CRIT_HITS = 3;
    private static final int RENDER_INTERVAL_TICKS = 2;

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeUsers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BubbleState> bubbles = new ConcurrentHashMap<>();

    public WaterBubbleAbility(JavaPlugin plugin) {
        super("water_bubble", 50, 15, 1);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        if (isActiveFor(player)) {
            player.sendMessage(ChatColor.RED + "Your water bubble is already active!");
            return false;
        }

        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.BUBBLE_POP, player.getLocation().add(0, 1, 0), 25,
                0.5, 0.6, 0.5, 0.05, null, true);

        setActive(player, true);

        BubbleState state = new BubbleState();
        bubbles.put(player.getUniqueId(), state);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isActiveFor(player)) {
                    cancel();
                    return;
                }

                if (ticks >= MAX_DURATION_TICKS) {
                    popBubble(player, false);
                    cancel();
                    return;
                }

                renderBubble(player);
                ticks += RENDER_INTERVAL_TICKS;
            }
        };
        state.task = task.runTaskTimer(plugin, 0L, RENDER_INTERVAL_TICKS);

        return true;
    }

    private void renderBubble(Player player) {
        Location center = player.getLocation().add(0, 1.0, 0);
        double radius = 1.1;
        int points = 14;

        // Evenly distribute points on a sphere (golden-angle spiral) for a clean shell look
        for (int i = 0; i < points; i++) {
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / points);
            double theta = Math.PI * (1 + Math.sqrt(5)) * i;

            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.cos(phi);
            double z = radius * Math.sin(phi) * Math.sin(theta);

            Location point = center.clone().add(x, y, z);
            player.getWorld().spawnParticle(Particle.BUBBLE_POP, point, 1, 0, 0, 0, 0, null, true);
        }
        player.getWorld().spawnParticle(Particle.SPLASH, center, 2, 0.4, 0.4, 0.4, 0.01, null, true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isActiveFor(player)) return;
        // Let the void kill through so the bubble can't be used to cheese an out-of-bounds fall
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;

        event.setCancelled(true);

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        if (!(byEntity.getDamager() instanceof Player attacker)) return;
        if (!isCriticalHit(attacker)) return;

        BubbleState state = bubbles.get(player.getUniqueId());
        if (state == null) return;

        state.critHits++;
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 12,
                0.4, 0.5, 0.4, 0.1, null, true);
        player.sendActionBar(ChatColor.AQUA + "Bubble cracked! (" + state.critHits + "/" + MAX_CRIT_HITS + ")");

        if (state.critHits >= MAX_CRIT_HITS) {
            popBubble(player, true);
        }
    }

    /**
     * Vanilla-equivalent critical hit check: the attacker must be falling, airborne,
     * not sprinting, not swimming/climbing, blindness-free, and not riding anything.
     */
    private boolean isCriticalHit(Player attacker) {
        org.bukkit.Material standingIn = attacker.getLocation().getBlock().getType();
        boolean onClimbable = standingIn == org.bukkit.Material.LADDER
                || standingIn == org.bukkit.Material.VINE
                || standingIn == org.bukkit.Material.SCAFFOLDING;

        return attacker.getFallDistance() > 0.0F
                && !attacker.isOnGround()
                && !onClimbable
                && !attacker.isInWater()
                && !attacker.isSprinting()
                && !attacker.isInsideVehicle()
                && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    private void popBubble(Player player, boolean brokenByCrits) {
        setActive(player, false);
        BubbleState state = bubbles.remove(player.getUniqueId());
        if (state != null && state.task != null) {
            state.task.cancel();
        }

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 30, 0.6, 0.6, 0.6, 0.15, null, true);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_DROWN, 1.0f, brokenByCrits ? 1.4f : 0.8f);

        player.sendMessage(brokenByCrits
                ? ChatColor.AQUA + "Your water bubble shattered!"
                : ChatColor.AQUA + "Your water bubble faded away.");
    }

    @Override
    public boolean isActiveFor(Player player) {
        return activeUsers.contains(player.getUniqueId());
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            activeUsers.add(player.getUniqueId());
        } else {
            activeUsers.remove(player.getUniqueId());
        }
    }

    public void clearEffects(Player player) {
        if (isActiveFor(player)) {
            popBubble(player, false);
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        BubbleState state = bubbles.remove(playerUuid);
        if (state != null && state.task != null) {
            state.task.cancel();
        }
        activeUsers.remove(playerUuid);
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Water Bubble";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Shields you from all damage until it's critically hit 3 times or 10 seconds pass. (50 mana)";
    }

    private static class BubbleState {
        int critHits = 0;
        BukkitTask task;
    }
}