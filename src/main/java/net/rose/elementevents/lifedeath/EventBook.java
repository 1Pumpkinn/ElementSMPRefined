package net.rose.elementevents.lifedeath;

import net.rose.elementSMPRefined.items.builder.ItemBuilder;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The two reward books of the Life &amp; Death event. Both are a plain {@link Material#BOOK}
 * wearing a resource-pack model ({@code elementsmp:life_book} / {@code elementsmp:death_book}) and
 * carry a persistent-data tag ({@link #KEY}) holding {@link Kind#id()}, which is how every
 * protection check in {@link EventBookProtection} recognises them.
 */
public final class EventBook {
    public static final String KEY = "event_book";

    private EventBook() {}

    public enum Kind {
        LIFE("life", "&a&lBook of Life", "Awarded to the top passive mob hunter."),
        DEATH("death", "&5&lBook of Death", "Awarded to the top hostile mob hunter.");

        private final String id;
        private final String name;
        private final String lore;

        Kind(String id, String name, String lore) {
            this.id = id;
            this.name = name;
            this.lore = lore;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return name;
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
                .lore(kind.lore, "&8Cannot be destroyed.")
                .itemModel(NamespacedKey.fromString("elementsmp:" + kind.id + "_book"))
                .data(key(plugin), kind.id)
                .edit(meta -> meta.setMaxStackSize(1))
                .build();
    }

    /** The kind of book {@code stack} is, or null if it isn't an event book. */
    public static @Nullable Kind kindOf(JavaPlugin plugin, @Nullable ItemStack stack) {
        if (stack == null || stack.getType() != Material.BOOK || !stack.hasItemMeta()) return null;
        String id =
                stack.getItemMeta()
                        .getPersistentDataContainer()
                        .get(key(plugin), PersistentDataType.STRING);
        return Kind.parse(id);
    }
}
