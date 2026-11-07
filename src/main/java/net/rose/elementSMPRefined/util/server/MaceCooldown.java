package net.rose.elementSMPRefined.util.server;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mace cooldown: the mace goes on a 15 second cooldown when it hits a player or a mob, and
 * while it is on cooldown its melee hits are cancelled (Bukkit's item cooldown on its own is
 * only a cosmetic swipe for melee, see {@code StatusEffectListener}). A mace that is already on
 * cooldown is never restarted.
 * <p>
 * The expiry is an absolute wall-clock timestamp stored in the player's persistent data
 * container, so it survives logging off, relogging and server restarts. It keeps counting down
 * while the player is offline. Only normal melee ({@code ENTITY_ATTACK}) counts, so ability and
 * true damage dealt by a player holding a mace neither triggers nor is blocked by the cooldown.
 * <p>
 * Mace melee hits are also capped at {@link Constants.Mace#MAX_DAMAGE_HEARTS} hearts of final
 * damage (after armor/resistance, counting absorption/golden hearts toward the cap), smash bonus included.
 * <p>
 * A smash hit (falling more than {@link #MIN_SMASH_FALL_DISTANCE} blocks, not gliding) scales with how
 * far the attacker has fallen: a mace's plain hit damage at the smallest smash, rising linearly to the
 * full cap at {@link #FULL_DAMAGE_FALL_DISTANCE} blocks. The vanilla smash bonus alone already passes the
 * cap at the smallest smash, so this is what makes height matter. The scaling only ever lowers a
 * hit, it never raises damage above what vanilla dealt.
 */
public class MaceCooldown implements Listener {

    private static final int COOLDOWN_SECONDS = 15;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;
    private static final int COOLDOWN_TICKS = COOLDOWN_SECONDS * 20;

    private static final long REAPPLY_DELAY_TICKS = 1L;

    private static final double MIN_SMASH_FALL_DISTANCE = 1.5;
    private static final double FULL_DAMAGE_FALL_DISTANCE = 25.0;
    private static final double DEFAULT_MACE_DAMAGE = 6.0;

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

    private static Player meleeAttacker(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return null;
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return null;
        if (ignoreDepth > 0) return null;
        return AbilityDamage.isAbilityDamage(event.getEntity()) ? null : player;
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
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) {
                                reapplyClientCooldown(player);
                            }
                        },
                        REAPPLY_DELAY_TICKS);
    }

    private static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player player = meleeAttacker(event);
        if (player == null) return;
        if (!isMace(player.getInventory().getItemInMainHand())) return;

        if (isOnCooldown(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Damage the hit deals in total: after armor/resistance, but BEFORE absorption (golden hearts) soaks
     * any of it up. {@link EntityDamageByEntityEvent#getFinalDamage()} already has absorption taken off,
     * so on its own it would let a victim with golden hearts take the cap on top of their absorption.
     */
    @SuppressWarnings("deprecation")
    private static double damageBeforeAbsorption(EntityDamageByEntityEvent event) {
        double total = event.getFinalDamage();
        if (event.isApplicable(DamageModifier.ABSORPTION)) {
            total -= event.getDamage(DamageModifier.ABSORPTION); // the modifier is negative
        }
        return total;
    }

    private static boolean isSmashing(Player player) {
        return player.getFallDistance() > MIN_SMASH_FALL_DISTANCE && !player.isGliding();
    }

    /** The attacker's plain mace hit damage, before any smash bonus, limited to the cap. */
    private static double plainHitDamage(Player player, double max) {
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        double plain = attack != null ? attack.getValue() : DEFAULT_MACE_DAMAGE;
        return Math.min(max, plain);
    }

    /**
     * The most final damage this hit may deal. A normal hit is only limited by the cap. A smash hit
     * scales from the plain hit damage up to the cap as the fall distance goes from the smash
     * minimum to {@link #FULL_DAMAGE_FALL_DISTANCE}.
     */
    private static double allowedDamage(Player player, double max) {
        if (!isSmashing(player)) {
            return max;
        }

        double plain = plainHitDamage(player, max);
        double span = FULL_DAMAGE_FALL_DISTANCE - MIN_SMASH_FALL_DISTANCE;
        double progress = (player.getFallDistance() - MIN_SMASH_FALL_DISTANCE) / span;
        progress = Math.max(0.0, Math.min(1.0, progress));
        return plain + (max - plain) * progress;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCapDamage(EntityDamageByEntityEvent event) {
        Player player = meleeAttacker(event);
        if (player == null) return;
        if (!isMace(player.getInventory().getItemInMainHand())) return;

        double allowed = allowedDamage(player, Constants.Mace.MAX_DAMAGE);
        double total = damageBeforeAbsorption(event);
        if (total <= allowed) return;

        // Armor reduction isn't perfectly linear in the base damage, so scale and re-check a few
        // times.
        // Setting the base damage also re-runs the absorption modifier, so the limit holds with
        // golden hearts.
        double base = event.getDamage();
        for (int i = 0; i < 5 && total > allowed + 1.0E-6; i++) {
            base *= allowed / total;
            event.setDamage(base);
            total = damageBeforeAbsorption(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player player = meleeAttacker(event);
        if (player == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand)
            return;
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