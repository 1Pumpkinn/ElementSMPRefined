package hs.elementSMPRefined.elements.impl.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.Element;
import hs.elementSMPRefined.elements.ElementBuilder;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.elements.abilities.Ability;
import hs.elementSMPRefined.elements.abilities.impl.fire.FireballAbility;
import hs.elementSMPRefined.elements.abilities.impl.fire.MeteorShowerAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * REFACTORED: This is how the FireElement could look using the new ElementBuilder API.
 * Compare this with the original FireElement.java to see the improvements.
 *
 * BENEFITS:
 * - Much less boilerplate code
 * - Cleaner, more readable
 * - Easier to modify and maintain
 * - Data-driven approach
 * - Less error-prone
 */
public class FireElementRefactored {
    /**
     * Factory method to create the Fire element using the new builder API
     */
    public static Element create(ElementSMPRefined plugin) {
        Ability fireballAbility = new FireballAbility(plugin);
        Ability meteorShowerAbility = new MeteorShowerAbility(plugin);

        return new ElementBuilder(plugin)
                .type(ElementType.FIRE)
                .displayName("Fire")
                .description("Masters of flame and destruction. Fire users are immune to fire damage and can rain destruction from above.")
                .color(ChatColor.RED)
                .isBasic(true)
                // Passive effects
                .passiveEffect(PotionEffectType.FIRE_RESISTANCE, 0)
                // Clear effects
                .clearEffect(PotionEffectType.FIRE_RESISTANCE)
                // Abilities
                .ability1(fireballAbility, "Fireball", "Launch a fireball that explodes on impact")
                .ability2(meteorShowerAbility, "Meteor Shower", "Rain down meteors from the sky")
                .build();
    }

    /**
     * ALTERNATIVE: Using the @RegisterElement annotation for automatic registration
     * This would be placed in FireElement.java instead of extending BaseElement
     */
    /*
    @RegisterElement(
        value = ElementType.FIRE,
        isBasic = true,
        displayName = "Fire",
        description = "Masters of flame and destruction",
        color = "RED"
    )
    public class FireElement extends BaseElement {
        private final Ability ability1;
        private final Ability ability2;

        public FireElement(ElementSMPRefined plugin) {
            super(plugin);
            this.ability1 = new FireballAbility(plugin);
            this.ability2 = new MeteorShowerAbility(plugin);
        }

        @Override
        public ElementType getType() {
            return ElementType.FIRE;
        }

        @Override
        public void applyUpsides(Player player, int upgradeLevel) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
        }

        @Override
        protected boolean executeAbility1(ElementContext context) {
            return ability1.execute(context);
        }

        @Override
        protected boolean executeAbility2(ElementContext context) {
            return ability2.execute(context);
        }

        @Override
        public void clearEffects(Player player) {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            ability1.setActive(player, false);
            ability2.setActive(player, false);
        }

        @Override
        public String getDisplayName() {
            return ChatColor.RED + "Fire";
        }

        @Override
        public String getDescription() {
            return ChatColor.GRAY + "Masters of flame and destruction. Fire users are immune to fire damage and can rain destruction from above.";
        }

        @Override
        public String getAbility1Name() {
            return ability1.getName();
        }

        @Override
        public String getAbility1Description() {
            return ability1.getDescription();
        }

        @Override
        public String getAbility2Name() {
            return ability2.getName();
        }

        @Override
        public String getAbility2Description() {
            return ability2.getDescription();
        }
    }
    */
}