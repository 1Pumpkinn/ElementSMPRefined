package net.rose.elementevents.mythic;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ItemBuilder;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The item every <b>mythic</b> element comes in. Right-clicking a book claims its element for the
 * player (see {@link MythicBookHandler}); only one player on the whole server can hold each mythic
 * element ({@link MythicElements}). How a book is obtained is up to each event - the Life &amp;
 * Death event hands them out as prizes.
 * <p>
 * A book is a plain {@link Material#BOOK} wearing a resource-pack model
 * ({@code elementsmp:<id>_book}) and carrying a persistent-data tag ({@link #KEY}) holding
 * {@link Kind#id()}. <b>To add a mythic element:</b> add a {@link Kind} here, a
 * {@code models/item/<id>_book.json} + {@code items/<id>_book.json} + texture in the pack, and
 * mark the element in {@link ElementType#isMythic()}.
 */
public final class MythicBook {
    public static final String KEY = "mythic_book";

    private MythicBook() {}

    public enum Kind {
        LIFE("life", ElementType.LIFE, "&a&lBook of Life", "Element of Life"),
        DEATH("death", ElementType.DEATH, "&5&lBook of Death", "Element of Death");

        private final String id;
        private final ElementType element;
        private final String name;
        private final String elementName;

        Kind(String id, ElementType element, String name, String elementName) {
            this.id = id;
            this.element = element;
            this.name = name;
            this.elementName = elementName;
        }

        public String id() {
            return id;
        }

        public ElementType element() {
            return element;
        }

        /** Plain-text name for chat, e.g. "Element of Life". */
        public String elementName() {
            return elementName;
        }

        public static @Nullable Kind parse(@Nullable String raw) {
            if (raw == null) return null;
            String text = raw.trim().toLowerCase(Locale.ROOT);
            for (Kind kind : values()) {
                if (kind.id.equals(text)) return kind;
            }
            return null;
        }
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static ItemStack make(JavaPlugin plugin, Kind kind) {
        return ItemBuilder.of(Material.BOOK)
                .name(kind.name)
                .lore(
                        "&7Right-click to claim the " + kind.elementName + ".",
                        "&7Only one exists on the whole server.",
                        "&8Cannot be destroyed.")
                .itemModel(NamespacedKey.fromString("elementsmp:" + kind.id + "_book"))
                .data(key(plugin), kind.id)
                .edit(meta -> meta.setMaxStackSize(1))
                .build();
    }

    /** The kind of book {@code stack} is, or null if it isn't a mythic book. */
    public static @Nullable Kind kindOf(JavaPlugin plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.BOOK || !stack.hasItemMeta()) return null;
        String id =
                stack.getItemMeta()
                        .getPersistentDataContainer()
                        .get(key(plugin), PersistentDataType.STRING);
        return Kind.parse(id);
    }

    /** The book that carries {@code element}, or null if it isn't a mythic element. */
    public static @Nullable Kind kindFor(@Nullable ElementType element) {
        if (element == null) return null;
        for (Kind kind : Kind.values()) {
            if (kind.element == element) return kind;
        }
        return null;
    }

    /**
     * Puts a book into the player's inventory; if it is full the book is dropped at their feet
     * instead (protected from destruction either way).
     *
     * @return true if it had to be dropped
     */
    public static boolean giveOrDrop(JavaPlugin plugin, Player player, Kind kind) {
        boolean dropped = false;
        for (ItemStack rest : player.getInventory().addItem(make(plugin, kind)).values()) {
            dropped = true;
            Location at = player.getLocation();
            at.getWorld()
                    .dropItem(
                            at,
                            rest,
                            (Item item) -> {
                                item.setVelocity(new Vector(0, 0, 0));
                                MythicBookProtection.protect(item);
                            });
        }
        return dropped;
    }
}
