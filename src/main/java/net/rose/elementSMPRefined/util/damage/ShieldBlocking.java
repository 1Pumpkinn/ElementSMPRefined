package net.rose.elementSMPRefined.util.damage;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lets a normal, raised shield stop projectile-style abilities (Icicle Barrage, Magma Beam, Lightning
 * Spear, Air Cutter...). Ability damage is {@link TrueDamage}, which ignores vanilla shield blocking, so
 * abilities that want to be blockable pass their travel direction to
 * {@link TrueDamage.Builder#blockableByShield(Vector)} and this class makes the call.
 * <p>
 * A hit is blocked when the victim is actively blocking with a shield (vanilla's own 5-tick raise delay
 * applies, via {@link Player#isBlocking()}) and the hit comes from the front 180 degrees, same as vanilla.
 * A blocked hit does no damage and wears the shield down: unlike vanilla (which ignores hits under 3
 * damage) every blocked hit costs {@code 1 + floor(damage)} durability, so even weak or rapid hits like
 * the Magma Beam's pulses chew through it. Unbreaking is respected and unbreakable shields don't wear.
 */
public final class ShieldBlocking {

    private ShieldBlocking() {}

    /**
     * @param travelDirection the direction the attack is travelling (attacker -> victim)
     * @param damage          the damage the hit would have dealt, used for the durability cost
     * @return true if the shield stopped the hit (the caller must then skip the damage and any on-hit effects)
     */
    public static boolean tryBlock(Player victim, Vector travelDirection, double damage) {
        if (travelDirection == null || !victim.isBlocking()) return false;

        // Same arc as vanilla: the hit has to be coming at the side the player is facing.
        Vector flat = new Vector(travelDirection.getX(), 0, travelDirection.getZ());
        if (flat.lengthSquared() < 1.0E-6) return false;
        flat.normalize();
        if (flat.dot(victim.getLocation().getDirection()) >= 0.0) return false;

        EquipmentSlot slot = findShieldSlot(victim);
        if (slot == null) return false;

        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0)
                .add(flat.clone().multiply(-0.6)), 6, 0.15, 0.2, 0.15, 0.1);

        wearShield(victim, slot, 1 + (int) Math.floor(Math.max(0.0, damage)));
        return true;
    }

    /** The hand holding the shield that's currently raised, or null. */
    private static EquipmentSlot findShieldSlot(Player player) {
        EquipmentSlot active = player.getActiveItemHand();
        if (active != null && isShield(player.getInventory().getItem(active))) return active;
        if (isShield(player.getInventory().getItemInMainHand())) return EquipmentSlot.HAND;
        if (isShield(player.getInventory().getItemInOffHand())) return EquipmentSlot.OFF_HAND;
        return null;
    }

    private static boolean isShield(ItemStack item) {
        return item != null && item.getType() == Material.SHIELD;
    }

    /** Takes {@code points} durability off the shield (each point rolled against Unbreaking), breaking it if it runs out. */
    private static void wearShield(Player player, EquipmentSlot slot, int points) {
        ItemStack shield = player.getInventory().getItem(slot);
        ItemMeta meta = shield.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) return;

        int unbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int wear = 0;
        for (int i = 0; i < points; i++) {
            if (unbreaking <= 0 || random.nextInt(unbreaking + 1) == 0) wear++;
        }
        if (wear == 0) return;

        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : shield.getType().getMaxDurability();
        int next = damageable.getDamage() + wear;

        if (next >= max) {
            player.getInventory().setItem(slot, null);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ITEM, player.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.1, shield);
            return;
        }

        damageable.setDamage(next);
        shield.setItemMeta(damageable);

        // The inventory normally hands back the live stack, but make sure the wear sticks if it was a copy.
        ItemStack held = player.getInventory().getItem(slot);
        if (held != null && held.getItemMeta() instanceof Damageable now && now.getDamage() != next) {
            player.getInventory().setItem(slot, shield);
        }
    }
}
