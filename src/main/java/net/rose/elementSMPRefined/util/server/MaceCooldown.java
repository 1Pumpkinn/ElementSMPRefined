package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mace cooldown: the mace goes on a 15 second cooldown when it hits a player or a mob, and
 * while it is on cooldown its melee hits are cancelled (Bukkit's item cooldown on its own is
 * only a cosmetic swipe for melee, see {@code DisarmListener}). A mace that is already on
 * cooldown is never restarted.
 * <p>
 * The expiry is an absolute wall-clock timestamp stored in the player's persistent data
 * container, so it survives logging off, relogging and server restarts. It keeps counting down
 * while the player is offline. Only normal melee ({@code ENTITY_ATTACK}) counts, so ability and
 * true damage dealt by a player holding a mace neither triggers nor is blocked by the cooldown.
 * <p>
 * Mace melee hits are also capped at {@link Constants.Mace#MAX_DAMAGE_HEARTS} hearts of final
 * damage (after armor/resistance, before absorption), smash bonus included.
 */
public class MaceCooldown implements Listener {

    private static final int COOLDOWN_SECONDS = 15;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;
    private static final int COOLDOWN_TICKS = COOLDOWN_SECONDS * 20;

    private static final long REAPPLY_DELAY_TICKS = 1L;

    private static int ignoreDepth = 0;

    /**
     * Runs {@code damageCall} (a synchronous {@code damage()} call) without it counting as a mace
     * hit. For ability damage that isn't routed through {@link AbilityDamage} or
     * {@code TrueDamage} - Bukkit reports any damage with a player attacker as a normal melee
     * {@code ENTITY_ATTACK}, so without this it would start (or be blocked by) the mace cooldown.
     */
    public static void ignoreHits(Runnable damageCall) {
        ignoreDepth++;
        try {
            damageCall.run();
        } finally {
            ignoreDepth--;
        }
    }

    private static boolean isPlayerMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return false;
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return false;
        if (ignoreDepth > 0) return false;
        return !AbilityDamage.isAbilityDamage(event.getEntity());
    }

    private final JavaPlugin plugin;
    private final NamespacedKey expiryKey;

    public MaceCooldown(JavaPlugin plugin) {
        this.plugin = plugin;
        this.expiryKey = new NamespacedKey(plugin, "mace_cooldown_expiry");
    }

    public boolean isOnCooldown(Player player) {
        return remainingMillis(player) > 0;
    }

    /**
     * Starts the mace cooldown for the player. Does nothing if the mace is already on
     * cooldown, so a running cooldown is never restarted or extended.
     */
    public void start(Player player) {
        if (isOnCooldown(player)) {
            return;
        }

        long expiry = System.currentTimeMillis() + COOLDOWN_MILLIS;
        player.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, expiry);
        player.setCooldown(Material.MACE, COOLDOWN_TICKS);
    }

    private long remainingMillis(Player player) {
        Long expiry = player.getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        if (expiry == null) {
            return 0L;
        }

        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            player.getPersistentDataContainer().remove(expiryKey);
            return 0L;
        }
        return remaining;
    }

    private void reapplyClientCooldown(Player player) {
        long remaining = remainingMillis(player);
        if (remaining > 0) {
            player.setCooldown(Material.MACE, (int) Math.max(1, remaining / 50L));
        }
    }

    private void scheduleReapply(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                reapplyClientCooldown(player);
            }
        }, REAPPLY_DELAY_TICKS);
    }

    private static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!isPlayerMelee(event)) return;
        Player player = (Player) event.getDamager();
        if (!isMace(player.getInventory().getItemInMainHand())) return;

        if (isOnCooldown(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCapDamage(EntityDamageByEntityEvent event) {
        if (!isPlayerMelee(event)) return;
        Player player = (Player) event.getDamager();
        if (!isMace(player.getInventory().getItemInMainHand())) return;

        double max = Constants.Mace.MAX_DAMAGE;
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= max) return;

        // Armor reduction isn't perfectly linear in the base damage, so scale and re-check a few times.
        double base = event.getDamage();
        for (int i = 0; i < 5 && finalDamage > max + 1.0E-6; i++) {
            base *= max / finalDamage;
            event.setDamage(base);
            finalDamage = event.getFinalDamage();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!isPlayerMelee(event)) return;
        Player player = (Player) event.getDamager();
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) return;
        if (!isMace(player.getInventory().getItemInMainHand())) return;

        start(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleReapply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleReapply(event.getPlayer());
    }
}