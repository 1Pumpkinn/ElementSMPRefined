package hs.elementSMPRefined.API;

import hs.elementSMPRefined.ElementSMPRefined;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Builder pattern for creating elements with a clean, fluent API.
 * Makes adding new elements much easier and more maintainable.
 */
public class ElementBuilder {
    private final ElementSMPRefined plugin;
    private ElementType type;
    private String displayName;
    private String description;
    private ChatColor color = ChatColor.WHITE;
    private boolean isBasic = false;
    private final List<BiConsumer<Player, Integer>> passiveEffects = new ArrayList<>();
    private final List<BiConsumer<Player, Integer>> clearEffects = new ArrayList<>();
    private Ability ability1;
    private Ability ability2;
    private String ability1Name;
    private String ability1Description;
    private String ability2Name;
    private String ability2Description;

    public ElementBuilder(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    public ElementBuilder type(ElementType type) {
        this.type = type;
        return this;
    }

    public ElementBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public ElementBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ElementBuilder color(ChatColor color) {
        this.color = color;
        return this;
    }

    public ElementBuilder isBasic(boolean isBasic) {
        this.isBasic = isBasic;
        return this;
    }

    public ElementBuilder passiveEffect(PotionEffectType effectType, int amplifier) {
        passiveEffects.add((player, level) -> {
            int finalAmplifier = amplifier + (level > 0 ? level - 1 : 0);
            player.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, finalAmplifier, true, false));
        });
        return this;
    }

    public ElementBuilder passiveEffect(BiConsumer<Player, Integer> effect) {
        passiveEffects.add(effect);
        return this;
    }

    public ElementBuilder clearEffect(PotionEffectType effectType) {
        clearEffects.add((player, level) -> player.removePotionEffect(effectType));
        return this;
    }

    public ElementBuilder clearEffect(BiConsumer<Player, Integer> effect) {
        clearEffects.add(effect);
        return this;
    }

    public ElementBuilder ability1(Ability ability, String name, String description) {
        this.ability1 = ability;
        this.ability1Name = name;
        this.ability1Description = description;
        return this;
    }

    public ElementBuilder ability2(Ability ability, String name, String description) {
        this.ability2 = ability;
        this.ability2Name = name;
        this.ability2Description = description;
        return this;
    }

    public Element build() {
        if (type == null) {
            throw new IllegalStateException("Element type is required");
        }
        if (displayName == null) {
            throw new IllegalStateException("Display name is required");
        }
        if (description == null) {
            throw new IllegalStateException("Description is required");
        }

        return new BuiltElement(plugin, type, displayName, description, color, isBasic,
                passiveEffects, clearEffects, ability1, ability2,
                ability1Name, ability1Description, ability2Name, ability2Description);
    }

    /**
     * Internal implementation of Element built from the builder
     */
    private static class BuiltElement extends BaseElement {
        private final ElementType type;
        private final String displayName;
        private final String description;
        private final ChatColor color;
        private final boolean isBasic;
        private final List<BiConsumer<Player, Integer>> passiveEffects;
        private final List<BiConsumer<Player, Integer>> clearEffects;
        private final Ability ability1;
        private final Ability ability2;
        private final String ability1Name;
        private final String ability1Description;
        private final String ability2Name;
        private final String ability2Description;

        public BuiltElement(ElementSMPRefined plugin, ElementType type, String displayName,
                          String description, ChatColor color, boolean isBasic,
                          List<BiConsumer<Player, Integer>> passiveEffects,
                          List<BiConsumer<Player, Integer>> clearEffects,
                          Ability ability1, Ability ability2,
                          String ability1Name, String ability1Description,
                          String ability2Name, String ability2Description) {
            super(plugin);
            this.type = type;
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.isBasic = isBasic;
            this.passiveEffects = passiveEffects;
            this.clearEffects = clearEffects;
            this.ability1 = ability1;
            this.ability2 = ability2;
            this.ability1Name = ability1Name;
            this.ability1Description = ability1Description;
            this.ability2Name = ability2Name;
            this.ability2Description = ability2Description;
        }

        @Override
        public ElementType getType() {
            return type;
        }

        @Override
        public void applyUpsides(Player player, int upgradeLevel) {
            for (BiConsumer<Player, Integer> effect : passiveEffects) {
                effect.accept(player, upgradeLevel);
            }
        }

        @Override
        protected boolean executeAbility1(ElementContext context) {
            return ability1 != null && ability1.execute(context);
        }

        @Override
        protected boolean executeAbility2(ElementContext context) {
            return ability2 != null && ability2.execute(context);
        }

        @Override
        public void clearEffects(Player player) {
            for (BiConsumer<Player, Integer> effect : clearEffects) {
                effect.accept(player, 0);
            }
            if (ability1 != null) {
                ability1.setActive(player, false);
            }
            if (ability2 != null) {
                ability2.setActive(player, false);
            }
        }

        @Override
        public String getDisplayName() {
            return color + displayName;
        }

        @Override
        public String getDescription() {
            return ChatColor.GRAY + description;
        }

        @Override
        public String getAbility1Name() {
            return ability1Name != null ? ability1Name : "Ability 1";
        }

        @Override
        public String getAbility1Description() {
            return ability1Description != null ? ability1Description : "No description";
        }

        @Override
        public String getAbility2Name() {
            return ability2Name != null ? ability2Name : "Ability 2";
        }

        @Override
        public String getAbility2Description() {
            return ability2Description != null ? ability2Description : "No description";
        }

        @Override
        protected boolean canCancelAbility1(ElementContext context) {
            return ability1 != null && ability1.isActiveFor(context.getPlayer());
        }

        @Override
        protected boolean canCancelAbility2(ElementContext context) {
            return ability2 != null && ability2.isActiveFor(context.getPlayer());
        }
    }
}