package hs.elementSMPRefined.status;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the 3 disarm variants:
 * <ul>
 *     <li>{@link StatusEffectType#ABILITY_DISARM} - element abilities cannot be used.</li>
 *     <li>{@link StatusEffectType#WEAPON_DISARM} - a random weapon category the player
 *     actually owns (sword/axe/spear/mace) is locked; "spear" maps to {@link Material#TRIDENT}
 *     since vanilla has no dedicated spear item.</li>
 *     <li>{@link StatusEffectType#MAIN_HAND_DISARM} - whatever material is currently in the
 *     player's main hand is locked.</li>
 * </ul>
 * Separate from {@link StatusEffectManager} because these lock a specific item/material
 * rather than just running a timer, so blocking damage/use needs the material to check against.
 * <p>
 * All expiries are stored as absolute wall-clock timestamps ({@code System.currentTimeMillis()}),
 * never as a per-tick countdown. That means a disarm that is applied and then the player logs
 * out simply keeps counting down in real time - there's no task that needs to keep running for
 * it to expire, so there's no way for a player to come back to a disarm stuck at "still on
 * cooldown forever". The periodic sweep below only tidies up the maps; it is never the thing
 * that decides whether an effect is still active (every read re-checks the timestamp itself).
 */
public class DisarmManager {
    private final JavaPlugin plugin;

    private final Map<UUID, Long> abilityDisarmExpiry = new ConcurrentHashMap<>();
    private final Map<UUID, MaterialLock> weaponDisarm = new ConcurrentHashMap<>();
    private final Map<UUID, MaterialLock> mainHandDisarm = new ConcurrentHashMap<>();

    private BukkitTask sweepTask;

    public DisarmManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startSweep();
    }

    public enum WeaponCategory {
        SWORD, AXE, SPEAR, MACE
    }

    private record MaterialLock(Material material, long expiryMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiryMillis;
        }

        int remainingTicks() {
            return (int) Math.max(0, (expiryMillis - System.currentTimeMillis()) / 50L);
        }
    }

    // ------------------------------------------------------------------
    // Ability disarm
    // ------------------------------------------------------------------

    /**
     * Disarms the player's abilities for the given duration (ticks).
     */
    public void applyAbilityDisarm(Player player, int durationTicks) {
        long expiry = System.currentTimeMillis() + (durationTicks * 50L);
        abilityDisarmExpiry.merge(player.getUniqueId(), expiry, Math::max);
        player.sendActionBar(Component.text("Your abilities have been disarmed!", NamedTextColor.RED));
    }

    public boolean isAbilityDisarmed(Player player) {
        Long expiry = abilityDisarmExpiry.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            abilityDisarmExpiry.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void clearAbilityDisarm(Player player) {
        abilityDisarmExpiry.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Weapon disarm - picks a random weapon category the player owns
    // ------------------------------------------------------------------

    /**
     * Disarms a random weapon (sword/axe/spear/mace) the player currently owns.
     * If they only own one of those categories, that one always gets picked.
     *
     * @return the {@link Material} that got disarmed, or {@code null} if the player
     * doesn't currently own any sword/axe/spear/mace to disarm.
     */
    public Material applyWeaponDisarm(Player player, int durationTicks) {
        List<Material> owned = ownedWeaponMaterials(player);
        if (owned.isEmpty()) {
            return null;
        }

        Material chosen = owned.get(ThreadLocalRandom.current().nextInt(owned.size()));
        lockMaterial(weaponDisarm, player, chosen, durationTicks);
        player.sendActionBar(Component.text(
                "Your " + prettyName(chosen) + " has been disarmed!", NamedTextColor.RED));
        return chosen;
    }

    public boolean isWeaponDisarmed(Player player, Material material) {
        return isLocked(weaponDisarm, player, material);
    }

    /**
     * One representative material per weapon category the player owns anywhere
     * in their inventory (main hand, offhand, armor/storage slots all count).
     */
    private List<Material> ownedWeaponMaterials(Player player) {
        Map<WeaponCategory, Material> found = new EnumMap<>(WeaponCategory.class);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            WeaponCategory category = classify(stack.getType());
            if (category != null) {
                found.putIfAbsent(category, stack.getType());
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        WeaponCategory offHandCategory = classify(offHand.getType());
        if (offHandCategory != null) {
            found.putIfAbsent(offHandCategory, offHand.getType());
        }
        return new ArrayList<>(found.values());
    }

    /**
     * Classifies a material into a weapon category, or returns {@code null} if it
     * isn't one of sword/axe/spear/mace. Spear is mapped to {@link Material#TRIDENT}
     * as the closest vanilla equivalent - there's no dedicated spear item yet.
     */
    public static WeaponCategory classify(Material material) {
        if (material == Material.MACE) return WeaponCategory.MACE;
        if (material == Material.TRIDENT) return WeaponCategory.SPEAR;
        String name = material.name();
        if (name.endsWith("_SWORD")) return WeaponCategory.SWORD;
        if (name.endsWith("_AXE")) return WeaponCategory.AXE;
        return null;
    }

    // ------------------------------------------------------------------
    // Main hand disarm - locks whatever material is currently held
    // ------------------------------------------------------------------

    /**
     * Disarms whatever material is currently in the player's main hand.
     *
     * @return the disarmed {@link Material}, or {@code null} if the main hand was empty.
     */
    public Material applyMainHandDisarm(Player player, int durationTicks) {
        Material material = player.getInventory().getItemInMainHand().getType();
        if (material == Material.AIR) {
            return null;
        }

        lockMaterial(mainHandDisarm, player, material, durationTicks);
        player.sendActionBar(Component.text(
                "Your " + prettyName(material) + " has been disarmed!", NamedTextColor.RED));
        return material;
    }

    public boolean isMainHandDisarmed(Player player, Material material) {
        return isLocked(mainHandDisarm, player, material);
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private void lockMaterial(Map<UUID, MaterialLock> map, Player player, Material material, int durationTicks) {
        long expiry = System.currentTimeMillis() + (durationTicks * 50L);
        map.put(player.getUniqueId(), new MaterialLock(material, expiry));
        player.setCooldown(material, durationTicks);
    }

    private boolean isLocked(Map<UUID, MaterialLock> map, Player player, Material material) {
        MaterialLock lock = map.get(player.getUniqueId());
        if (lock == null) return false;
        if (lock.isExpired()) {
            map.remove(player.getUniqueId());
            return false;
        }
        return lock.material() == material;
    }

    private String prettyName(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
    }

    /**
     * Re-applies the client-side cooldown swipe for any weapon/main-hand disarm still
     * active for this player. Bukkit's item cooldown is session state and resets on
     * relog, but the underlying lock (checked against wall-clock time) was never lost -
     * this just makes the client show it again so the player isn't confused about why
     * the item silently fails to work.
     */
    public void reapplyCooldowns(Player player) {
        MaterialLock weapon = weaponDisarm.get(player.getUniqueId());
        if (weapon != null && !weapon.isExpired()) {
            player.setCooldown(weapon.material(), weapon.remainingTicks());
        }
        MaterialLock mainHand = mainHandDisarm.get(player.getUniqueId());
        if (mainHand != null && !mainHand.isExpired()) {
            player.setCooldown(mainHand.material(), mainHand.remainingTicks());
        }
    }

    /**
     * Drops all tracked disarms for a player. Not required for correctness (expiries are
     * wall-clock based and time out on their own), only used to free memory for players
     * who won't be back, e.g. on ban/kick cleanup.
     */
    public void clearAll(UUID uuid) {
        abilityDisarmExpiry.remove(uuid);
        weaponDisarm.remove(uuid);
        mainHandDisarm.remove(uuid);
    }

    private void startSweep() {
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            abilityDisarmExpiry.values().removeIf(expiry -> now >= expiry);
            weaponDisarm.values().removeIf(lock -> now >= lock.expiryMillis());
            mainHandDisarm.values().removeIf(lock -> now >= lock.expiryMillis());
        }, 20L, 20L); // once a second - pure housekeeping, see class javadoc
    }

    /**
     * Cancels the sweep task and drops all tracked state. Called on plugin disable.
     */
    public void cleanup() {
        if (sweepTask != null && !sweepTask.isCancelled()) {
            sweepTask.cancel();
        }
        sweepTask = null;
        abilityDisarmExpiry.clear();
        weaponDisarm.clear();
        mainHandDisarm.clear();
    }
}