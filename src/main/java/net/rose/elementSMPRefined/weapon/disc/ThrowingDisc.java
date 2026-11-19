package net.rose.elementSMPRefined.weapon.disc;

import net.rose.elementSMPRefined.item.ItemKeys;
import net.rose.elementSMPRefined.item.builder.ItemBuilder;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Building and recognising the throwing disc item.
 * <p>
 * The disc is a hoe (diamond, later netherite) carrying the {@code elementsmp:throwing_disc} data
 * tag and a custom {@code item_model}. There is only ever one of them on the server, see
 * {@link DiscRegistry}.
 */
public final class ThrowingDisc {
    private ThrowingDisc() {}

    public static ItemStack make(JavaPlugin plugin, DiscTier tier) {
        return decorate(
                ItemBuilder.of(tier.material()).flag(ItemKeys.throwingDisc(plugin)), tier)
                .build();
    }

    /**
     * The netherite version of a diamond disc. Keeps the durability, enchantments and everything
     * else the diamond disc had, so the upgrade never creates a second disc.
     */
    public static ItemStack upgrade(JavaPlugin plugin, ItemStack diamondDisc) {
        ItemStack upgraded = diamondDisc.withType(DiscTier.NETHERITE.material());
        return decorate(ItemBuilder.from(upgraded), DiscTier.NETHERITE).build();
    }

    private static ItemBuilder decorate(ItemBuilder builder, DiscTier tier) {
        return builder.name(
                        net.kyori.adventure.text.Component.text(tier.displayName(), tier.colour()))
                .lore(
                        "&6&lOne of One",
                        "&7Only one exists on the whole server.",
                        "",
                        "&7Right-click to throw. It ricochets off walls",
                        "&7and flies back to you.",
                        "&7Damage: &c" + (int) tier.damage())
                .itemModel(tier.model())
                .flags(ItemFlag.HIDE_ATTRIBUTES);
    }

    public static boolean isDisc(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.isEmpty() || DiscTier.of(item.getType()) == null) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(ItemKeys.throwingDisc(plugin), PersistentDataType.BYTE);
    }

    /** The tier of a disc, or null if the stack is not the disc. */
    public static DiscTier tierOf(JavaPlugin plugin, ItemStack item) {
        return isDisc(plugin, item) ? DiscTier.of(item.getType()) : null;
    }
}
